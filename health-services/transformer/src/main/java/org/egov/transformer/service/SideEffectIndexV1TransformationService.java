package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SideEffectIndexV1TransformationService extends SideEffectTransformationService {
    @Autowired
    protected SideEffectIndexV1TransformationService(SideEffectIndexV1Transformer transformer,
                                                Producer producer, TransformerProperties properties) {
        super(transformer, producer, properties);
    }

    @Override
    public void transform(List<SideEffect> payloadList) {
        super.transform(payloadList);
    }

    @Override
    public String getTopic() {
        return properties.getTransformerProducerBulkProjectStaffIndexV1Topic();
    }

}
