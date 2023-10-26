package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.facility.AdditionalFields;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.Field;
import org.egov.common.models.stock.Stock;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.StockIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.*;

@Slf4j
public abstract class StockTransformationService implements TransformationService<Stock>{
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
        private final TransformerProperties properties;
        private final CommonUtils commonUtils;
        private UserService userService;
        StockIndexV1Transformer(ProjectService projectService, FacilityService facilityService,
                                TransformerProperties properties, CommonUtils commonUtils, UserService userService) {
            this.projectService = projectService;
            this.facilityService = facilityService;
            this.properties = properties;
            this.commonUtils = commonUtils;
            this.userService = userService;
        }

        @Override
        public List<StockIndexV1> transform(Stock stock) {
            Map<String, String> boundaryLabelToNameMap = null;
            Facility facility = facilityService.findFacilityById(stock.getFacilityId(), stock.getTenantId());
            Facility transactingFacility = facilityService.findFacilityById(stock.getTransactingPartyId(), stock.getTenantId());
            if (facility.getAddress().getLocality() != null && facility.getAddress().getLocality().getCode() != null) {
                boundaryLabelToNameMap = projectService
                        .getBoundaryLabelToNameMap(facility.getAddress().getLocality().getCode(), stock.getTenantId());
            } else {
                if (stock.getReferenceIdType().equals(PROJECT)) {
                    boundaryLabelToNameMap = projectService
                            .getBoundaryLabelToNameMapByProjectId(stock.getReferenceId(), stock.getTenantId());
                }
            }

            String facilityLevel = getFacilityLevel(facility);
            String transactingFacilityLevel = getFacilityLevel(transactingFacility);
            Long facilityTarget = getFacilityTarget(facility);

            String facilityType = WAREHOUSE;
            String transactingFacilityType = WAREHOUSE;

            facilityType = getType(facilityType, facility);
            transactingFacilityType = getType(transactingFacilityType, transactingFacility);

            List<User> users = userService.getUsers(stock.getTenantId(), stock.getAuditDetails().getCreatedBy());
            String syncedTimeStamp = commonUtils.getTimeStampFromEpoch(stock.getAuditDetails().getCreatedTime());

            return Collections.singletonList(StockIndexV1.builder()
                    .id(stock.getId())
                    .clientReferenceId(stock.getClientReferenceId())
                    .tenantId(stock.getTenantId())
                    .productVariant(stock.getProductVariantId())
                    .facilityId(stock.getFacilityId())
                    .facilityName(facility.getName())
                    .transactingFacilityId(stock.getTransactingPartyId())
                    .userName(userService.getUserName(users,stock.getAuditDetails().getCreatedBy()))
                    .role(userService.getStaffRole(stock.getTenantId(),users))
                    .transactingFacilityName(transactingFacility.getName())
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
                    .longitude(facility.getAddress() != null ? facility.getAddress().getLongitude() : null )
                    .latitude(facility.getAddress() != null ? facility.getAddress().getLatitude() : null)
                    .province(boundaryLabelToNameMap != null ? boundaryLabelToNameMap.get(properties.getProvince()) : null)
                    .district(boundaryLabelToNameMap != null ? boundaryLabelToNameMap.get(properties.getDistrict()) : null)
                    .administrativeProvince(boundaryLabelToNameMap != null ?
                            boundaryLabelToNameMap.get(properties.getAdministrativeProvince()) : null)
                    .locality(boundaryLabelToNameMap != null ? boundaryLabelToNameMap.get(properties.getLocality()) : null)
                    .village(boundaryLabelToNameMap != null ? boundaryLabelToNameMap.get(properties.getVillage()) : null)
                    .additionalFields(stock.getAdditionalFields())
                    .clientAuditDetails(stock.getClientAuditDetails())
                    .syncedTimeStamp(syncedTimeStamp)
                    .syncedTime(stock.getAuditDetails().getCreatedTime())
                    .facilityLevel(facilityLevel)
                    .transactingFacilityLevel(transactingFacilityLevel)
                    .facilityTarget(facilityTarget)
                    .build());
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
            return facility.getUsage().equalsIgnoreCase(WAREHOUSE) ?
                    (facility.getIsPermanent() ? DISTRICT_WAREHOUSE : SATELLITE_WAREHOUSE) : null;
        }
    }
}
