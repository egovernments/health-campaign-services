package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.facility.AdditionalFields;
import org.egov.common.models.facility.Facility;
import org.egov.common.models.facility.Field;
import org.egov.common.models.stock.Stock;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.StockIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.PROJECT;
import static org.egov.transformer.Constants.TYPE_KEY;
import static org.egov.transformer.Constants.WAREHOUSE;

@Slf4j
public abstract class StockTransformationService implements TransformationService<Stock>{
    protected final StockTransformationService.StockIndexV1Transformer transformer;

    protected final Producer producer;

    protected final TransformerProperties properties;

    protected StockTransformationService(StockIndexV1Transformer transformer,
                                         Producer producer,
                                         TransformerProperties properties) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
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

        StockIndexV1Transformer(ProjectService projectService, FacilityService facilityService,
                                TransformerProperties properties) {
            this.projectService = projectService;
            this.facilityService = facilityService;
            this.properties = properties;

        }

        @Override
        public List<StockIndexV1> transform(Stock stock) {
            Map<String, String> boundaryLabelToNameMap = null;
            Facility facility = facilityService.findFacilityById(stock.getFacilityId(), stock.getTenantId());
            if (facility.getAddress().getLocality() != null && facility.getAddress().getLocality().getCode() != null) {
                boundaryLabelToNameMap = projectService
                        .getBoundaryLabelToNameMap(facility.getAddress().getLocality().getCode(), stock.getTenantId());
            } else {
                if (stock.getReferenceIdType().equals(PROJECT)) {
                    boundaryLabelToNameMap = projectService
                            .getBoundaryLabelToNameMapByProjectId(stock.getReferenceId(), stock.getTenantId());
                }
            }

            String transactingPartyName = null;
            String transactingPartyType = stock.getTransactingPartyType();
            String facilityType = transactingPartyType;
            if (WAREHOUSE.equals(transactingPartyType)) {
                Facility transactingFacility = facilityService.findFacilityById(stock.getTransactingPartyId(), stock.getTenantId());
                transactingPartyName = transactingFacility.getName();
            } else {
                transactingPartyName = stock.getTransactingPartyId();
            }

            AdditionalFields facilityAdditionalFields = facility.getAdditionalFields();
            if (facilityAdditionalFields != null) {
                List<Field> fields = facilityAdditionalFields.getFields();
                Optional<Field> field = fields.stream().filter(field1 -> TYPE_KEY.equalsIgnoreCase(field1.getKey())).findFirst();
                if (field.isPresent() && field.get().getValue() != null) {
                    facilityType = field.get().getValue();
                }
            }

            return Collections.singletonList(StockIndexV1.builder()
                    .id(stock.getId())
                    .productVariant(stock.getProductVariantId())
                    .facilityId(stock.getFacilityId())
                    .facilityName(facility.getName())
                    .transactingFacilityId(stock.getTransactingPartyId())
                    .transactingPartyName(transactingPartyName)
                    .transactingPartyType(transactingPartyType)
                    .facilityType(facilityType)
                    .physicalCount(stock.getQuantity())
                    .eventType(stock.getTransactionType())
                    .reason(stock.getTransactionReason())
                    .eventTimeStamp(stock.getDateOfEntry() != null ?
                            stock.getDateOfEntry() : stock.getAuditDetails().getLastModifiedTime())
                    .createdTime(stock.getAuditDetails().getCreatedTime())
                    .dateOfEntry(stock.getDateOfEntry())
                    .createdBy(stock.getAuditDetails().getCreatedBy())
                    .lastModifiedTime(stock.getAuditDetails().getLastModifiedTime())
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
                    .build());
        }
    }
}
