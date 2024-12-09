package org.egov.transformer.aggregator.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProjectBeneficiaryConsumer {

    private final ObjectMapper objectMapper;

    public ProjectBeneficiaryConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
}
