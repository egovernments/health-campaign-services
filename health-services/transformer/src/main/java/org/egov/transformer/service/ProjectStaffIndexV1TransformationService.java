package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.ProjectStaff;
import org.egov.transformer.config.TransformerProperties;
import org.egov.common.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class ProjectStaffIndexV1TransformationService extends ProjectStaffTransformationService {

    @Autowired
    public ProjectStaffIndexV1TransformationService(ProjectStaffIndexV1Transformer transformer,
                                                    Producer producer, TransformerProperties properties) {
        super(transformer, producer, properties);
    }

    @Override
    public void transform(List<ProjectStaff> payloadList) {
        super.transform(payloadList);
    }

    @Override
    public String getTopic() {
        return properties.getTransformerProducerBulkProjectStaffIndexV1Topic();
    }



}
