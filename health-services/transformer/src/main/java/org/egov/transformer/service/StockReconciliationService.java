package org.egov.transformer.service;

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
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class StockReconciliationService {

    private final ProjectService projectService;
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final FacilityService facilityService;
    private final CommonUtils commonUtils;
    private final ObjectMapper objectMapper;

    private static final Set<String> ADDITIONAL_DETAILS_INTEGER_FIELDS = new HashSet<>(Arrays.asList(
            RECEIVED, ISSUED, RETURNED, LOST, GAINED, DAMAGED, INHAND, CYCLE_NUMBER
    ));

    public StockReconciliationService(ProjectService projectService, TransformerProperties transformerProperties, Producer producer, FacilityService facilityService, CommonUtils commonUtils, ObjectMapper objectMapper, StockTransformationService stockTransformationService) {
        this.projectService = projectService;
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.facilityService = facilityService;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
    }

    public void transform(List<StockReconciliation> payloadList) {
        String topic = transformerProperties.getTransformerProducerStockReconciliationRegisterIndexV1Topic();
        List<StockReconciliationIndexV1> transformedPayloadList = payloadList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation successful");
        producer.push(topic,
                transformedPayloadList);
    }

    public StockReconciliationIndexV1 transform(StockReconciliation stockReconciliation){
        Map<String, String> boundaryLabelToNameMap = new HashMap<>();
        String tenantId = stockReconciliation.getTenantId();
        String projectId = stockReconciliation.getReferenceId();
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();
        Facility facility = facilityService.findFacilityById(stockReconciliation.getFacilityId(), tenantId);
        String facilityLevel = facility != null ? facilityService.getFacilityLevel(facility) : null;
        Long facilityTarget = facility != null ? facilityService.getFacilityTarget(facility) : null;

        if (facility != null && facility.getAddress() != null && facility.getAddress().getLocality() != null
                && facility.getAddress().getLocality().getCode() != null) {
            boundaryLabelToNameMap = projectService
                    .getBoundaryLabelToNameMap(facility.getAddress().getLocality().getCode(), stockReconciliation.getTenantId());
        } else {
            if (stockReconciliation.getReferenceIdType().equals(PROJECT)) {
                boundaryLabelToNameMap = projectService
                        .getBoundaryLabelToNameMapByProjectId(stockReconciliation.getReferenceId(), stockReconciliation.getTenantId());
            }
        }
        ObjectNode boundaryHierarchy = (ObjectNode) commonUtils.getBoundaryHierarchy(tenantId, projectTypeId, boundaryLabelToNameMap);
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(stockReconciliation.getAuditDetails().getLastModifiedTime());
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        if (stockReconciliation.getAdditionalFields() != null) {
            addAdditionalDetails(stockReconciliation.getAdditionalFields(), additionalDetails);
            addCycleIndex(additionalDetails, stockReconciliation.getAuditDetails(), tenantId, projectTypeId);
        }

        StockReconciliationIndexV1 stockReconciliationIndexV1 = StockReconciliationIndexV1.builder()
                .stockReconciliation(stockReconciliation)
                .facilityName(facility != null ? facility.getName() : stockReconciliation.getFacilityId())
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
                    Double doubleValue = Double.valueOf(value);
                    Integer intValue = doubleValue.intValue();
                    additionalDetails.put(key, intValue);
                } catch (NumberFormatException e) {
                    log.warn("Invalid integer format for key '{}': value '{}'. Storing as null.", key, value);
                    additionalDetails.put(key, (JsonNode) null);
                }
            }
            else {
                additionalDetails.put(key, field.getValue());
            }
        });
    }


    private void addCycleIndex(ObjectNode additionalDetails, AuditDetails auditDetails, String tenantId, String projectTypeId) {
        if (!additionalDetails.has(CYCLE_NUMBER)) {
            Integer cycleIndex = commonUtils.fetchCycleIndex(tenantId, projectTypeId, auditDetails);
            additionalDetails.put(CYCLE_NUMBER, cycleIndex);
        }
    }
}
