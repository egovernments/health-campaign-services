package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.stock.AdditionalFields;
import org.egov.common.models.project.Project;
import org.egov.common.models.stock.Stock;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.boundary.BoundaryHierarchyResult;
import org.egov.transformer.models.downstream.StockIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.*;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;
import static org.egov.transformer.Constants.STAFF;

@Slf4j
@Component
public class StockTransformationService {
    private final Producer producer;
    private final TransformerProperties transformerProperties;
    private final FacilityService facilityService;
    private final CommonUtils commonUtils;
    private final ProjectService projectService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final BoundaryService boundaryService;
    private final ProductService productService;
    private static final Set<String> ADDITIONAL_DETAILS_DOUBLE_FIELDS = new HashSet<>(Arrays.asList(LAT, LNG));

    public StockTransformationService(Producer producer, FacilityService facilityService, TransformerProperties transformerProperties, CommonUtils commonUtils, ProjectService projectService, UserService userService, ObjectMapper objectMapper, ProductService productService, BoundaryService boundaryService) {
        this.producer = producer;
        this.facilityService = facilityService;
        this.transformerProperties = transformerProperties;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.boundaryService = boundaryService;
        this.productService = productService;
    }

    public void transform(List<Stock> stocksList) {
        log.info("transforming for STOCK id's {}", stocksList.stream()
                .map(Stock::getId).collect(Collectors.toList()));
        String topic = transformerProperties.getTransformerProducerBulkStockIndexV1Topic();
        List<StockIndexV1> transformedStockList = stocksList.stream()
                .map(this::transform)
                .collect(Collectors.toList());
        log.info("transformation success for STOCK id's {}", transformedStockList.stream()
                .map(StockIndexV1::getId)
                .collect(Collectors.toList()));
        producer.push(topic, transformedStockList);
    }

