package org.egov.individual.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.Address;
import org.egov.individual.web.models.AddressType;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.IndividualRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getEntitiesWithMismatchedRowVersion;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.identifyObjectsWithNullIds;


@Component
public class IndividualUpdateValidator implements Validator<IndividualRequest> {

    private final IndividualRepository individualRepository;

    private final ObjectMapper objectMapper;

    @Autowired
    public IndividualUpdateValidator(IndividualRepository individualRepository,
                                     @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.individualRepository = individualRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ErrorDetails> validate(IndividualRequest request) {
        List<ErrorDetails> errorDetailsList = new ArrayList<>();
        Method idMethod = getIdMethod(request.getIndividual());
        List<Individual> indWithNullIds = identifyObjectsWithNullIds(request.getIndividual(), idMethod);
        indWithNullIds.forEach(individual -> populateErrorDetails(individual, "ERROR_IN_INDIVIDUAL",
                "Id cannot be null", request, errorDetailsList));
        Map<String, Individual> iMap = getIdToObjMap(request.getIndividual().stream()
                .filter(individual -> !individual.getHasErrors())
                .collect(Collectors.toList()), idMethod);

        List<String> individualIds = new ArrayList<>(iMap.keySet());
        List<Individual> existingIndividuals = individualRepository.findById(individualIds,
                getIdFieldName(idMethod), false);
        List<Individual> nonExistentIndividuals = checkNonExistentEntities(iMap,
                existingIndividuals, idMethod);
        nonExistentIndividuals.forEach(individual -> populateErrorDetails(individual, "NON_EXISTENT_INDIVIDUAL",
                "Individual does not exist in db", request, errorDetailsList));
        List<Individual> individualsWithInvalidAddress = validateAddressType(request.getIndividual());
        individualsWithInvalidAddress.forEach(individual -> populateErrorDetails(individual, "INVALID_ADDRESS",
                "Invalid address", request, errorDetailsList));
        List<Individual> individualsWithMismatchedRowVersion =
                getEntitiesWithMismatchedRowVersion(iMap, existingIndividuals, idMethod);
        individualsWithMismatchedRowVersion.forEach(individual ->
                populateErrorDetails(individual, "MISMATCHED_ROW_VERSION",
                        "Row version mismatch", request, errorDetailsList));
        return errorDetailsList;
    }

    private void populateErrorDetails(Individual individual, String errorCode, String errorMessage,
                                      IndividualRequest request, List<ErrorDetails> errorDetailsList) {
        try {
            individual.setHasErrors(Boolean.TRUE);
            ErrorDetails errorDetails = ErrorDetails.builder()
                    .status("")
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .apiDetails(ApiDetails.builder()
                            .url("/individual/v1/_update")
                            .requestBody(objectMapper.writeValueAsString(IndividualRequest.builder()
                                    .requestInfo(request.getRequestInfo())
                                    .individual(Collections.singletonList(individual))
                                    .build()))
                            .build())
                    .build();
            errorDetailsList.add(errorDetails);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    private List<Individual> validateAddressType(List<Individual> indInReq) {
        List<Individual> individuals = new ArrayList<>();
        for (Individual individual : indInReq) {
            Map<AddressType, Integer> addressTypeCountMap = new EnumMap<>(AddressType.class);
            if (individual.getAddress() == null) {
                continue;
            }
            for (Address address : individual.getAddress()) {
                addressTypeCountMap.merge(address.getType(), 1, Integer::sum);
            }
            addressTypeCountMap.entrySet().stream().filter(e -> e.getValue() > 1).forEach(e -> individuals.add(individual));
        }
        return individuals;
    }
}
