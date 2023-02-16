package org.egov.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.stock.config.StockReconciliationConfiguration;
import org.egov.stock.repository.StockReconciliationRepository;
import org.egov.stock.service.enrichment.StockReconciliationEnrichmentService;
import org.egov.stock.validator.stockreconciliation.SrIsDeletedValidator;
import org.egov.stock.validator.stockreconciliation.SrNonExistentValidator;
import org.egov.stock.validator.stockreconciliation.SrNullIdValidator;
import org.egov.stock.validator.stockreconciliation.SrProductVariantIdValidator;
import org.egov.stock.validator.stockreconciliation.SrRowVersionValidator;
import org.egov.stock.validator.stockreconciliation.SrUniqueEntityValidator;
import org.egov.stock.web.models.StockReconciliation;
import org.egov.stock.web.models.StockReconciliationBulkRequest;
import org.egov.stock.web.models.StockReconciliationRequest;
import org.egov.stock.web.models.StockReconciliationSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.handleErrors;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.stock.Constants.GET_STOCK_RECONCILIATION;
import static org.egov.stock.Constants.SET_STOCK_RECONCILIATION;
import static org.egov.stock.Constants.VALIDATION_ERROR;

@Service
@Slf4j
public class StockReconciliationService {

    private final StockReconciliationRepository stockRepository;

    private final List<Validator<StockReconciliationBulkRequest, StockReconciliation>> validators;

    private final StockReconciliationConfiguration configuration;

    private final StockReconciliationEnrichmentService enrichmentService;

    private final Predicate<Validator<StockReconciliationBulkRequest, StockReconciliation>> isApplicableForCreate =
            validator -> validator.getClass().equals(SrProductVariantIdValidator.class);

    private final Predicate<Validator<StockReconciliationBulkRequest, StockReconciliation>> isApplicableForUpdate =
            validator -> validator.getClass().equals(SrProductVariantIdValidator.class)
                    || validator.getClass().equals(SrIsDeletedValidator.class)
                    || validator.getClass().equals(SrNonExistentValidator.class)
                    || validator.getClass().equals(SrNullIdValidator.class)
                    || validator.getClass().equals(SrRowVersionValidator.class)
                    || validator.getClass().equals(SrUniqueEntityValidator.class);

    private final Predicate<Validator<StockReconciliationBulkRequest, StockReconciliation>> isApplicableForDelete =
            validator -> validator.getClass().equals(SrNonExistentValidator.class)
                    || validator.getClass().equals(SrNullIdValidator.class);

    public StockReconciliationService(StockReconciliationRepository stockRepository, List<Validator<StockReconciliationBulkRequest, StockReconciliation>> validators, StockReconciliationConfiguration configuration, StockReconciliationEnrichmentService enrichmentService) {
        this.stockRepository = stockRepository;
        this.validators = validators;
        this.configuration = configuration;
        this.enrichmentService = enrichmentService;
    }


    public StockReconciliation create(StockReconciliationRequest request) {
        StockReconciliationBulkRequest bulkRequest = StockReconciliationBulkRequest.builder()
                .stockReconciliation(Collections.singletonList(request.getStockReconciliation()))
                .requestInfo(request.getRequestInfo()).build();

        return create(bulkRequest, false).get(0);
    }

    public List<StockReconciliation> create(StockReconciliationBulkRequest request, boolean isBulk) {
        Tuple<List<StockReconciliation>, Map<StockReconciliation, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, request, SET_STOCK_RECONCILIATION, GET_STOCK_RECONCILIATION,
                isBulk);

