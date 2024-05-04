package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.stock.AdditionalFields;
import org.egov.common.models.project.Project;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.StockReconciliationIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.FacilityService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class StockReconciliationTransformationService {

    private final ProjectService projectService;
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final FacilityService facilityService;
    private final CommonUtils commonUtils;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    private static final Set<String> ADDITIONAL_DETAILS_INTEGER_FIELDS = new HashSet<>(Arrays.asList(
            RECEIVED, ISSUED, RETURNED, LOST, GAINED, DAMAGED, INHAND, CYCLE_INDEX
    ));

    public StockReconciliationTransformationService(ProjectService projectService, TransformerProperties transformerProperties, Producer producer, FacilityService facilityService, CommonUtils commonUtils, ObjectMapper objectMapper, UserService userService) {
        this.projectService = projectService;
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.facilityService = facilityService;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
        this.userService = userService;
    }

    public void transform(List<StockReconciliation> stockReconciliationList) {
        log.info("transforming for STOCK RECONCILIATION id's {}", stockReconciliationList.stream()
                .map(StockReconciliation::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerStockReconciliationRegisterIndexV1Topic();
        List<StockReconciliationIndexV1> stockReconciliationIndexV1List = stockReconciliationList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation success for STOCK RECONCILIATION id's {}", stockReconciliationIndexV1List.stream()
                .map(StockReconciliationIndexV1::getStockReconciliation)
                .map(StockReconciliation::getId)
                .collect(Collectors.toList()));
        producer.push(topic, stockReconciliationIndexV1List);
    }

    public StockReconciliationIndexV1 transform(StockReconciliation stockReconciliation) {
        Map<String, String> boundaryHierarchy = new HashMap<>();
        String tenantId = stockReconciliation.getTenantId();
        String projectId = stockReconciliation.getReferenceId();
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();
        Facility facility = facilityService.findFacilityById(stockReconciliation.getFacilityId(), tenantId);
        String facilityLevel = facility != null ? facilityService.getFacilityLevel(facility) : null;
        Long facilityTarget = facility != null ? facilityService.getFacilityTarget(facility) : null;

        Map<String, String> userInfoMap = userService.getUserInfo(stockReconciliation.getClientAuditDetails().getLastModifiedBy(), tenantId);

        if (facility != null && facility.getAddress() != null && facility.getAddress().getLocality() != null
                && facility.getAddress().getLocality().getCode() != null) {
            boundaryHierarchy = commonUtils.getBoundaryHierarchyWithLocalityCode(facility.getAddress().getLocality().getCode(), tenantId);
        } else {
            if (stockReconciliation.getReferenceIdType().equals(PROJECT)) {
                boundaryHierarchy = commonUtils.getBoundaryHierarchyWithProjectId(stockReconciliation.getReferenceId(), tenantId);
            }
        }
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(stockReconciliation.getAuditDetails().getLastModifiedTime());
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        if (stockReconciliation.getAdditionalFields() != null) {
            addAdditionalDetails(stockReconciliation.getAdditionalFields(), additionalDetails);
            addCycleIndex(additionalDetails, stockReconciliation.getAuditDetails(), tenantId, projectTypeId);
        }

        StockReconciliationIndexV1 stockReconciliationIndexV1 = StockReconciliationIndexV1.builder()
                .stockReconciliation(stockReconciliation)
                .facilityName(facility != null ? facility.getName() : stockReconciliation.getFacilityId())
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .facilityTarget(facilityTarget)
                .facilityLevel(facilityLevel)
                .syncedTimeStamp(syncedTimeStamp)
                .syncedTime(stockReconciliation.getAuditDetails().getLastModifiedTime())
                .boundaryHierarchy(boundaryHierarchy)
                .additionalDetails(additionalDetails)
                .taskDates(commonUtils.getDateFromEpoch(stockReconciliation.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(stockReconciliation.getAuditDetails().getLastModifiedTime()))
                .build();

        return stockReconciliationIndexV1;
    }

    private void addAdditionalDetails(AdditionalFields additionalFields, ObjectNode additionalDetails) {
        additionalFields.getFields().forEach(field -> {
            String key = field.getKey();
            String value = field.getValue();
            if (ADDITIONAL_DETAILS_INTEGER_FIELDS.contains(key)) {
                try {
                    double doubleValue = Double.parseDouble(value);
                    additionalDetails.put(key, doubleValue);
                } catch (NumberFormatException e) {
                    log.warn("Invalid integer format for key '{}': value '{}'. Storing as null.", key, value);
                    additionalDetails.put(key, (JsonNode) null);
                }
            } else {
                additionalDetails.put(key, field.getValue());
            }
        });
    }


    private void addCycleIndex(ObjectNode additionalDetails, AuditDetails auditDetails, String tenantId, String projectTypeId) {
        if (!additionalDetails.has(CYCLE_INDEX)) {
            Integer cycleIndex = commonUtils.fetchCycleIndex(tenantId, projectTypeId, auditDetails);
            additionalDetails.put(CYCLE_INDEX, cycleIndex);
        }
    }
}
