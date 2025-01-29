package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.AdditionalFields;
import org.egov.common.models.household.Field;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.HouseholdMemberIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.HouseholdService;
import org.egov.transformer.service.IndividualService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.UserService;
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
    private final ProjectService projectService;
    private static final List<String> INDIVIDUAL_ADDITIONAL_FIELDS = new ArrayList<>(Arrays.asList(HEIGHT, WEIGHT, DISABILITY_TYPE));

    public HouseholdMemberTransformationService(TransformerProperties transformerProperties, Producer producer, CommonUtils commonUtils, IndividualService individualService, UserService userService, HouseholdService householdService, ObjectMapper objectMapper, ProjectService projectService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.commonUtils = commonUtils;
        this.individualService = individualService;
        this.userService = userService;
        this.householdService = householdService;
        this.objectMapper = objectMapper;
        this.projectService = projectService;
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
        Map<String, Object> additionalDetails = new HashMap<>();
        List<Double> geoPoint = null;
        String individualClientReferenceId = householdMember.getIndividualClientReferenceId();
        Map<String, Object> individualDetails = individualService.getIndividualInfo(individualClientReferenceId, householdMember.getTenantId());

        List<Household> households = householdService.searchHousehold(householdMember.getHouseholdClientReferenceId(), householdMember.getTenantId());
        String localityCode = null;
        if (!CollectionUtils.isEmpty(households) && households.get(0).getAddress() != null
                && households.get(0).getAddress().getLocality() != null
                && households.get(0).getAddress().getLocality().getCode() != null) {
            localityCode = households.get(0).getAddress().getLocality().getCode();
            boundaryHierarchy = projectService.getBoundaryHierarchyWithLocalityCode(localityCode, householdMember.getTenantId());
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

        Map<String, Object> finalAdditionalDetails = additionalDetails;
        INDIVIDUAL_ADDITIONAL_FIELDS.forEach(field ->{
            if (individualDetails.containsKey(field)){
                finalAdditionalDetails.put(field, individualDetails.get(field));
            }
        });

        HouseholdMemberIndexV1 householdMemberIndexV1 = HouseholdMemberIndexV1.builder()
                .householdMember(householdMember)
                .boundaryHierarchy(boundaryHierarchy)
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .dateOfBirth(individualDetails.containsKey(DATE_OF_BIRTH) ? (Long) individualDetails.get(DATE_OF_BIRTH) : null)
                .age(individualDetails.containsKey(AGE) ? (Integer) individualDetails.get(AGE) : null)
                .identifierType(individualDetails.containsKey(INDIVIDUAL_IDENTIFIER_TYPE) ? (String) individualDetails.get(INDIVIDUAL_IDENTIFIER_TYPE) : null)
                .gender(individualDetails.containsKey(GENDER) ? (String) individualDetails.get(GENDER) : null)
                .geoPoint(geoPoint)
                .localityCode(localityCode)
                .taskDates(commonUtils.getDateFromEpoch(householdMember.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(householdMember.getAuditDetails().getLastModifiedTime()))
                .syncedTimeStamp(commonUtils.getTimeStampFromEpoch(householdMember.getAuditDetails().getLastModifiedTime()))
                .additionalDetails(finalAdditionalDetails)
                .build();
        return householdMemberIndexV1;
    }

    private Map<String,Object> additionalFieldsToDetails(List<Field> fields) {
        Map<String,Object> additionalDetails = new HashMap<>();
        fields.forEach(
                f -> additionalDetails.put(f.getKey(), f.getValue())
        );
        return additionalDetails;
    }
    private void addToAdditionalDetails(List<Field> fields, Map<String,Object> additionalDetails) {
        fields.forEach(
                f -> additionalDetails.put(f.getKey(), f.getValue())
        );
    }
}

