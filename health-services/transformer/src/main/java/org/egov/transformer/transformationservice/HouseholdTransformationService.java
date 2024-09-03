package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.household.AdditionalFields;
import org.egov.common.models.household.Field;
import org.egov.common.models.household.Household;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.HouseholdIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.HouseholdService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class HouseholdTransformationService {
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final CommonUtils commonUtils;
    private final ProjectService projectService;
    private final HouseholdService householdService;

    public HouseholdTransformationService(TransformerProperties transformerProperties, Producer producer, ObjectMapper objectMapper, UserService userService, CommonUtils commonUtils, ProjectService projectService, HouseholdService householdService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
        this.householdService = householdService;
    }

    public void transform(List<Household> householdList) {
        log.info("transforming for HOUSEHOLD id's {}", householdList.stream()
                .map(Household::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerBulkHouseholdIndexV1Topic();
        List<HouseholdIndexV1> householdIndexV1List = householdList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation success for HOUSEHOLD id's {}", householdIndexV1List.stream()
                .map(HouseholdIndexV1::getHousehold)
                .map(Household::getId)
                .collect(Collectors.toList()));
        producer.push(topic, householdIndexV1List);
    }

    private HouseholdIndexV1 transform(Household household) {
        householdService.searchHousehold(household.getClientReferenceId(), household.getTenantId());
        Map<String, String> boundaryHierarchy = null;

        String localityCode;
        if (household.getAddress() != null
                && household.getAddress().getLocality() != null
                && household.getAddress().getLocality().getCode() != null) {
            localityCode = household.getAddress().getLocality().getCode();
            boundaryHierarchy = projectService.getBoundaryHierarchyWithLocalityCode(localityCode, household.getTenantId());
        }

        Map<String, String> userInfoMap = userService.getUserInfo(household.getTenantId(), household.getAuditDetails().getCreatedBy());
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(household.getAuditDetails().getLastModifiedTime());

        ObjectNode additionalDetails = objectMapper.createObjectNode();
        AdditionalFields additionalFields = household.getAdditionalFields();
        if (additionalFields != null && additionalFields.getFields() != null
                && !CollectionUtils.isEmpty(additionalFields.getFields())) {
            additionalDetails = additionalFieldsToDetails(additionalFields.getFields());
        }
        int pregnantWomenCount = additionalDetails.has(PREGNANTWOMEN) ? additionalDetails.get(PREGNANTWOMEN).asInt(0) : 0;
        int childrenCount = additionalDetails.has(CHILDREN) ? additionalDetails.get(CHILDREN).asInt(0) : 0;
        if (pregnantWomenCount > 0 || childrenCount > 0) {
            additionalDetails.put(ISVULNERABLE, true);
        }

        return HouseholdIndexV1.builder()
                .household(household)
                .userName(userInfoMap.get(USERNAME))
                .role(userInfoMap.get(ROLE))
                .nameOfUser(userInfoMap.get(NAME))
                .userAddress(userInfoMap.get(CITY))
                .geoPoint(commonUtils.getGeoPoint(household.getAddress()))
                .boundaryHierarchy(boundaryHierarchy)
                .taskDates(commonUtils.getDateFromEpoch(household.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(household.getAuditDetails().getLastModifiedTime()))
                .syncedTimeStamp(syncedTimeStamp)
                .additionalDetails(additionalDetails)
                .build();
    }

    private ObjectNode additionalFieldsToDetails(List<Field> fields) {
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        fields.forEach(
                f -> additionalDetails.put(f.getKey(), f.getValue())
        );
        return additionalDetails;
    }
}
