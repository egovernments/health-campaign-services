package org.egov.transformer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.facility.AdditionalFields;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.Field;
import org.egov.common.models.project.Project;
import org.egov.common.models.stock.Stock;
import org.egov.transformer.Constants;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.StockIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.egov.transformer.Constants.*;

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

        StockIndexV1Transformer(ProjectService projectService, FacilityService facilityService,
                                CommonUtils commonUtils, UserService userService, ProductService productService) {
            this.projectService = projectService;
            this.facilityService = facilityService;
            this.commonUtils = commonUtils;
            this.userService = userService;
            this.productService = productService;
        }

        @Override
        public List<StockIndexV1> transform(Stock stock) {
            Map<String, String> boundaryLabelToNameMap = new HashMap<>();
            String tenantId = stock.getTenantId();
            String projectId = stock.getReferenceId();
            Project project = projectService.getProject(projectId, tenantId);
            String projectTypeId = project.getProjectTypeId();
            Facility facility = facilityService.findFacilityById(stock.getFacilityId(), stock.getTenantId());
            Facility transactingFacility = facilityService.findFacilityById(stock.getTransactingPartyId(), stock.getTenantId());
            if (facility != null && facility.getAddress() != null && facility.getAddress().getLocality() != null
                    && facility.getAddress().getLocality().getCode() != null) {
                boundaryLabelToNameMap = projectService
                        .getBoundaryLabelToNameMap(facility.getAddress().getLocality().getCode(), stock.getTenantId());
            } else {
                if (stock.getReferenceIdType().equals(PROJECT)) {
                    boundaryLabelToNameMap = projectService
                            .getBoundaryLabelToNameMapByProjectId(stock.getReferenceId(), stock.getTenantId());
                }
            }
            ObjectNode boundaryHierarchy = (ObjectNode) commonUtils.getBoundaryHierarchy(tenantId, projectTypeId, boundaryLabelToNameMap);
            String facilityLevel = facility != null ? getFacilityLevel(facility) : null;
            String transactingFacilityLevel = transactingFacility != null ? getFacilityLevel(transactingFacility) : null;
            Long facilityTarget = facility != null ? getFacilityTarget(facility) : null;

            String facilityType = WAREHOUSE;
            String transactingFacilityType = WAREHOUSE;

            facilityType = facility != null ? getType(facilityType, facility) : facilityType;
            transactingFacilityType = transactingFacility != null ? getType(transactingFacilityType, transactingFacility) : transactingFacilityType;

            String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(stock.getAuditDetails().getCreatedTime());
            List<String> variantList = new ArrayList<>(Collections.singleton(stock.getProductVariantId()));
            String productName = String.join(COMMA, productService.getProductVariantNames(variantList, tenantId));
            Map<String, String> userInfoMap = userService.getUserInfo(stock.getTenantId(), stock.getAuditDetails().getCreatedBy());

            StockIndexV1 stockIndexV1 = StockIndexV1.builder()
                    .id(stock.getId())
                    .clientReferenceId(stock.getClientReferenceId())
                    .tenantId(stock.getTenantId())
                    .productVariant(stock.getProductVariantId())
                    .productName(productName)
                    .facilityId(stock.getFacilityId())
                    .facilityName(facility != null ? facility.getName() : stock.getFacilityId())
                    .transactingFacilityId(stock.getTransactingPartyId())
                    .userName(userInfoMap.get("userName"))
                    .role(userInfoMap.get("role"))
                    .transactingFacilityName(transactingFacility != null ? transactingFacility.getName() : stock.getTransactingPartyId())
                    .facilityType(facilityType)
                    .transactingFacilityType(transactingFacilityType)
                    .physicalCount(stock.getQuantity())
                    .eventType(stock.getTransactionType())
                    .reason(stock.getTransactionReason())
                    .eventTimeStamp(stock.getDateOfEntry() != null ?
                            stock.getDateOfEntry() : stock.getAuditDetails().getLastModifiedTime())
                    .createdTime(stock.getClientAuditDetails().getCreatedTime())
                    .dateOfEntry(stock.getDateOfEntry())
                    .createdBy(stock.getAuditDetails().getCreatedBy())
                    .lastModifiedTime(stock.getClientAuditDetails().getLastModifiedTime())
                    .lastModifiedBy(stock.getAuditDetails().getLastModifiedBy())
                    .additionalFields(stock.getAdditionalFields())
                    .syncedTimeStamp(syncedTimeStamp)
                    .syncedTime(stock.getAuditDetails().getCreatedTime())
                    .facilityLevel(facilityLevel)
                    .transactingFacilityLevel(transactingFacilityLevel)
                    .facilityTarget(facilityTarget)
                    .boundaryHierarchy(boundaryHierarchy)
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

        private Long getFacilityTarget(Facility facility) {
            AdditionalFields facilityAdditionalFields = facility.getAdditionalFields();
            if (facilityAdditionalFields != null) {
                List<Field> fields = facilityAdditionalFields.getFields();
                Optional<Field> field = fields.stream().filter(field1 -> FACILITY_TARGET_KEY.equalsIgnoreCase(field1.getKey())).findFirst();
                if (field.isPresent() && field.get().getValue() != null) {
                    return Long.valueOf(field.get().getValue());
                }
            }
            return null;
        }

        private String getFacilityLevel(Facility facility) {
            String facilityUsage = facility.getUsage();
            if (facilityUsage != null) {
                return WAREHOUSE.equalsIgnoreCase(facility.getUsage()) ?
                        (facility.getIsPermanent() ? DISTRICT_WAREHOUSE : SATELLITE_WAREHOUSE) : null;
            }
            return null;
        }
    }
}