        Map<StockReconciliation, ErrorDetails> errorDetailsMap = tuple.getY();
        List<StockReconciliation> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                log.info("processing {} valid entities", validTasks.size());
                enrichmentService.create(validTasks, request);
                stockRepository.save(validTasks, configuration.getCreateStockReconciliationTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_STOCK_RECONCILIATION);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        return validTasks;
    }

    public StockReconciliation update(StockReconciliationRequest request) {
        StockReconciliationBulkRequest bulkRequest = StockReconciliationBulkRequest.builder()
                .stockReconciliation(Collections.singletonList(request.getStockReconciliation()))
                .requestInfo(request.getRequestInfo()).build();

        return update(bulkRequest, false).get(0);
    }

    public List<StockReconciliation> update(StockReconciliationBulkRequest request, boolean isBulk) {
        Tuple<List<StockReconciliation>, Map<StockReconciliation, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, request, SET_STOCK_RECONCILIATION, GET_STOCK_RECONCILIATION,
                isBulk);

        Map<StockReconciliation, ErrorDetails> errorDetailsMap = tuple.getY();
        List<StockReconciliation> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                log.info("processing {} valid entities", validTasks.size());
                enrichmentService.update(validTasks, request);
                stockRepository.save(validTasks, configuration.getUpdateStockReconciliationTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_STOCK_RECONCILIATION);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        return validTasks;
    }

    public StockReconciliation delete(StockReconciliationRequest request) {
        StockReconciliationBulkRequest bulkRequest = StockReconciliationBulkRequest.builder()
                .stockReconciliation(Collections.singletonList(request.getStockReconciliation()))
                .requestInfo(request.getRequestInfo()).build();

        return delete(bulkRequest, false).get(0);
    }

    public List<StockReconciliation> delete(StockReconciliationBulkRequest request, boolean isBulk) {
        Tuple<List<StockReconciliation>, Map<StockReconciliation, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, request, SET_STOCK_RECONCILIATION, GET_STOCK_RECONCILIATION,
                isBulk);

        Map<StockReconciliation, ErrorDetails> errorDetailsMap = tuple.getY();
        List<StockReconciliation> validTasks = tuple.getX();
        try {
            if (!validTasks.isEmpty()) {
                log.info("processing {} valid entities", validTasks.size());
                enrichmentService.delete(validTasks, request);
                stockRepository.save(validTasks, configuration.getDeleteStockReconciliationTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred", exception);
            populateErrorDetails(request, errorDetailsMap, validTasks, exception, SET_STOCK_RECONCILIATION);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        return validTasks;
    }

    public List<StockReconciliation> search(StockReconciliationSearchRequest request,
                              Integer limit,
                              Integer offset,
                              String tenantId,
                              Long lastChangedSince,
                              Boolean includeDeleted) throws Exception  {
        String idFieldName = getIdFieldName(request.getStockReconciliation());
        if (isSearchByIdOnly(request.getStockReconciliation(), idFieldName)) {
            List<String> ids = (List<String>) ReflectionUtils.invokeMethod(getIdMethod(Collections
                            .singletonList(request.getStockReconciliation())),
                    request.getStockReconciliation());
            return stockRepository.findById(ids, includeDeleted, idFieldName).stream()
                    .filter(lastChangedSince(lastChangedSince))
                    .filter(havingTenantId(tenantId))
                    .filter(includeDeleted(includeDeleted))
                    .collect(Collectors.toList());
        }
        return stockRepository.find(request.getStockReconciliation(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }

    private <T, R> Tuple<List<T>, Map<T, ErrorDetails>> validate(List<Validator<R, T>> validators,
                                                                 Predicate<Validator<R, T>> applicableValidators,
                                                                 R request, String setPayloadMethodName,
                                                                 String getPayloadMethodName,boolean isBulk) {
        log.info("validating request");
        Map<T, ErrorDetails> errorDetailsMap = CommonUtils.validate(validators,
                applicableValidators, request,
                setPayloadMethodName);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(VALIDATION_ERROR, errorDetailsMap.values().toString());
        }
        Method getEntities = getMethod(getPayloadMethodName, request.getClass());
        List<T> validEntities = (List<T>) ReflectionUtils.invokeMethod(getEntities, request);
        validEntities = validEntities.stream().filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validEntities, errorDetailsMap);
    }
}
