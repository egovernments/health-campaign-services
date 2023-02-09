package org.egov.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.validator.Validator;
import org.egov.stock.repository.StockRepository;
import org.egov.stock.validator.stock.SisDeletedValidator;
import org.egov.stock.validator.stock.SnonExistentValidator;
import org.egov.stock.validator.stock.SnullIdValidator;
import org.egov.stock.validator.stock.SproductVaraintIdValidator;
import org.egov.stock.validator.stock.SrowVersionValidator;
import org.egov.stock.validator.stock.SuniqueEntityValidator;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
import org.egov.stock.web.models.StockRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

@Service
@Slf4j
public class StockService {

    private final StockRepository stockRepository;

    private final List<Validator<StockBulkRequest, Stock>> validators;

    private final Predicate<Validator<StockBulkRequest, Stock>> isApplicableForCreate = validator -> validator.getClass().equals(SproductVaraintIdValidator.class);

    private final Predicate<Validator<StockBulkRequest, Stock>> isApplicableForUpdate = validator -> validator.getClass().equals(SproductVaraintIdValidator.class)
            || validator.getClass().equals(SisDeletedValidator.class)
            || validator.getClass().equals(SnonExistentValidator.class)
            || validator.getClass().equals(SnullIdValidator.class)
            || validator.getClass().equals(SrowVersionValidator.class)
            || validator.getClass().equals(SuniqueEntityValidator.class);

    private final Predicate<Validator<StockBulkRequest, Stock>> isApplicableForDelete = validator -> validator.getClass().equals(SnonExistentValidator.class)
            || validator.getClass().equals(SnullIdValidator.class);

    public StockService(StockRepository stockRepository, List<Validator<StockBulkRequest, Stock>> validators) {
        this.stockRepository = stockRepository;
        this.validators = validators;
    }

    public Stock create(StockRequest request) {
        StockBulkRequest bulkRequest = StockBulkRequest.builder().stock(Collections.singletonList(request.getStock()))
                .requestInfo(request.getRequestInfo()).build();

        return create(bulkRequest, false).get(0);
    }

    public List<Stock> create(StockBulkRequest request, boolean isBulk) {
        return request.getStock();
    }

    public Stock update(StockRequest request) {
        StockBulkRequest bulkRequest = StockBulkRequest.builder().stock(Collections.singletonList(request.getStock()))
                .requestInfo(request.getRequestInfo()).build();

        return update(bulkRequest, false).get(0);
    }

    public List<Stock> update(StockBulkRequest request, boolean isBulk) {
        return request.getStock();
    }

    public Stock delete(StockRequest request) {
        StockBulkRequest bulkRequest = StockBulkRequest.builder().stock(Collections.singletonList(request.getStock()))
                .requestInfo(request.getRequestInfo()).build();

        return delete(bulkRequest, false).get(0);
    }

    public List<Stock> delete(StockBulkRequest request, boolean isBulk) {
        return request.getStock();
    }
}
