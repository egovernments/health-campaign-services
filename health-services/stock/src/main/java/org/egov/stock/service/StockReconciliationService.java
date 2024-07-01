package org.egov.stock.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.ds.Tuple;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.common.models.stock.StockReconciliationBulkRequest;
import org.egov.common.models.stock.StockReconciliationRequest;
import org.egov.common.models.stock.StockReconciliationSearchRequest;
import org.egov.common.validator.Validator;
import org.egov.stock.config.StockReconciliationConfiguration;
import org.egov.stock.repository.StockReconciliationRepository;
import org.egov.stock.service.enrichment.StockReconciliationEnrichmentService;
import org.egov.stock.validator.stockreconciliation.SrExistentEntityValidator;
import org.egov.stock.validator.stockreconciliation.SrFacilityIdValidator;
import org.egov.stock.validator.stockreconciliation.SrIsDeletedValidator;
import org.egov.stock.validator.stockreconciliation.SrNonExistentValidator;
import org.egov.stock.validator.stockreconciliation.SrNullIdValidator;
import org.egov.stock.validator.stockreconciliation.SrProductVariantIdValidator;
import org.egov.stock.validator.stockreconciliation.SrReferenceIdValidator;
import org.egov.stock.validator.stockreconciliation.SrRowVersionValidator;
import org.egov.stock.validator.stockreconciliation.SrUniqueEntityValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.handleErrors;
import static org.egov.common.utils.CommonUtils.havingTenantId;
import static org.egov.common.utils.CommonUtils.includeDeleted;
import static org.egov.common.utils.CommonUtils.isSearchByIdOnly;
import static org.egov.common.utils.CommonUtils.lastChangedSince;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.CommonUtils.validate;
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
            validator -> validator.getClass().equals(SrProductVariantIdValidator.class)
                    || validator.getClass().equals(SrExistentEntityValidator.class)
                    || validator.getClass().equals(SrFacilityIdValidator.class)
                    || validator.getClass().equals(SrReferenceIdValidator.class);

    private final Predicate<Validator<StockReconciliationBulkRequest, StockReconciliation>> isApplicableForUpdate =
            validator -> validator.getClass().equals(SrProductVariantIdValidator.class)
                    || validator.getClass().equals(SrIsDeletedValidator.class)
                    || validator.getClass().equals(SrNonExistentValidator.class)
                    || validator.getClass().equals(SrNullIdValidator.class)
                    || validator.getClass().equals(SrRowVersionValidator.class)
                    || validator.getClass().equals(SrUniqueEntityValidator.class)
                    || validator.getClass().equals(SrFacilityIdValidator.class)
                    || validator.getClass().equals(SrReferenceIdValidator.class);

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
        log.info("starting create method for stock reconciliation");

        Tuple<List<StockReconciliation>, Map<StockReconciliation, ErrorDetails>> tuple = validate(validators,
                isApplicableForCreate, request, SET_STOCK_RECONCILIATION, GET_STOCK_RECONCILIATION, VALIDATION_ERROR,
                isBulk);

        Map<StockReconciliation, ErrorDetails> errorDetailsMap = tuple.getY();
        List<StockReconciliation> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                log.info("processing {} valid entities", validEntities.size());
                enrichmentService.create(validEntities, request);
                stockRepository.save(validEntities, configuration.getCreateStockReconciliationTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_STOCK_RECONCILIATION);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        log.info("completed create method for stock reconciliation");
        return validEntities;
    }

    public StockReconciliation update(StockReconciliationRequest request) {
        StockReconciliationBulkRequest bulkRequest = StockReconciliationBulkRequest.builder()
                .stockReconciliation(Collections.singletonList(request.getStockReconciliation()))
                .requestInfo(request.getRequestInfo()).build();

        return update(bulkRequest, false).get(0);
    }

    public List<StockReconciliation> update(StockReconciliationBulkRequest request, boolean isBulk) {
        log.info("starting update method for stock reconciliation");
        Tuple<List<StockReconciliation>, Map<StockReconciliation, ErrorDetails>> tuple = validate(validators,
                isApplicableForUpdate, request, SET_STOCK_RECONCILIATION, GET_STOCK_RECONCILIATION, VALIDATION_ERROR,
                isBulk);

        Map<StockReconciliation, ErrorDetails> errorDetailsMap = tuple.getY();
        List<StockReconciliation> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                log.info("processing {} valid entities", validEntities.size());
                enrichmentService.update(validEntities, request);
                stockRepository.save(validEntities, configuration.getUpdateStockReconciliationTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_STOCK_RECONCILIATION);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);

        log.info("completed update method for stock reconciliation");
        return validEntities;
    }

    public StockReconciliation delete(StockReconciliationRequest request) {
        StockReconciliationBulkRequest bulkRequest = StockReconciliationBulkRequest.builder()
                .stockReconciliation(Collections.singletonList(request.getStockReconciliation()))
                .requestInfo(request.getRequestInfo()).build();

        return delete(bulkRequest, false).get(0);
    }

    public List<StockReconciliation> delete(StockReconciliationBulkRequest request, boolean isBulk) {
        log.info("starting delete method for stock reconciliation");
        Tuple<List<StockReconciliation>, Map<StockReconciliation, ErrorDetails>> tuple = validate(validators,
                isApplicableForDelete, request, SET_STOCK_RECONCILIATION, GET_STOCK_RECONCILIATION, VALIDATION_ERROR,
                isBulk);

        Map<StockReconciliation, ErrorDetails> errorDetailsMap = tuple.getY();
        List<StockReconciliation> validEntities = tuple.getX();
        try {
            if (!validEntities.isEmpty()) {
                log.info("processing {} valid entities", validEntities.size());
                enrichmentService.delete(validEntities, request);
                stockRepository.save(validEntities, configuration.getDeleteStockReconciliationTopic());
            }
        } catch (Exception exception) {
            log.error("error occurred: {}", ExceptionUtils.getStackTrace(exception));
            populateErrorDetails(request, errorDetailsMap, validEntities, exception, SET_STOCK_RECONCILIATION);
        }

        handleErrors(errorDetailsMap, isBulk, VALIDATION_ERROR);
        log.info("completed delete method for stock reconciliation");
        return validEntities;
    }

    public List<StockReconciliation> search(StockReconciliationSearchRequest request,
                                            Integer limit,
                                            Integer offset,
                                            String tenantId,
                                            Long lastChangedSince,
                                            Boolean includeDeleted) throws Exception  {
        log.info("starting search method for stock reconciliation");
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
        log.info("completed search method for stock reconciliation");
        return stockRepository.find(request.getStockReconciliation(),
                limit, offset, tenantId, lastChangedSince, includeDeleted);
    }
}
