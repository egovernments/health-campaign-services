package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.household.AdditionalFields;
import org.egov.common.models.household.Field;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.HouseholdMemberIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;
import static org.egov.transformer.Constants.GENDER;

@Slf4j
@Component
public class HouseholdMemberTransformationService {
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final CommonUtils commonUtils;
    private final IndividualService individualService;
    private final UserService userService;
    private final HouseholdService householdService;
    private final ObjectMapper objectMapper;
    private final BoundaryService boundaryService;
    private static final Set<String> BENEFICIARY_INFO_STRING_KEYS = new HashSet<>(Arrays.asList(
            INDIVIDUAL_CLIENT_REFERENCE_ID, GENDER, HOUSEHOLD_CLIENT_REFERENCE_ID, UNIQUE_BENEFICIARY_ID
    ));
    private static final Set<String> BENEFICIARY_INFO_INTEGER_KEYS = new HashSet<>(Arrays.asList(
            AGE, MEMBER_COUNT
    ));
    public HouseholdMemberTransformationService(TransformerProperties transformerProperties, Producer producer, CommonUtils commonUtils, IndividualService individualService, UserService userService, HouseholdService householdService, ObjectMapper objectMapper, BoundaryService boundaryService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.commonUtils = commonUtils;
        this.individualService = individualService;
        this.userService = userService;
        this.householdService = householdService;
        this.objectMapper = objectMapper;
        this.boundaryService = boundaryService;
    }

