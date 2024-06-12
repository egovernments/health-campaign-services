package org.egov.transformer.service;

import static org.egov.transformer.Constants.CITY;
import static org.egov.transformer.Constants.COMMA;
import static org.egov.transformer.Constants.CYCLE_NUMBER;
import static org.egov.transformer.Constants.PROJECT;
import static org.egov.transformer.Constants.ROLE;
import static org.egov.transformer.Constants.TYPE_KEY;
import static org.egov.transformer.Constants.USERNAME;
import static org.egov.transformer.Constants.WAREHOUSE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.egov.common.models.facility.AdditionalFields;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.Field;
import org.egov.common.models.project.Project;
import org.egov.common.models.stock.SenderReceiverType;
import org.egov.common.models.stock.Stock;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.StockIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class StockTransformationService implements TransformationService<Stock> {
    protected final StockTransformationService.StockIndexV1Transformer transformer;

    protected final Producer producer;

    protected final TransformerProperties properties;
    protected final CommonUtils commonUtils;

    protected StockTransformationService(StockIndexV1Transformer transformer,
                                         Producer producer,
                                         TransformerProperties properties, CommonUtils commonUtils) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
        this.commonUtils = commonUtils;
    }

    @Override
    public void transform(List<Stock> payloadList) {
        log.info("transforming for ids {}", payloadList.stream()
                .map(Stock::getId).collect(Collectors.toList()));
        List<StockIndexV1> transformedPayloadList = payloadList.stream()
                .map(transformer::transform)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        log.info("transformation successful");
        producer.push(getTopic(),
                transformedPayloadList);
    }

    @Override
    public Operation getOperation() {
        return Operation.STOCK;
    }

    public abstract String getTopic();

    @Component
    static class StockIndexV1Transformer implements
            Transformer<Stock, StockIndexV1> {

        private final ProjectService projectService;

        private final FacilityService facilityService;
        private final CommonUtils commonUtils;
        private final UserService userService;
        private final ProductService productService;

        private final ObjectMapper objectMapper;

        StockIndexV1Transformer(ProjectService projectService, FacilityService facilityService,
                                CommonUtils commonUtils, UserService userService, ProductService productService, ObjectMapper objectMapper) {
            this.projectService = projectService;
            this.facilityService = facilityService;
            this.commonUtils = commonUtils;
            this.userService = userService;
            this.productService = productService;
            this.objectMapper = objectMapper;
        }

        @Override
        public List<StockIndexV1> transform(Stock stock) {
            Map<String, String> boundaryLabelToNameMap = new HashMap<>();
            String tenantId = stock.getTenantId();
            String projectId = stock.getReferenceId();
            Project project = projectService.getProject(projectId, tenantId);
            String projectTypeId = project.getProjectTypeId();
            
            /*
             * FIXME index stock model has to be updated
             */
            Facility facility = null;
			if (stock.getSenderType().equals(SenderReceiverType.WAREHOUSE)) {
				facility = facilityService.findFacilityById(stock.getSenderId(), stock.getTenantId());
			}
            
			Facility transactingFacility = null;
			if (stock.getReceiverId().equals(SenderReceiverType.WAREHOUSE)) {
				transactingFacility = facilityService.findFacilityById(stock.getReceiverId(),
						stock.getTenantId());
			}
            
            if (facility != null && facility.getAddress() != null && facility.getAddress().getLocality() != null
                    && facility.getAddress().getLocality().getCode() != null) {
                boundaryLabelToNameMap = projectService
                        .getBoundaryCodeToNameMap(facility.getAddress().getLocality().getCode(), stock.getTenantId());
            } else {
                if (stock.getReferenceIdType().equals(PROJECT)) {
                    boundaryLabelToNameMap = projectService
                            .getBoundaryCodeToNameMapByProjectId(stock.getReferenceId(), stock.getTenantId());
                }
            }
            ObjectNode boundaryHierarchy = (ObjectNode) commonUtils.getBoundaryHierarchy(tenantId, projectTypeId, boundaryLabelToNameMap);
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
            additionalDetails.put(CYCLE_NUMBER, cycleIndex);

            StockIndexV1 stockIndexV1 = StockIndexV1.builder()
                    .id(stock.getId())
                    .clientReferenceId(stock.getClientReferenceId())
                    .tenantId(stock.getTenantId())
                    .productVariant(stock.getProductVariantId())
                    .productName(productName)
                    .facilityId(stock.getSenderId())
                    .facilityName(facility != null ? facility.getName() : stock.getSenderId())
                    .transactingFacilityId(stock.getReceiverId())
                    .userName(userInfoMap.get(USERNAME))
                    .role(userInfoMap.get(ROLE))
                    .userAddress(userInfoMap.get(CITY))
                    .transactingFacilityName(transactingFacility != null ? transactingFacility.getName() : stock.getReceiverId())
                    .facilityType(facilityType)
                    .transactingFacilityType(transactingFacilityType)
                    .physicalCount(stock.getQuantity())
                    .eventType(stock.getTransactionType())
                    .reason(stock.getTransactionReason())
                    .eventTimeStamp(stock.getDateOfEntry() != null ?
                            stock.getDateOfEntry() : stock.getAuditDetails().getLastModifiedTime())
                    .createdTime(stock.getClientAuditDetails().getCreatedTime())
                    .dateOfEntry(stock.getDateOfEntry())
                    .createdBy(stock.getClientAuditDetails().getCreatedBy())
                    .lastModifiedTime(stock.getClientAuditDetails().getLastModifiedTime())
                    .lastModifiedBy(stock.getClientAuditDetails().getLastModifiedBy())
                    .additionalFields(stock.getAdditionalFields())
                    .syncedTimeStamp(syncedTimeStamp)
                    .syncedTime(stock.getAuditDetails().getLastModifiedTime())
                    .taskDates(commonUtils.getDateFromEpoch(stock.getClientAuditDetails().getLastModifiedTime()))
                    .syncedDate(commonUtils.getDateFromEpoch(stock.getAuditDetails().getLastModifiedTime()))
                    .facilityLevel(facilityLevel)
                    .transactingFacilityLevel(transactingFacilityLevel)
                    .waybillNumber(stock.getWayBillNumber())
                    .facilityTarget(facilityTarget)
                    .boundaryHierarchy(boundaryHierarchy)
                    .additionalDetails(additionalDetails)
                    .build();
            return Collections.singletonList(stockIndexV1);
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
}
