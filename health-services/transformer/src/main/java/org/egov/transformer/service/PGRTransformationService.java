package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.downstream.PGRIndex;
import org.egov.transformer.models.pgr.Service;
import org.egov.transformer.producer.Producer;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PGRTransformationService {

    private final ObjectMapper objectMapper;
    private final TransformerProperties transformerProperties;
    private final Producer producer;
    private final CommonUtils commonUtils;
    private static final HashMap<String, String> translations = null;


    public PGRTransformationService(ObjectMapper objectMapper, ServiceDefinitionService serviceDefinitionService, TransformerProperties transformerProperties, Producer producer, ProjectService projectService, UserService userService, CommonUtils commonUtils) {

        this.objectMapper = objectMapper;
        this.transformerProperties = transformerProperties;
        this.producer = producer;
        this.commonUtils = commonUtils;
    }

    public void transform(List<Service> pgrList) {
        List<PGRIndex> pgrIndexList = new ArrayList<>();
        String topic = transformerProperties.getTransformerProducerCreatePgrTopic();
        pgrList.forEach(service -> {
            transform(service, pgrIndexList);
        });
        producer.push(topic, pgrIndexList);
    }

    private void transform(Service service, List<PGRIndex> pgrIndexList) {
        if ("PENDING_ASSIGNMENT".equalsIgnoreCase(service.getApplicationStatus()) ) {
            service.setApplicationStatus("MOZ_PENDING_ASSIGNMENT");
        }
        PGRIndex pgrIndex = PGRIndex.builder()
                .service(service)
                .build();
    }

}
