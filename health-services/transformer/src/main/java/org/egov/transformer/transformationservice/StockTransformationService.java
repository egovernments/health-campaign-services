package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.facility.AdditionalFields;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.Field;
import org.egov.common.models.project.Project;
import org.egov.common.models.stock.Stock;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.StockIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.FacilityService;
import org.egov.transformer.service.ProductService;
import org.egov.transformer.service.ProjectService;
import org.egov.transformer.service.UserService;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

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

    public StockTransformationService(Producer producer, FacilityService facilityService, TransformerProperties transformerProperties, CommonUtils commonUtils, ProjectService projectService, UserService userService, ObjectMapper objectMapper, ProductService productService) {
        this.producer = producer;
        this.facilityService = facilityService;
        this.transformerProperties = transformerProperties;
        this.commonUtils = commonUtils;
        this.projectService = projectService;
        this.userService = userService;
        this.objectMapper = objectMapper;
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
        String tenantId = stock.getTenantId();
        String projectId = stock.getReferenceId();
        Project project = projectService.getProject(projectId, tenantId);
        String projectTypeId = project.getProjectTypeId();
        Facility facility = facilityService.findFacilityById(stock.getFacilityId(), stock.getTenantId());
        Facility transactingFacility = facilityService.findFacilityById(stock.getTransactingPartyId(), stock.getTenantId());
        if (facility != null && facility.getAddress() != null && facility.getAddress().getLocality() != null
                && facility.getAddress().getLocality().getCode() != null) {
            boundaryHierarchy = commonUtils.getBoundaryHierarchyWithLocalityCode(facility.getAddress().getLocality().getCode(), tenantId);
        } else {
            if (stock.getReferenceIdType().equals(PROJECT)) {
                boundaryHierarchy = commonUtils.getBoundaryHierarchyWithProjectId(stock.getReferenceId(), tenantId);
            }
        }

        String facilityLevel = facility != null ? facilityService.getFacilityLevel(facility) : null;
        String transactingFacilityLevel = transactingFacility != null ? facilityService.getFacilityLevel(transactingFacility) : null;
        Long facilityTarget = facility != null ? facilityService.getFacilityTarget(facility) : null;

        String facilityType = WAREHOUSE;
        String transactingFacilityType = WAREHOUSE;

        facilityType = facility != null ? getType(facilityType, facility) : facilityType;
        transactingFacilityType = transactingFacility != null ? getType(transactingFacilityType, transactingFacility) : transactingFacilityType;

        String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(stock.getAuditDetails().getLastModifiedTime());
        List<String> variantList = new ArrayList<>(Collections.singleton(stock.getProductVariantId()));
        String productName = String.join(COMMA, productService.getProductVariantNames(variantList, tenantId));
        Map<String, String> userInfoMap = userService.getUserInfo(stock.getTenantId(), stock.getClientAuditDetails().getCreatedBy());
        Integer cycleIndex = commonUtils.fetchCycleIndex(tenantId, projectTypeId, stock.getAuditDetails());
        ObjectNode additionalDetails = objectMapper.createObjectNode();
        additionalDetails.put(CYCLE_INDEX, cycleIndex);
        StockIndexV1 stockIndexV1 = StockIndexV1.builder()
                .id(stock.getId())
                .clientReferenceId(stock.getClientReferenceId())
                .tenantId(tenantId)
                .productVariant(stock.getProductVariantId())
                .productName(productName)
                .facilityId(stock.getFacilityId())
                .facilityName(facility != null ? facility.getName() : stock.getFacilityId())
                .facilityType(facilityType)
                .facilityLevel(facilityLevel)
                .facilityTarget(facilityTarget)
                .transactingFacilityId(stock.getTransactingPartyId())
                .transactingFacilityName(transactingFacility != null ? transactingFacility.getName() : stock.getTransactingPartyId())
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
                .additionalDetails(additionalDetails)
                .build();
        return stockIndexV1;
    }

    private String getType(String transactingFacilityType, Facility transactingFacility) {
        AdditionalFields transactingFacilityAdditionalFields = transactingFacility.getAdditionalFields();
        if (transactingFacilityAdditionalFields != null) {
            List<Field> fields = transactingFacilityAdditionalFields.getFields();
            Optional<Field> field = fields.stream().filter(field1 -> TYPE_KEY.equalsIgnoreCase(field1.getKey())).findFirst();
            if (field.isPresent() && field.get().getValue() != null) {
                transactingFacilityType = field.get().getValue();
            }
        }
        return transactingFacilityType;
    }
}
