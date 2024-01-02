package org.egov.transformer.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ProjectStaffIndexV1;
import org.egov.transformer.models.downstream.SideEffectsIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public abstract class SideEffectTransformationService implements TransformationService<SideEffect>{
    protected final SideEffectIndexV1Transformer transformer;

    protected final Producer producer;

    protected final TransformerProperties properties;

    @Autowired
    protected SideEffectTransformationService(SideEffectTransformationService.SideEffectIndexV1Transformer transformer,
                                               Producer producer, TransformerProperties properties) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
    }

    @Override
    public void transform(List<SideEffect> payloadList){
        log.info("transforming for ids {}", payloadList.stream()
                .map(SideEffect::getId).collect(Collectors.toList()));
        List<SideEffectsIndexV1> transformedPayloadList = payloadList.stream()
                .map(transformer::transform)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        log.info("transformation successful");
        producer.push(getTopic(),
                transformedPayloadList);
    }

    public abstract String getTopic();

    @Override
    public Operation getOperation() {
        return Operation.SIDE_EFFECT;
    }
    @Component
    static class SideEffectIndexV1Transformer implements Transformer<SideEffect, SideEffectsIndexV1> {
        private final SideEffectService sideEffectService;
        private final TransformerProperties properties;
        private final ObjectMapper objectMapper;

        @Autowired
        SideEffectIndexV1Transformer(SideEffectService sideEffectService, TransformerProperties properties, ObjectMapper objectMapper) {
            this.sideEffectService = sideEffectService;
            this.properties = properties;
            this.objectMapper = objectMapper;
        }

        @Override
        public List<SideEffectsIndexV1> transform(SideEffect sideEffect) {
            String tenantId = sideEffect.getTenantId();
            List<SideEffectsIndexV1> sideEffectsIndexV1List = new ArrayList<>();
            SideEffectsIndexV1 sideEffectsIndexV1 = SideEffectsIndexV1.builder()
                    .id(sideEffect.getId())
                    .tenantId(sideEffect.getTenantId())
                    .clientReferenceId(sideEffect.getClientReferenceId())
                    .taskId(sideEffect.getTaskId())
                    .taskClientReferenceId(sideEffect.getTaskClientReferenceId())
                    .projectBeneficiaryId(sideEffect.getProjectBeneficiaryId())
                    .projectBeneficiaryClientReferenceId(sideEffect.getProjectBeneficiaryClientReferenceId())
                    .symptoms(sideEffect.getSymptoms())
                    .isDeleted(sideEffect.getIsDeleted())
                    .rowVersion(sideEffect.getRowVersion())
                    .auditDetails(sideEffect.getAuditDetails())
                    .clientAuditDetails(sideEffect.getClientAuditDetails())
                    .build();
            return null;
        }
    }
}
