package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.User;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.downstream.ReferralIndexV1;
import org.egov.transformer.models.downstream.StockIndexV1;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.service.transformer.Transformer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
public abstract class ReferralTransformationService implements TransformationService<Referral> {
    protected final ReferralTransformationService.ReferralIndexV1Transformer transformer;

    protected final Producer producer;

    protected final TransformerProperties properties;

    protected final CommonUtils commonUtils;

    @Autowired
    protected ReferralTransformationService(ReferralTransformationService.ReferralIndexV1Transformer transformer,
                                            Producer producer, TransformerProperties properties, CommonUtils commonUtils) {
        this.transformer = transformer;
        this.producer = producer;
        this.properties = properties;
        this.commonUtils = commonUtils;

    }

    @Override
    public void transform(List<Referral> payloadList) {
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
    static class ReferralIndexV1Transformer implements
            Transformer<Referral, ReferralIndexV1> {
        private final TransformerProperties properties;
        private final ServiceDefinitionService serviceDefinitionService;
        private final CommonUtils commonUtils;
        private UserService userService;

        @Autowired
        ReferralIndexV1Transformer(TransformerProperties properties, ServiceDefinitionService serviceDefinitionService, CommonUtils commonUtils, UserService userService) {

            this.properties = properties;
            this.serviceDefinitionService = serviceDefinitionService;
            this.commonUtils = commonUtils;
            this.userService = userService;
        }

        @Override
        public List<ReferralIndexV1> transform(Referral referral) {
            List<User> users = userService.getUsers(referral.getTenantId(), referral.getAuditDetails().getCreatedBy());

            //TODO ADD FIELDS and other LOGIC

            return Collections.singletonList(ReferralIndexV1.builder()
                    .id(referral.getId())
                    .referrerId(referral.getReferrerId())
                    .clientReferenceId(referral.getClientReferenceId())
                    .projectBeneficiaryId(referral.getProjectBeneficiaryId())
                    .projectBeneficiaryClientReferenceId(referral.getProjectBeneficiaryClientReferenceId())
                    .tenantId(referral.getTenantId())
                    .userName(userService.getUserName(users,referral.getAuditDetails().getCreatedBy()))
                    .role(userService.getStaffRole(referral.getTenantId(),users))
                    .reasons(referral.getReasons())
                    .referrerId(referral.getReferrerId())
                    .recipientId(referral.getRecipientId())
                    .recipientType(referral.getRecipientType())
                    .clientLastModifiedTime(referral.getClientAuditDetails().getLastModifiedTime())
                    .build());
        }
    }
}