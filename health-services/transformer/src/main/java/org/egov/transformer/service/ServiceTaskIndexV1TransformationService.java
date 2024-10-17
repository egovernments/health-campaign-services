package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.upstream.Service;
import org.egov.common.producer.Producer;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ServiceTaskIndexV1TransformationService extends ServiceTaskTransformationService {


    protected ServiceTaskIndexV1TransformationService(ServiceTaskIndexV1Transformer transformer, Producer producer, TransformerProperties properties) {
        super(transformer, producer, properties);
    }

    @Override
    public void transform(List<Service> payloadList) {
        super.transform(payloadList);
    }
    @Override
    public String getTopic() {
        return properties.getTransformerProducerServiceTaskIndexV1Topic();
    }

}
