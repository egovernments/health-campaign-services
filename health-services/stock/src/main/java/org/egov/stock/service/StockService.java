package org.egov.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.StockBulkRequest;
import org.egov.common.models.stock.StockRequest;
import org.egov.common.validator.Validator;
import org.egov.stock.config.StockConfiguration;
import org.egov.stock.repository.StockRepository;
import org.egov.stock.service.enrichment.StockEnrichmentService;
import org.egov.stock.validator.stock.*;
import org.egov.stock.web.models.StockSearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.*;
import static org.egov.stock.Constants.*;


@Service
@Slf4j
public class StockService {

    private final StockRepository stockRepository;

    private final List<Validator<StockBulkRequest, Stock>> validators;

    private final StockConfiguration configuration;

    private final StockEnrichmentService enrichmentService;

    private final Predicate<Validator<StockBulkRequest, Stock>> isApplicableForCreate =
            validator -> validator.getClass().equals(SProductVariantIdValidator.class)
                    || validator.getClass().equals(SSenderIdReceiverIdEqualsValidator.class)
                    || validator.getClass().equals(StocktransferPartiesValidator.class)
                    || validator.getClass().equals(SReferenceIdValidator.class);

    private final Predicate<Validator<StockBulkRequest, Stock>> isApplicableForUpdate =
            validator -> validator.getClass().equals(SProductVariantIdValidator.class)
            || validator.getClass().equals(SIsDeletedValidator.class)
            || validator.getClass().equals(SNonExistentValidator.class)
            || validator.getClass().equals(SNullIdValidator.class)
            || validator.getClass().equals(SRowVersionValidator.class)
            || validator.getClass().equals(SUniqueEntityValidator.class)
            || validator.getClass().equals(SReferenceIdValidator.class)
            || validator.getClass().equals(SSenderIdReceiverIdEqualsValidator.class)
            || validator.getClass().equals(StocktransferPartiesValidator.class);

    private final Predicate<Validator<StockBulkRequest, Stock>> isApplicableForDelete =
            validator -> validator.getClass().equals(SNonExistentValidator.class)
            || validator.getClass().equals(SNullIdValidator.class)
            || validator.getClass().equals(SRowVersionValidator.class);

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
        log.info("starting create method for stock");
        Tuple<List<Stock>, Map<Stock, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, request, SET_STOCK, GET_STOCK, VALIDATION_ERROR,
                isBulk);
        Map<Stock, ErrorDetails> errorDetailsMap = tuple.getY();
        List<Stock> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                log.info("processing {} valid entities", validEntities.size());
                enrichmentService.create(validEntities, request);
                if (configuration.getIsPersisterBulkProcessingEnabled()) {
                    stockRepository.save(validEntities,
                            configuration.getCreateStockTopic());
                } else {
                    for (Stock entity : validEntities) {
                        stockRepository.save(Collections.singletonList(entity),
                                configuration.getCreateStockTopic());
                    }
                }
            }
        } catch (Exception exception) {
            log.error("error occurred: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_STOCK);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        log.info("completed create method for stock");
        return validEntities;
    }

    public Stock update(StockRequest request) {
        StockBulkRequest bulkRequest = StockBulkRequest.builder().stock(Collections.singletonList(request.getStock()))
                .requestInfo(request.getRequestInfo()).build();

        return update(bulkRequest, false).get(0);
    }

    public List<Stock> update(StockBulkRequest request, boolean isBulk) {
        log.info("starting update method for stock");
        Tuple<List<Stock>, Map<Stock, ErrorDetails>> tuple = validate(validators,
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
            log.error("error occurred: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_STOCK);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        log.info("completed update method for stock");
        return validTasks;
    }

    public Stock delete(StockRequest request) {
        StockBulkRequest bulkRequest = StockBulkRequest.builder().stock(Collections.singletonList(request.getStock()))
                .requestInfo(request.getRequestInfo()).build();

        return delete(bulkRequest, false).get(0);
    }

    public List<Stock> delete(StockBulkRequest request, boolean isBulk) {
        log.info("starting delete method for stock");
        Tuple<List<Stock>, Map<Stock, ErrorDetails>> tuple = validate(validators,
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
            log.error("error occurred: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_STOCK);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        log.info("completed delete method for stock");
        return validTasks;
    }

    public List<Stock> search(StockSearchRequest stockSearchRequest,
                              Integer limit,
                              Integer offset,
                              String tenantId,
                              Long lastChangedSince,
                              Boolean includeDeleted) throws Exception  {
        log.info("starting search method for stock");
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

        log.info("completed search method for stock");
        return stockRepository.find(stockSearchRequest.getStock(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }
}
