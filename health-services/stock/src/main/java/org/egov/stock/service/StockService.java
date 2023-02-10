package org.egov.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.stock.config.StockConfiguration;
import org.egov.stock.repository.StockRepository;
import org.egov.stock.service.enrichment.StockEnrichmentService;
import org.egov.stock.validator.stock.SisDeletedValidator;
import org.egov.stock.validator.stock.SnonExistentValidator;
import org.egov.stock.validator.stock.SnullIdValidator;
import org.egov.stock.validator.stock.SproductVaraintIdValidator;
import org.egov.stock.validator.stock.SrowVersionValidator;
import org.egov.stock.validator.stock.SuniqueEntityValidator;
import org.egov.stock.web.models.Stock;
import org.egov.stock.web.models.StockBulkRequest;
import org.egov.stock.web.models.StockRequest;
import org.egov.stock.web.models.StockSearchRequest;
import org.egov.tracer.model.CustomException;
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
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
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
            validator -> validator.getClass().equals(SproductVaraintIdValidator.class);

    private final Predicate<Validator<StockBulkRequest, Stock>> isApplicableForUpdate =
            validator -> validator.getClass().equals(SproductVaraintIdValidator.class)
            || validator.getClass().equals(SisDeletedValidator.class)
            || validator.getClass().equals(SnonExistentValidator.class)
            || validator.getClass().equals(SnullIdValidator.class)
            || validator.getClass().equals(SrowVersionValidator.class)
            || validator.getClass().equals(SuniqueEntityValidator.class);

    private final Predicate<Validator<StockBulkRequest, Stock>> isApplicableForDelete =
            validator -> validator.getClass().equals(SnonExistentValidator.class)
            || validator.getClass().equals(SnullIdValidator.class);

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
        Tuple<List<Stock>, Map<Stock, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, request,
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
        Tuple<List<Stock>, Map<Stock, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, request,
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

    private Tuple<List<Stock>, Map<Stock, ErrorDetails>> validate(List<Validator<StockBulkRequest, Stock>> validators,
                                                                Predicate<Validator<StockBulkRequest, Stock>> applicableValidators,
                                                                StockBulkRequest request, boolean isBulk) {
        log.info("validating request");
        Map<Stock, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                applicableValidators, request,
                SET_STOCK);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        List<Stock> validStocks = request.getStock().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validStocks, errorDetailsMap);
    }
    

    public List<Stock> delete(StockBulkRequest request, boolean isBulk) {
        Tuple<List<Stock>, Map<Stock, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, request,
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
            return stockRepository.findById(ids, includeDeleted).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        return stockRepository.find(stockSearchRequest.getStock(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }
}
