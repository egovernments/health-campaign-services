package org.egov.individual.validators;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.Error;
import org.egov.common.models.idgen.IdRecord;
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

import static org.egov.common.utils.CommonUtils.populateErrorDetails;

@Component
@Slf4j
@AllArgsConstructor
@Order(value = 12)
public class IdPoolValidatorForUpdate implements Validator<IndividualBulkRequest, Individual> {

    private final IdGenService idGenService;

    private final IndividualProperties individualProperties;

    @Override
    public Map<Individual, List<Error>> validate(IndividualBulkRequest request) {
        Map<Individual, List<Error>> errorDetailsMap = new HashMap<>();
        if (!individualProperties.getBeneficiaryIdValidationEnabled()) return errorDetailsMap;

        log.info("validating beneficiary id for update");
        List<Individual> individuals = request.getIndividuals();

        Map<String, IdRecord> idRecordMap = IdPoolValidatorForCreate
                .getIdRecords(idGenService, individuals, null, request.getRequestInfo());

        if (!individuals.isEmpty()) {
            for (Individual individual : individuals) {
               if (!CollectionUtils.isEmpty(individual.getIdentifiers())) {
                   Identifier identifier = individual.getIdentifiers().stream()
                           .filter(id -> id.getIdentifierType().contains("UNIQUE_BENEFICIARY_ID"))
                           .findFirst().orElse(null);
                   if (identifier != null && StringUtils.isNotBlank(identifier.getIdentifierId())) {
                       if (!idRecordMap.containsKey(identifier.getIdentifierId())) {
                           updateError(errorDetailsMap, individual);
                       }
                   }
               }
            }
        }
        return errorDetailsMap;
    }

    private static void updateError(Map<Individual, List<Error>> errorDetailsMap, Individual individual) {
        String errorCode = "INVALID_BENEFICIARY_ID";
        String errorMessage = "Invalid beneficiary id";
        Error error = Error.builder().errorMessage(errorMessage).errorCode(errorCode)
                .type(Error.ErrorType.NON_RECOVERABLE)
                .exception(new CustomException(errorCode, errorMessage)).build();
        populateErrorDetails(individual, error, errorDetailsMap);
    }


}
