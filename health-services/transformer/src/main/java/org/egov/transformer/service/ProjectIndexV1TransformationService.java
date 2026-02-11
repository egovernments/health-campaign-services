package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Project;
import org.egov.transformer.config.TransformerProperties;
import org.egov.common.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ProjectIndexV1TransformationService extends ProjectTransformationService {

    @Autowired
    public ProjectIndexV1TransformationService(ProjectIndexV1Transformer transformer,
                                               Producer producer, TransformerProperties properties) {
        super(transformer, producer, properties);
    }

    @Override
    public void transform(List<Project> payloadList) {
        super.transform(payloadList);
    }

    @Override
    public String getTopic() {
        return properties.getTransformerProducerBulkProjectIndexV1Topic();
    }



}
