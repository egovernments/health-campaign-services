package org.egov.individual.validators;

import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.Error;
import org.egov.common.models.core.EgovModel;
import org.egov.common.models.idgen.IdDispatchResponse;
import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdStatus;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.service.IdGenService;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.individual.Constants.*;

@Component
@Slf4j
@AllArgsConstructor
@Order(value = 7)
public class IdPoolValidatorForCreate implements Validator<IndividualBulkRequest, Individual> {

    private final IdGenService idGenService;

    private final IndividualProperties individualProperties;

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        String userId = request.getRequestInfo().getUserInfo().getUuid();
        if (!individualProperties.getBeneficiaryIdValidationEnabled()) return errorDetailsMap;

        log.info("validating beneficiary id for create");
        List<Individual> individuals = request.getIndividuals();

        Map<String, IdRecord> idRecordMap = getIdRecords(idGenService, individuals, null, request.getRequestInfo());

        if (!individuals.isEmpty()) {
            for (Individual individual : individuals) {
               if (!CollectionUtils.isEmpty(individual.getIdentifiers())) {
                   Identifier identifier = individual.getIdentifiers().stream()
                           .filter(id -> id.getIdentifierType().contains("UNIQUE_BENEFICIARY_ID"))
                           .findFirst().orElse(null);
                   if (identifier != null && StringUtils.isNotBlank(identifier.getIdentifierId())) {
                       if (!idRecordMap.containsKey(identifier.getIdentifierId())) {
                           createError(errorDetailsMap, individual, null, INVALID_BENEFICIARY_ID, "Invalid beneficiary id");
                       } else if (!IdStatus.DISPATCHED.name().equals(idRecordMap.get(identifier.getIdentifierId()).getStatus())) {
                           createError(errorDetailsMap, individual, idRecordMap.get(identifier.getIdentifierId()).getStatus(), INVALID_BENEFICIARY_ID ,"Id Status is not in DISPATCHED state" );
                       } else if (!userId.equals(idRecordMap.get(identifier.getIdentifierId()).getLastModifiedBy())) {
                           createError(errorDetailsMap, individual, idRecordMap.get(identifier.getIdentifierId()).getStatus(),  INVALID_USER_ID,"This beneficiary id is dispatched to another user");
                       }

                   }
               }
            }
        }
        return errorDetailsMap;
    }

    private static void createError(Map<Individual, List<Error>> errorDetailsMap, Individual individual, String status, String errorCode , String errorMessage) {
        if (StringUtils.isEmpty(errorCode) || StringUtils.isEmpty(errorMessage)) {
            errorCode = INVALID_BENEFICIARY_ID;
            errorMessage = "Invalid beneficiary id";
        }
        Error error = Error.builder().errorMessage(errorMessage).errorCode(errorCode)
                  .type(Error.ErrorType.NON_RECOVERABLE)
                  .exception(new CustomException(errorCode, errorMessage)).build();
        populateErrorDetails(individual, error, errorDetailsMap);
    }

    public static Map<String, IdRecord> getIdRecords(IdGenService idGenService, List<Individual> individuals, String status, RequestInfo requestInfo) {
        List<String> beneficiaryIds = individuals.stream()
                .flatMap(d -> Optional.ofNullable(d.getIdentifiers())
                        .orElse(Collections.emptyList())
                        .stream()
                        .filter(identifier -> UNIQUE_BENEFICIARY_ID.equals(identifier.getIdentifierType()))
                        .findFirst()
                        .stream())
                .map(identifier -> String.valueOf(identifier.getIdentifierId()))
                .toList();

        Map<String, IdRecord> getIds = new HashMap<>();
        if (ObjectUtils.isEmpty(beneficiaryIds)) return getIds;
        String tenantId = individuals.get(0).getTenantId();
        IdDispatchResponse idDispatchResponse = idGenService.searchIdRecord(
                beneficiaryIds,
                status,
                tenantId,
                requestInfo
        );
        Map<String, IdRecord> map = idDispatchResponse.getIdResponses().stream()
                .collect(Collectors.toMap(EgovModel::getId, d -> d));
        return map;
    }
}
