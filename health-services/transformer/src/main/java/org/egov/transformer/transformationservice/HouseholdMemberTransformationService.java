package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
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

import java.util.List;
import java.util.Map;
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
    private final ProjectService projectService;
    private final BoundaryService boundaryService;

    public HouseholdMemberTransformationService(TransformerProperties transformerProperties, Producer producer, CommonUtils commonUtils, IndividualService individualService, UserService userService, HouseholdService householdService, ObjectMapper objectMapper, ProjectService projectService, BoundaryService boundaryService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.commonUtils = commonUtils;
        this.individualService = individualService;
        this.userService = userService;
        this.householdService = householdService;
        this.objectMapper = objectMapper;
        this.projectService = projectService;
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
        Map<String, Object> individualDetails = individualService.getIndividualInfo(individualClientReferenceId, householdMember.getTenantId());

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

