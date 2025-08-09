package org.egov.excelingestion.web.processor;

import lombok.extern.slf4j.Slf4j;

import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.GenerateResource;
import org.springframework.stereotype.Component;

@Component("villageTargetGenerateProcessor")
@Slf4j
public class VillageTargetGenerateProcessor implements IGenerateProcessor {

    @Override
    public GenerateResource process(GenerateResourceRequest request) {
        log.info("Processing village target generation for type: {}", request.getGenerateResource().getType());
        // Implement village target generation logic here
        return request.getGenerateResource();
    }

    @Override
    public String getType() {
        return "villageTarget";
    }
}
