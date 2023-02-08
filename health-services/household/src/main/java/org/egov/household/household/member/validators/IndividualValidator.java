package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.validator.Validator;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberBulkRequest;
import org.egov.household.web.models.Individual;
import org.egov.household.web.models.IndividualResponse;
import org.egov.household.web.models.IndividualSearch;
import org.egov.household.web.models.IndividualSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.household.Constants.GET_INDIVIDUAL_CLIENT_REFERENCE_ID;
import static org.egov.household.Constants.GET_INDIVIDUAL_ID;
import static org.egov.household.Constants.INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD;
import static org.egov.household.Constants.INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD_MESSAGE;
import static org.egov.household.Constants.INDIVIDUAL_NOT_FOUND;
import static org.egov.household.Constants.INDIVIDUAL_NOT_FOUND_MESSAGE;
import static org.egov.household.Constants.INTERNAL_SERVER_ERROR;

@Component
@Order(7)
@Slf4j
public class IndividualValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {
    private final ServiceRequestClient serviceRequestClient;

    private final HouseholdMemberConfiguration householdMemberConfiguration;

    private final HouseholdMemberRepository householdMemberRepository;

    public IndividualValidator(ServiceRequestClient serviceRequestClient,
                               HouseholdMemberConfiguration householdMemberConfiguration,
                               HouseholdMemberRepository householdMemberRepository) {
        this.serviceRequestClient = serviceRequestClient;
        this.householdMemberConfiguration = householdMemberConfiguration;
        this.householdMemberRepository = householdMemberRepository;
    }

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest householdMemberBulkRequest) {
        HashMap<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();

        // TODO: Rename to validHouseholdMembers
        List<HouseholdMember> householdMembers = householdMemberBulkRequest.getHouseholdMembers().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());

        if(!householdMembers.isEmpty()){
            RequestInfo requestInfo = householdMemberBulkRequest.getRequestInfo();
            String tenantId = getTenantId(householdMembers);

            // TODO: Use IndividualBulkResponse for search
            IndividualResponse searchResponse = searchIndividualBeneficiary(
                    householdMembers,
                    requestInfo,
                    tenantId
            );
            householdMembers.forEach(householdMember -> {
                Individual individual = validateIndividual(householdMember,
                        searchResponse, errorDetailsMap);
                if (individual != null) {
                    householdMember.setIndividualId(individual.getId());
                    householdMember.setIndividualClientReferenceId(individual.getClientReferenceId());

                    List<HouseholdMember> individualSearchResult = householdMemberRepository
                            .findIndividual(individual.getId());
                    if(!individualSearchResult.isEmpty()) {
                        Error error = Error.builder().errorMessage(INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD_MESSAGE)
                                .errorCode(INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD)
                                .type(Error.ErrorType.NON_RECOVERABLE)
                                .exception(new CustomException(INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD,
                                        INDIVIDUAL_ALREADY_MEMBER_OF_HOUSEHOLD_MESSAGE))
                                .build();
                        populateErrorDetails(householdMember, error, errorDetailsMap);
                    }
                }
            });
        }

        return errorDetailsMap;
    }

    private IndividualResponse searchIndividualBeneficiary(
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

    private IndividualResponse getIndividualResponse(String tenantId, IndividualSearchRequest individualSearchRequest) {
        try {
            return serviceRequestClient.fetchResult(
                    new StringBuilder(householdMemberConfiguration.getIndividualServiceHost()
                            + householdMemberConfiguration.getIndividualServiceSearchUrl()
                            + "?limit=10&offset=0&tenantId=" + tenantId),
                    individualSearchRequest,
                    IndividualResponse.class);
        } catch (Exception e) {
            throw new CustomException(INTERNAL_SERVER_ERROR, "Error while fetching individuals list");
        }
    }

    private Individual validateIndividual(HouseholdMember householdMember,
                                          IndividualResponse searchResponse,
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
