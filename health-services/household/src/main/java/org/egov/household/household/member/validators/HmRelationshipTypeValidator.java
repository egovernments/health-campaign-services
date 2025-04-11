package org.egov.household.household.member.validators;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.household.Relationship;
import org.egov.common.validator.Validator;
import org.egov.mdms.model.MasterDetail;
import org.egov.mdms.model.MdmsResponse;
import org.egov.mdms.service.MdmsClientService;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.household.Constants.HOUSEHOLD_MEMBER_RELATIONSHIP_CONFIG_NOT_FOUND_MESSAGE;
import static org.egov.household.Constants.INVALID_HOUSEHOLD_MEMBER_RELATIONSHIP;
import static org.egov.household.Constants.INVALID_HOUSEHOLD_MEMBER_RELATIONSHIP_MESSAGE;

/**
 * Validator class for checking valid household member's relationship type,
 * if relationships are there.
 * checks for allowed relationship types from MDMS configs and compares with relationships
 * @author holashchand
 */
@Slf4j
@Component
@Order(10)
public class HmRelationshipTypeValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private final MdmsClientService mdmsClientService;

    private static final String MODULE_NAME = "HCM";
    private static final String MASTER_NAME = "HOUSEHOLD_MEMBER_RELATIONSHIP_TYPES";
    private static final String RELATIONSHIP_TYPE_KEYNAME = "code";

    /**
     * Constructor to initialize the MdmsClientService dependency.
     *
     * @param mdmsClientService The client for fetching mdms data.
     */
    public HmRelationshipTypeValidator(MdmsClientService mdmsClientService) {
        this.mdmsClientService = mdmsClientService;
    }

    /**
     * Validates allowed relationship types for household members
     *
     * @param householdMemberBulkRequest The bulk request containing household members.
     * @return A map containing household members and their associated error details.
     */
    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest householdMemberBulkRequest) {
        HashMap<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        List<HouseholdMember> householdMembers = householdMemberBulkRequest.getHouseholdMembers();
        if (CollectionUtils.isEmpty(householdMembers)) return errorDetailsMap;

        String tenantId = householdMembers.stream().findAny().get().getTenantId();
        Map<String, List<MasterDetail>> masterDetailsMap = new HashMap<>();
        masterDetailsMap.put(MODULE_NAME, Collections.singletonList(
                MasterDetail.builder()
                        .name(MASTER_NAME)
                        .filter("[?(@.active==true)]")
                        .build()));

        log.debug("Getting mdms config for Household member relationship types");
        MdmsResponse mdmsResponse = mdmsClientService.getMaster(householdMemberBulkRequest.getRequestInfo()
        , tenantId, masterDetailsMap);

        Set<String> allowedRelationshipTypes = null;
        try {
            allowedRelationshipTypes = mdmsResponse.getMdmsRes()
                    .get(MODULE_NAME).get(MASTER_NAME).stream()
                    .map(relationshipConfig -> (String) ((Map<String, Object>) relationshipConfig).get(RELATIONSHIP_TYPE_KEYNAME))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new CustomException(INVALID_HOUSEHOLD_MEMBER_RELATIONSHIP, HOUSEHOLD_MEMBER_RELATIONSHIP_CONFIG_NOT_FOUND_MESSAGE);
        }

        log.debug("Allowed relationship types: {}", allowedRelationshipTypes);

        if (!CollectionUtils.isEmpty(allowedRelationshipTypes)) {
            Set<String> finalAllowedRelationshipTypes = allowedRelationshipTypes;
            householdMembers.stream()
                    .filter(householdMember -> !CollectionUtils.isEmpty(householdMember.getRelationships()))
                    .forEach(householdMember -> {
                        Set<String> relationshipTypes = householdMember.getRelationships().stream().map(Relationship::getRelationshipType)
                                .collect(Collectors.toSet());
                        if (!finalAllowedRelationshipTypes.containsAll(relationshipTypes)) {
                            Error error = Error.builder().errorMessage(INVALID_HOUSEHOLD_MEMBER_RELATIONSHIP_MESSAGE)
                                    .errorCode(INVALID_HOUSEHOLD_MEMBER_RELATIONSHIP)
                                    .type(Error.ErrorType.NON_RECOVERABLE)
                                    .exception(new CustomException(INVALID_HOUSEHOLD_MEMBER_RELATIONSHIP, INVALID_HOUSEHOLD_MEMBER_RELATIONSHIP_MESSAGE))
                                    .build();
                            log.error("validation failed for household member: {} with error: {}", householdMember, error);
                            populateErrorDetails(householdMember, error, errorDetailsMap);
                        }

                    });
        }


        log.debug("Household member relationship validation completed successfully, total errors: {}", errorDetailsMap.size());
        return errorDetailsMap;
    }

}
