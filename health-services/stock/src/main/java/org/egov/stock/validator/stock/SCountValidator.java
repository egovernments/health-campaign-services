package org.egov.stock.validator.stock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.TransactionType;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.stock.repository.StockRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.notHavingErrors;

@Component
@Order(value = 10)
@Slf4j
public class SCountValidator implements Validator<StockBulkRequest, Stock> {

    private StockRepository stockRepository;

    public SCountValidator(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }
    @Override
    public Map<Stock, List<Error>> validate(StockBulkRequest request) {
        Map<Stock, List<Error>> errorDetailsMap = new HashMap<>();

        List<Stock> validEntities = request.getStock().stream()
                .filter(notHavingErrors())
                .toList();

        String tenantId = CommonUtils.getTenantId(validEntities);

        List<Stock> dispatchedStocks = validEntities.stream()
                .filter(stock -> TransactionType.DISPATCHED.equals(stock.getTransactionType()))
                .toList();

        List<Stock> receivedStocks = validEntities.stream()
                .filter(stock -> TransactionType.RECEIVED.equals(stock.getTransactionType()))
                .toList();

        Map<String, List<Stock>> senderIdToDispatchedStocksMap = new HashMap<>();
        dispatchedStocks.forEach(stock -> {
            if(senderIdToDispatchedStocksMap.containsKey(stock.getSenderId())) {
                senderIdToDispatchedStocksMap.get(stock.getSenderId()).add(stock);
            } else {
                senderIdToDispatchedStocksMap.put(stock.getSenderId(), new ArrayList<>(List.of(stock)));
            }
        });
        Map<String, List<Stock>> receiverIdToReceivedStocksMap = new HashMap<>();
        receivedStocks.forEach(stock -> {
            if(receiverIdToReceivedStocksMap.containsKey(stock.getReceiverId())) {
                receiverIdToReceivedStocksMap.get(stock.getReceiverId()).add(stock);
            } else {
                receiverIdToReceivedStocksMap.put(stock.getReceiverId(), new ArrayList<>(List.of(stock)));
            }
        });

        List<String> senderIds = senderIdToDispatchedStocksMap.keySet().stream().toList();

        if(senderIds.isEmpty()) return errorDetailsMap;

        try {
            List<Stock> dispatchedStocksFromDb = stockRepository.findById(tenantId, senderIds, false, "senderId");

            dispatchedStocksFromDb.stream()
                    .filter(stock -> TransactionType.DISPATCHED.equals(stock.getTransactionType()))
                    .forEach(dispatchedStock -> {
                if(senderIdToDispatchedStocksMap.containsKey(dispatchedStock.getSenderId())) {
                    senderIdToDispatchedStocksMap.get(dispatchedStock.getSenderId()).add(dispatchedStock);
                } else {
                    senderIdToDispatchedStocksMap.put(dispatchedStock.getSenderId(), new ArrayList<>(List.of(dispatchedStock)));
                }
            });

            List<Stock> receivedStocksFromDb = stockRepository.findById(tenantId, senderIds, false, "receiverId");

            receivedStocksFromDb.stream()
                    .filter(stock -> TransactionType.RECEIVED.equals(stock.getTransactionType()))
                    .forEach(receivedStock -> {
                        if(receiverIdToReceivedStocksMap.containsKey(receivedStock.getReceiverId())) {
                            receiverIdToReceivedStocksMap.get(receivedStock.getReceiverId()).add(receivedStock);
                        } else {
                            receiverIdToReceivedStocksMap.put(receivedStock.getReceiverId(), new ArrayList<>(List.of(receivedStock)));
                        }
                    });

        } catch (InvalidTenantIdException e) {
            throw new RuntimeException(e);
        }

        dispatchedStocks.forEach(stock -> {
            int totalDispatchedQuantity = senderIdToDispatchedStocksMap.getOrDefault(stock.getSenderId(), List.of())
                    .stream()
                    .mapToInt(Stock::getQuantity)
                    .sum();
            int totalReceivedQuantity = receiverIdToReceivedStocksMap.getOrDefault(stock.getSenderId(), List.of())
                    .stream()
                    .mapToInt(Stock::getQuantity)
                    .sum();
            log.info("For senderId: {}, totalDispatchedQuantity: {}, totalReceivedQuantity: {}", stock.getSenderId(), totalDispatchedQuantity, totalReceivedQuantity);
            if(totalReceivedQuantity < totalDispatchedQuantity) {
                Error error = Error.builder()
                        .errorCode("STOCK_DISPATCH_EXCEEDS_RECEIPT")
                        .errorMessage("Total dispatched quantity exceeds total received quantity for senderId: " + stock.getSenderId())
                        .exception(new CustomException("STOCK_DISPATCH_EXCEEDS_RECEIPT", "Total dispatched quantity exceeds total received quantity for senderId: " + stock.getSenderId()))
                        .build();
                CommonUtils.populateErrorDetails(stock, error, errorDetailsMap);
            }
        });

        return errorDetailsMap;

    }
}
