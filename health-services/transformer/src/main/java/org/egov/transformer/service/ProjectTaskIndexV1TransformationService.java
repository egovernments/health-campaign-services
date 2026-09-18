package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Task;
import org.egov.transformer.config.TransformerProperties;
import org.egov.common.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ProjectTaskIndexV1TransformationService extends ProjectTaskTransformationService {

    @Autowired
    public ProjectTaskIndexV1TransformationService(ProjectTaskIndexV1Transformer transformer,
                                                   Producer producer, TransformerProperties properties) {
        super(transformer, producer, properties);
    }

    @Override
    public void transform(List<Task> payloadList) {
        super.transform(payloadList);
    }

    @Override
    public String getTopic() {
        return properties.getTransformerProducerBulkProjectTaskIndexV1Topic();
    }



}
