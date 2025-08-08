package org.egov.excelingestion.web.processor;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.web.models.GeneratedResource;
import org.egov.excelingestion.web.models.GeneratedResourceRequest;
import org.springframework.stereotype.Component;

@Component("villageTargetGenerateProcessor")
@Slf4j
public class VillageTargetGenerateProcessor implements IGenerateProcessor {

    @Override
    public GeneratedResource process(GeneratedResourceRequest request) {
        log.info("Processing village target generation for type: {}", request.getGeneratedResource().getType());
        // Implement village target generation logic here
        return request.getGeneratedResource();
    }

    @Override
    public String getType() {
        return "villageTarget";
    }
}
