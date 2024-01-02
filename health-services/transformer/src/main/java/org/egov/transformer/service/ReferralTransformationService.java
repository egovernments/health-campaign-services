package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.Task;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ReferralIndexV1;
import org.egov.transformer.models.downstream.SideEffectsIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.transformer.Constants.AGE;
import static org.egov.transformer.Constants.DATE_OF_BIRTH;

@Slf4j
public abstract class ReferralTransformationService implements TransformationService<Referral>{
    protected final ReferralIndexV1Transformer transformer;

    protected final Producer producer;

    protected final TransformerProperties properties;

    @Autowired
    protected ReferralTransformationService(ReferralTransformationService.ReferralIndexV1Transformer transformer,
                                              Producer producer, TransformerProperties properties) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
    }
    @Override
    public void transform(List<Referral> payloadList){
        log.info("transforming for ids {}", payloadList.stream()
                .map(Referral::getId).collect(Collectors.toList()));
        List<ReferralIndexV1> transformedPayloadList = payloadList.stream()
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
        return Operation.REFERRAL;
    }

    @Component
    static class ReferralIndexV1Transformer implements Transformer<Referral, ReferralIndexV1> {
        private final ReferralService referralService;
        private final TransformerProperties properties;
        private final ObjectMapper objectMapper;
        private IndividualService individualService;
        private ProjectService projectService;

        @Autowired
        ReferralIndexV1Transformer(ReferralService referralService, TransformerProperties properties, ObjectMapper objectMapper) {
            this.referralService = referralService;
            this.properties = properties;
            this.objectMapper = objectMapper;
        }

        @Override
        public List<ReferralIndexV1> transform(Referral referral) {
            String tenantId = referral.getTenantId();
            List<ReferralIndexV1> referralIndexV1List = new ArrayList<>();
            ProjectBeneficiary projectBeneficiary = null;
            List<ProjectBeneficiary> projectBeneficiaries = projectService
                    .searchBeneficiary(referral.getProjectBeneficiaryClientReferenceId(), tenantId);

            if (!CollectionUtils.isEmpty(projectBeneficiaries)) {
                projectBeneficiary = projectBeneficiaries.get(0);
            }
            Map individualDetails = individualService.findIndividualByClientReferenceId(projectBeneficiary.getBeneficiaryClientReferenceId(), tenantId);
            ReferralIndexV1 referralIndexV1 = ReferralIndexV1.builder()
                    .id(referral.getId())
                    .tenantId(referral.getTenantId())
                    .projectBeneficiaryId(referral.getProjectBeneficiaryId())
                    .projectBeneficiaryClientReferenceId(referral.getProjectBeneficiaryClientReferenceId())
                    .dateOfBirth(individualDetails.containsKey(DATE_OF_BIRTH) ? (Long) individualDetails.get(DATE_OF_BIRTH) : null)
                    .age(individualDetails.containsKey(AGE) ? (Integer) individualDetails.get(AGE) : null)
                    .referrerId(referral.getReferrerId())
                    .reasons(referral.getReasons())
                    .clientReferenceId(referral.getClientReferenceId())
                    .rowVersion(referral.getRowVersion())
                    .recipientType(referral.getRecipientType())
                    .recipientId(referral.getRecipientId())
                    .isDeleted(referral.getIsDeleted())
                    .auditDetails(referral.getAuditDetails())
                    .clientAuditDetails(referral.getClientAuditDetails())
                    .build();
            referralIndexV1List.add(referralIndexV1);
            return referralIndexV1List;
        }
    }
}
