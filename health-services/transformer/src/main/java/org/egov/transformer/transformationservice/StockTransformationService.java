package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.egov.common.models.facility.Facility;
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
import org.egov.common.models.stock.TransactionType;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

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
    private final ProductService productService;
    private final BoundaryService boundaryService;
    private static final Set<String> ADDITIONAL_DETAILS_DOUBLE_FIELDS = new HashSet<>(Arrays.asList(LAT, LNG));

    public StockTransformationService(Producer producer, FacilityService facilityService, TransformerProperties transformerProperties, CommonUtils commonUtils, ProjectService projectService, UserService userService, ObjectMapper objectMapper, ProductService productService, BoundaryService boundaryService) {
        this.producer = producer;
        this.facilityService = facilityService;
        this.transformerProperties = transformerProperties;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.productService = productService;
        this.boundaryService = boundaryService;
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

        String transactingFacilityType = stock.getSenderType().toString();
        String facilityType = stock.getReceiverType().toString();

        String tenantId = stock.getTenantId();
        String projectId = stock.getReferenceId();
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();

        String facilityId = fetchFacilityId(stock.getReceiverId(), stock.getSenderId(), stock.getTransactionType());
        String transactingFacilityId = fetchTransactingFacilityId(stock.getReceiverId(), stock.getSenderId(), stock.getTransactionType());


        Facility facility = facilityService.findFacilityById(facilityId, stock.getTenantId());
        Facility transactingFacility = facilityService.findFacilityById(transactingFacilityId, stock.getTenantId());
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

        String facilityLevel = facility != null ? facilityService.getFacilityLevel(facility) : null;
        String transactingFacilityLevel = transactingFacility != null ? facilityService.getFacilityLevel(transactingFacility) : null;
        Long facilityTarget = facility != null ? facilityService.getFacilityTarget(facility) : null;

//        String facilityType = WAREHOUSE;
//        String transactingFacilityType = WAREHOUSE;

        facilityType = facility != null ? facilityService.getType(facilityType, facility) : facilityType;
        transactingFacilityType = transactingFacility != null ? facilityService.getType(transactingFacilityType, transactingFacility) : transactingFacilityType;

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
                .facilityName(facility != null ? facility.getName() : facilityId)
                .facilityType(facilityType)
                .facilityLevel(facilityLevel)
                .facilityTarget(facilityTarget)
                .transactingFacilityId(transactingFacilityId)
                .transactingFacilityName(transactingFacility != null ? transactingFacility.getName() : transactingFacilityId)
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
                .boundaryHierarchy(boundaryHierarchy)
                .boundaryHierarchyCode(boundaryHierarchyCode)
                .additionalDetails(additionalDetails)
                .build();
        return stockIndexV1;
    }

    private String fetchFacilityId(String receiverId, String senderId, TransactionType transactionType) {
        if (RECEIVED.equalsIgnoreCase(transactionType.toString())) {
            return receiverId;
        } else return senderId;
    }
    private String fetchTransactingFacilityId(String receiverId, String senderId, TransactionType transactionType) {
        if (RECEIVED.equalsIgnoreCase(transactionType.toString())) {
            return senderId;
        } else return receiverId;
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