    private StockIndexV1 transform(Stock stock) {
        Map<String, String> boundaryHierarchy = new HashMap<>();
        Map<String, String> boundaryHierarchyCode = new HashMap<>();

        String transactingFacilityType = getTransactingFacilityType(stock), facilityType = getFacilityType(stock);
        String facilityId = getFacilityId(stock), transactingFacilityId = getTransactingFacilityId(stock);
        String facilityLevel = null, transactingFacilityLevel = null;
        Long facilityTarget = null;
        String facilityName, transactingFacilityName;
        String tenantId = stock.getTenantId();
        String projectId = stock.getReferenceId();
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();

        if (!STAFF.equalsIgnoreCase(facilityType)) {
            Facility facility = facilityService.findFacilityById(facilityId, stock.getTenantId());
            if (facility != null && facility.getAddress() != null && facility.getAddress().getLocality() != null
                    && facility.getAddress().getLocality().getCode() != null) {
                BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithLocalityCode(facility.getAddress().getLocality().getCode(), tenantId);
                boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
                boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
            } else if (stock.getReferenceIdType().equals(PROJECT)) {
                BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(stock.getReferenceId(), tenantId);
                boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
                boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
            }
            facilityLevel = facility != null ? facilityService.getFacilityLevel(facility) : null;
            facilityType = facility != null ? facilityService.getType(facilityType, facility) : facilityType;
            facilityTarget = facility != null ? facilityService.getFacilityTarget(facility) : null;
            facilityName = facility != null ? facility.getName() : facilityId;
        } else {
            facilityName = userService.getUserInfo(tenantId, facilityId).get(USERNAME);
            List<ProjectStaff> projectStaffList = projectService.searchProjectStaff(new ArrayList<>(Collections.singleton(facilityId)), tenantId);
            if (projectStaffList != null) {
                BoundaryHierarchyResult boundaryHierarchyResult = boundaryService.getBoundaryHierarchyWithProjectId(projectStaffList.get(0).getProjectId(), tenantId);
                boundaryHierarchy = boundaryHierarchyResult.getBoundaryHierarchy();
                boundaryHierarchyCode = boundaryHierarchyResult.getBoundaryHierarchyCode();
            }
        }

        if (!STAFF.equalsIgnoreCase(transactingFacilityType)) {
            Facility transactingFacility = facilityService.findFacilityById(transactingFacilityId, stock.getTenantId());
            transactingFacilityLevel = transactingFacility != null ? facilityService.getFacilityLevel(transactingFacility) : null;
            transactingFacilityType = transactingFacility != null ? facilityService.getType(transactingFacilityType, transactingFacility) : transactingFacilityType;
            transactingFacilityName = transactingFacility != null ? transactingFacility.getName() : transactingFacilityId;

        } else {
            transactingFacilityName = userService.getUserInfo(tenantId, transactingFacilityId).get(USERNAME);
        }

        if (boundaryHierarchy.isEmpty() && PROJECT.equalsIgnoreCase(stock.getReferenceIdType().toString())) {
            boundaryHierarchy = projectService.getBoundaryHierarchyWithProjectId(stock.getReferenceId(), tenantId);
        }
        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(stock.getAuditDetails().getLastModifiedTime());
        List<String> variantList = new ArrayList<>(Collections.singleton(stock.getProductVariantId()));
        String productName = String.join(COMMA, productService.getProductVariantNames(variantList, tenantId));
        Map<String, String> userInfoMap = userService.getUserInfo(stock.getTenantId(), stock.getClientAuditDetails().getCreatedBy());
        Integer cycleIndex = commonUtils.fetchCycleIndex(tenantId, projectTypeId, stock.getAuditDetails());
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        additionalDetails.put(CYCLE_INDEX, cycleIndex);

        if (ObjectUtils.isNotEmpty(stock.getAdditionalFields()) && !CollectionUtils.isEmpty(stock.getAdditionalFields().getFields())) {
            addAdditionalDetails(stock.getAdditionalFields(), additionalDetails);
        }

        StockIndexV1 stockIndexV1 = StockIndexV1.builder()
                .id(stock.getId())
                .clientReferenceId(stock.getClientReferenceId())
                .tenantId(tenantId)
                .productVariant(stock.getProductVariantId())
                .productName(productName)
                .facilityId(facilityId)
                .facilityName(facilityName)
                .facilityType(facilityType)
                .facilityLevel(facilityLevel)
                .facilityTarget(facilityTarget)
                .transactingFacilityId(transactingFacilityId)
                .transactingFacilityName(transactingFacilityName)
                .transactingFacilityType(transactingFacilityType)
                .transactingFacilityLevel(transactingFacilityLevel)
                .userName(userInfoMap.get(USERNAME))
                .nameOfUser(userInfoMap.get(NAME))
                .role(userInfoMap.get(ROLE))
                .userAddress(userInfoMap.get(CITY))
                .physicalCount(stock.getQuantity())
                .eventType(stock.getTransactionType())
                .reason(stock.getTransactionReason())
                .dateOfEntry(stock.getDateOfEntry() != null ?
                        stock.getDateOfEntry() : stock.getAuditDetails().getLastModifiedTime())
                .createdTime(stock.getClientAuditDetails().getCreatedTime())
                .createdBy(stock.getClientAuditDetails().getCreatedBy())
                .lastModifiedTime(stock.getClientAuditDetails().getLastModifiedTime())
                .lastModifiedBy(stock.getClientAuditDetails().getLastModifiedBy())
                .additionalFields(stock.getAdditionalFields())
                .syncedTimeStamp(syncedTimeStamp)
                .syncedTime(stock.getAuditDetails().getLastModifiedTime())
                .taskDates(commonUtils.getDateFromEpoch(stock.getClientAuditDetails().getLastModifiedTime()))
                .syncedDate(commonUtils.getDateFromEpoch(stock.getAuditDetails().getLastModifiedTime()))
                .waybillNumber(stock.getWayBillNumber())
                .projectType(project.getProjectType())
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .additionalDetails(additionalDetails)
                .build();
        return stockIndexV1;
    }

    private String getFacilityId(Stock stock) {
        return RECEIVED.equalsIgnoreCase(stock.getTransactionType().toString()) ?
                stock.getReceiverId() :
                stock.getSenderId();
    }

    private String getTransactingFacilityId(Stock stock) {
        return RECEIVED.equalsIgnoreCase(stock.getTransactionType().toString()) ?
                stock.getSenderId() :
                stock.getReceiverId();
    }

    private String getFacilityType(Stock stock) {
        return RECEIVED.equalsIgnoreCase(stock.getTransactionType().toString()) ?
                stock.getReceiverType().toString() :
                stock.getSenderType().toString();
    }

    private String getTransactingFacilityType(Stock stock) {
        return RECEIVED.equalsIgnoreCase(stock.getTransactionType().toString()) ?
                stock.getSenderType().toString() :
                stock.getReceiverType().toString();
    }
    private void addAdditionalDetails(AdditionalFields additionalFields, ObjectNode additionalDetails) {
        additionalFields.getFields().forEach(field -> {
            String key = field.getKey();
            String value = field.getValue();
            if (ADDITIONAL_DETAILS_DOUBLE_FIELDS.contains(key)) {
                try {
                    additionalDetails.put(key, Double.valueOf(value));
                } catch (NumberFormatException e) {
                    log.warn("Invalid number format for key '{}': value '{}'. Storing as null.", key, value);
                    additionalDetails.put(key, (JsonNode) null);
                }
            } else {
                additionalDetails.put(key, value);
            }
        });
    }
}
