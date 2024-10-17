package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.stock.Field;
import org.egov.common.models.stock.StockReconciliation;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.StockReconciliationIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
@Component
public class StockReconciliationTransformationService {

    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final FacilityService facilityService;
    private final CommonUtils commonUtils;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final ProductService productService;
    private final ProjectService projectService;
    private final BoundaryService boundaryService;

    private static final Set<String> ADDITIONAL_DETAILS_DOUBLE_FIELDS = new HashSet<>(Arrays.asList(
            RECEIVED, ISSUED, RETURNED, LOST, GAINED, DAMAGED, INHAND
    ));

    public StockReconciliationTransformationService(TransformerProperties transformerProperties, Producer producer, FacilityService facilityService, CommonUtils commonUtils, ObjectMapper objectMapper, UserService userService, ProductService productService, ProjectService projectService, BoundaryService boundaryService) {
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.facilityService = facilityService;
        this.commonUtils = commonUtils;
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.productService = productService;
        this.projectService = projectService;
        this.boundaryService = boundaryService;
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
        Map<String, String> boundaryHierarchyCode = new HashMap<>();
        String tenantId = stockReconciliation.getTenantId();
        Facility facility = facilityService.findFacilityById(stockReconciliation.getFacilityId(), tenantId);
        String facilityLevel = facility != null ? facilityService.getFacilityLevel(facility) : null;
        Long facilityTarget = facility != null ? facilityService.getFacilityTarget(facility) : null;
        String localityCode = null;

        if (facility != null && facility.getAddress() != null &&
                facility.getAddress().getLocality() != null &&
                facility.getAddress().getLocality().getCode() != null) {
            localityCode = facility.getAddress().getLocality().getCode();
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(localityCode, tenantId);
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        } else if (stockReconciliation.getReferenceIdType().equals(PROJECT)) {
            BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(stockReconciliation.getReferenceId(), tenantId);
            boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
            boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
        }
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        if (stockReconciliation.getAdditionalFields() != null && stockReconciliation.getAdditionalFields().getFields() != null
                && !CollectionUtils.isEmpty(stockReconciliation.getAdditionalFields().getFields())) {
            additionalDetails = additionalFieldsToDetails(stockReconciliation.getAdditionalFields().getFields());
        }

        Map<String, String> userInfoMap = userService.getUserInfo(tenantId, stockReconciliation.getClientAuditDetails().getLastModifiedBy());
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(stockReconciliation.getAuditDetails().getLastModifiedTime());
        String productName = String.join(COMMA, productService.getProductVariantNames(Collections.singletonList(stockReconciliation.getProductVariantId()), tenantId));

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
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .productName(productName)
                .localityCode(localityCode)
                .additionalDetails(additionalDetails)
                .taskDates(commonUtils.getDateFromEpoch(stockReconciliation.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(stockReconciliation.getAuditDetails().getLastModifiedTime()))
                .build();

        return stockReconciliationIndexV1;
    }

    private ObjectNode additionalFieldsToDetails(List<Field> fields) {
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        fields.forEach(
                field -> {
                    String key = field.getKey();
                    String value = field.getValue();
                    if (ADDITIONAL_DETAILS_DOUBLE_FIELDS.contains(key)) {
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
                }
        );
        return additionalDetails;
    }
}
