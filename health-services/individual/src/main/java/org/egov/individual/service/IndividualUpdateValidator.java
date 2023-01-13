package org.egov.individual.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.web.models.IndividualRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


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
        /*Method idMethod = getIdMethod(request.getIndividual());


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
                        "Row version mismatch", request, errorDetailsList));*/


        return errorDetailsList;
    }
}
