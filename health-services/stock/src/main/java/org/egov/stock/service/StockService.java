package org.egov.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.stock.config.StockConfiguration;
import org.egov.stock.repository.StockRepository;
import org.egov.stock.service.enrichment.StockEnrichmentService;
import org.egov.stock.validator.stock.SIsDeletedValidator;
import org.egov.stock.validator.stock.SNonExistentValidator;
import org.egov.stock.validator.stock.SNullIdValidator;
import org.egov.stock.validator.stock.SProductVariantIdValidator;
import org.egov.stock.validator.stock.SRowVersionValidator;
import org.egov.stock.validator.stock.SUniqueEntityValidator;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
import org.egov.stock.web.models.StockRequest;
import org.egov.stock.web.models.StockSearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.handleErrors;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.stock.Constants.GET_STOCK;
import static org.egov.stock.Constants.SET_STOCK;
import static org.egov.stock.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class StockService {

    private final StockRepository stockRepository;

    private final List<Validator<StockBulkRequest, Stock>> validators;

    private final StockConfiguration configuration;

    private final StockEnrichmentService enrichmentService;

    private final Predicate<Validator<StockBulkRequest, Stock>> isApplicableForCreate =
            validator -> validator.getClass().equals(SProductVariantIdValidator.class);

    private final Predicate<Validator<StockBulkRequest, Stock>> isApplicableForUpdate =
            validator -> validator.getClass().equals(SProductVariantIdValidator.class)
            || validator.getClass().equals(SIsDeletedValidator.class)
            || validator.getClass().equals(SNonExistentValidator.class)
            || validator.getClass().equals(SNullIdValidator.class)
            || validator.getClass().equals(SRowVersionValidator.class)
            || validator.getClass().equals(SUniqueEntityValidator.class);

    private final Predicate<Validator<StockBulkRequest, Stock>> isApplicableForDelete =
            validator -> validator.getClass().equals(SNonExistentValidator.class)
            || validator.getClass().equals(SNullIdValidator.class);

    public StockService(StockRepository stockRepository, List<Validator<StockBulkRequest, Stock>> validators, StockConfiguration configuration, StockEnrichmentService enrichmentService) {
        this.stockRepository = stockRepository;
        this.validators = validators;
        this.configuration = configuration;
        this.enrichmentService = enrichmentService;
    }

    public Stock create(StockRequest request) {
        StockBulkRequest bulkRequest = StockBulkRequest.builder().stock(Collections.singletonList(request.getStock()))
                .requestInfo(request.getRequestInfo()).build();

        return create(bulkRequest, false).get(0);
    }

    public List<Stock> create(StockBulkRequest request, boolean isBulk) {
        Tuple<List<Stock>, Map<Stock, ErrorDetails>> tuple = CommonUtils.validate(validators,
                isApplicableForCreate, request, SET_STOCK, GET_STOCK, VALIDATION_ERROR,
                isBulk);

        Map<Stock, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Stock> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                log.info("processing {} valid entities", validTasks.size());
                enrichmentService.create(validTasks, request);
                stockRepository.save(validTasks, configuration.getCreateStockTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_STOCK);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        return validTasks;
    }

    public Stock update(StockRequest request) {
        StockBulkRequest bulkRequest = StockBulkRequest.builder().stock(Collections.singletonList(request.getStock()))
                .requestInfo(request.getRequestInfo()).build();

        return update(bulkRequest, false).get(0);
    }

    public List<Stock> update(StockBulkRequest request, boolean isBulk) {
        Tuple<List<Stock>, Map<Stock, ErrorDetails>> tuple = CommonUtils.validate(validators,
                isApplicableForUpdate, request, SET_STOCK, GET_STOCK, VALIDATION_ERROR,
                isBulk);
        Map<Stock, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Stock> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                log.info("processing {} valid entities", validTasks.size());
                enrichmentService.update(validTasks, request);
                stockRepository.save(validTasks, configuration.getUpdateStockTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_STOCK);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        return validTasks;
    }

    public Stock delete(StockRequest request) {
        StockBulkRequest bulkRequest = StockBulkRequest.builder().stock(Collections.singletonList(request.getStock()))
                .requestInfo(request.getRequestInfo()).build();

        return delete(bulkRequest, false).get(0);
    }

    public List<Stock> delete(StockBulkRequest request, boolean isBulk) {
        Tuple<List<Stock>, Map<Stock, ErrorDetails>> tuple = CommonUtils.validate(validators,
                isApplicableForDelete, request, SET_STOCK, GET_STOCK, VALIDATION_ERROR,
                isBulk);
        Map<Stock, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Stock> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                log.info("processing {} valid entities", validTasks.size());
                enrichmentService.delete(validTasks, request);
                stockRepository.save(validTasks, configuration.getDeleteStockTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_STOCK);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        return validTasks;
    }

    public List<Stock> search(StockSearchRequest stockSearchRequest,
                              Integer limit,
                              Integer offset,
                              String tenantId,
                              Long lastChangedSince,
                              Boolean includeDeleted) throws Exception  {
        String idFieldName = getIdFieldName(stockSearchRequest.getStock());
        if (isSearchByIdOnly(stockSearchRequest.getStock(), idFieldName)) {
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(stockSearchRequest.getStock())),
                    stockSearchRequest.getStock());
            return stockRepository.findById(ids, includeDeleted, idFieldName).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        return stockRepository.find(stockSearchRequest.getStock(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }
}
