package org.egov.household.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.Individual;
import org.egov.household.web.models.IndividualBulkResponse;
import org.egov.household.web.models.IndividualSearch;
import org.egov.household.web.models.IndividualSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.household.Constants.GET_INDIVIDUAL_CLIENT_REFERENCE_ID;
import static org.egov.household.Constants.GET_INDIVIDUAL_ID;
import static org.egov.household.Constants.INDIVIDUAL_NOT_FOUND;
import static org.egov.household.Constants.INDIVIDUAL_NOT_FOUND_MESSAGE;
import static org.egov.household.Constants.INTERNAL_SERVER_ERROR;

@Component
public class IndividualService {

    private final ServiceRequestClient serviceRequestClient;

    private final HouseholdMemberConfiguration householdMemberConfiguration;

    public IndividualService(ServiceRequestClient serviceRequestClient,
                             HouseholdMemberConfiguration householdMemberConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.householdMemberConfiguration = householdMemberConfiguration;
    }

    public IndividualBulkResponse searchIndividualBeneficiary(
            List<HouseholdMember> householdMembers,
            RequestInfo requestInfo,
            String tenantId
    ) {
        IndividualSearch individualSearch = null;
        Method idMethod = getMethod(GET_INDIVIDUAL_ID, HouseholdMember.class);
        Method clientReferenceIdMethod = getMethod(GET_INDIVIDUAL_CLIENT_REFERENCE_ID, HouseholdMember.class);

        if (householdMembers.get(0).getIndividualId() != null) {
            individualSearch = IndividualSearch
                    .builder()
                    .id(getIdList(householdMembers, idMethod))
                    .build();
        } else if (householdMembers.get(0).getIndividualClientReferenceId() != null) {
            individualSearch = IndividualSearch
                    .builder()
                    .clientReferenceId(getIdList(householdMembers, clientReferenceIdMethod))
                    .build();
        }

        IndividualSearchRequest individualSearchRequest = IndividualSearchRequest.builder()
                .requestInfo(requestInfo)
                .individual(individualSearch)
                .build();

        return getIndividualResponse(tenantId, individualSearchRequest);
    }

    private IndividualBulkResponse getIndividualResponse(String tenantId, IndividualSearchRequest individualSearchRequest) {
        try {
            return serviceRequestClient.fetchResult(
                    new StringBuilder(householdMemberConfiguration.getIndividualServiceHost()
                            + householdMemberConfiguration.getIndividualServiceSearchUrl()
                            + "?limit=10&offset=0&tenantId=" + tenantId),
                    individualSearchRequest,
                    IndividualBulkResponse.class);
        } catch (Exception e) {
            throw new CustomException(INTERNAL_SERVER_ERROR, "Error while fetching individuals list");
        }
    }

    public Individual validateIndividual(HouseholdMember householdMember,
                                          IndividualBulkResponse searchResponse,
                                          Map<HouseholdMember, List<Error>> errorDetailsMap) {
        List<Individual> individuals = searchResponse.getIndividual().stream().filter(individual -> {
            if (householdMember.getIndividualId() != null) {
                return householdMember.getIndividualId().equals(individual.getId());
            } else {
                return householdMember.getIndividualClientReferenceId().equals(individual.getClientReferenceId());
            }
        }).collect(Collectors.toList());
        if(individuals.isEmpty()){
            Error error = Error.builder().errorMessage(INDIVIDUAL_NOT_FOUND_MESSAGE)
                    .errorCode(INDIVIDUAL_NOT_FOUND)
                    .type(Error.ErrorType.NON_RECOVERABLE)
                    .exception(new CustomException(INDIVIDUAL_NOT_FOUND,
                            INDIVIDUAL_NOT_FOUND_MESSAGE))
                    .build();
            populateErrorDetails(householdMember, error, errorDetailsMap);
            return null;
        }
        return individuals.get(0);
    }
}
