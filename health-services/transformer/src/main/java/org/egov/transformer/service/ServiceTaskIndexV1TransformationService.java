package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.servicerequest.web.models.Service;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ServiceTaskIndexV1TransformationService extends ServiceTaskTransformationService {
    @Autowired
    public ServiceTaskIndexV1TransformationService(ServiceTaskIndexV1Transformer transformer,
                                                   Producer producer, TransformerProperties properties) {
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