    public void transform(List<HouseholdMember> householdMemberList) {
        log.info("transforming for HHM id's {}", householdMemberList.stream()
                .map(HouseholdMember::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerHouseholdMemberIndexV1Topic();
        List<HouseholdMemberIndexV1> householdMemberIndexV1List = householdMemberList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation success for HHM id's {}", householdMemberIndexV1List.stream()
                .map(HouseholdMemberIndexV1::getHouseholdMember)
                .map(HouseholdMember::getId)
                .collect(Collectors.toList()));
        producer.push(topic, householdMemberIndexV1List);
    }

    private HouseholdMemberIndexV1 transform(HouseholdMember householdMember) {
        Map<String, String> boundaryHierarchy = null;
        Map<String, String> boundaryHierarchyCode = null;
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        List<Double> geoPoint = null;
        String individualClientReferenceId = householdMember.getIndividualClientReferenceId();
        AdditionalFields hhmAdditionalFields = householdMember.getAdditionalFields();

        boolean hasIndividualInfo = false;

        if (hhmAdditionalFields != null && hhmAdditionalFields.getFields() != null
                && !hhmAdditionalFields.getFields().isEmpty()) {
            hasIndividualInfo = hhmAdditionalFields.getFields().stream()
                    .anyMatch(field -> INDIVIDUAL_CLIENT_REFERENCE_ID.equals(field.getKey()));
        }
        Map<String, Object> individualDetails = new HashMap<>();

        if (hasIndividualInfo) {
            log.info("Fetching BeneficiaryInfo from task addFields");
            List<Field> fields = hhmAdditionalFields.getFields();
            if (fields != null) {
                fields.forEach(field -> {
                    String key = field.getKey();
                    String value = field.getValue();
                    if (BENEFICIARY_INFO_STRING_KEYS.contains(key)) {
                        individualDetails.put(key, value);
                    } else if (BENEFICIARY_INFO_INTEGER_KEYS.contains(key)) {
                        try {
                            individualDetails.put(key, Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            log.warn("Invalid integer for key '{}': '{}', defaulting to null", key, value);
                            individualDetails.put(key, null);
                        }
                    }
                });
            }
        } else {
            individualDetails.putAll(individualService.getIndividualInfo(individualClientReferenceId, householdMember.getTenantId()));
        }

        List<Household> households = householdService.searchHousehold(householdMember.getHouseholdClientReferenceId(), householdMember.getTenantId());
        String localityCode = null;
        if (!CollectionUtils.isEmpty(households) && households.get(0).getAddress() != null
                && households.get(0).getAddress().getLocality() != null
                && households.get(0).getAddress().getLocality().getCode() != null) {
            localityCode = households.get(0).getAddress().getLocality().getCode();
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, householdMember.getTenantId());
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
            geoPoint = commonUtils.getGeoPoint(households.get(0).getAddress());

            AdditionalFields additionalFields = households.get(0).getAdditionalFields();
            if (additionalFields != null && additionalFields.getFields() != null
                    && !CollectionUtils.isEmpty(additionalFields.getFields())) {
                additionalDetails = additionalFieldsToDetails(additionalFields.getFields());
            }
        }

        if (householdMember.getAdditionalFields() != null && householdMember.getAdditionalFields().getFields() != null
                && !CollectionUtils.isEmpty(householdMember.getAdditionalFields().getFields())) {
            List<Field> fields = householdMember.getAdditionalFields().getFields();
            addToAdditionalDetails(fields, additionalDetails);
        }

        Map<String, String> userInfoMap = userService.
                getUserInfo(householdMember.getTenantId(), householdMember.getClientAuditDetails().getLastModifiedBy());
        if (individualDetails.containsKey(HEIGHT) && individualDetails.containsKey(DISABILITY_TYPE)) {
            additionalDetails.put(HEIGHT, (Integer) individualDetails.get(HEIGHT));
            additionalDetails.put(DISABILITY_TYPE,(String) individualDetails.get(DISABILITY_TYPE));
        }
        if (!additionalDetails.has(PROJECT_ID) || !additionalDetails.has(PROJECT_TYPE_ID)) {
            commonUtils.addProjectDetailsToAdditionalDetails(additionalDetails,
                    householdMember.getClientAuditDetails().getLastModifiedBy() ,
                    householdMember.getTenantId());
        }
        String projectIdProjectTypeId = commonUtils.projectDetailsFromUserId(householdMember.getClientAuditDetails().getCreatedBy(), householdMember.getTenantId());

        String projectTypeId = null;
        if (!StringUtils.isEmpty(projectIdProjectTypeId)) {
            projectTypeId = projectIdProjectTypeId.split(":")[1];
        }
        log.info("HOUSEHOLD MEMBER ADD INFO {}, {}", additionalDetails, additionalDetails.get(PROJECT_TYPE_ID));
        String cycleIndex = commonUtils.fetchCycleIndexFromTime(householdMember.getTenantId(), projectTypeId, householdMember.getClientAuditDetails().getCreatedTime());
        additionalDetails.put(CYCLE_INDEX, cycleIndex);
        HouseholdMemberIndexV1 householdMemberIndexV1 = HouseholdMemberIndexV1.builder()
                .householdMember(householdMember)
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .dateOfBirth(individualDetails.containsKey(DATE_OF_BIRTH) ? (Long) individualDetails.get(DATE_OF_BIRTH) : null)
                .age(individualDetails.containsKey(AGE) ? (Integer) individualDetails.get(AGE) : null)
                .gender(individualDetails.containsKey(GENDER) ? (String) individualDetails.get(GENDER) : null)
                .geoPoint(geoPoint)
                .localityCode(localityCode)
                .taskDates(commonUtils.getDateFromEpoch(householdMember.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(householdMember.getAuditDetails().getLastModifiedTime()))
                .syncedTimeStamp(commonUtils.getTimeStampFromEpoch(householdMember.getAuditDetails().getLastModifiedTime()))
                .additionalDetails(additionalDetails)
                .build();
        return householdMemberIndexV1;
    }

    private ObjectNode additionalFieldsToDetails(List<Field> fields) {
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        fields.forEach(
                f -> additionalDetails.put(f.getKey(), f.getValue())
        );
        return additionalDetails;
    }
    private void addToAdditionalDetails(List<Field> fields, ObjectNode additionalDetails) {
        fields.forEach(
                f -> additionalDetails.put(f.getKey(), f.getValue())
        );
    }
}

