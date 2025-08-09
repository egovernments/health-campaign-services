package org.egov.excelingestion.web.processor;

import lombok.extern.slf4j.Slf4j;

import org.egov.excelingestion.web.models.GenerateResourceRequest;
import org.egov.excelingestion.web.models.GenerateResource;
import org.springframework.stereotype.Component;

@Component("userCredentialGenerateProcessor")
@Slf4j
public class UserCredentialGenerateProcessor implements IGenerateProcessor {

    @Override
    public GenerateResource process(GenerateResourceRequest request) {
        log.info("Processing user credential generation for type: {}", request.getGenerateResource().getType());
        // Implement user credential generation logic here
        return request.getGenerateResource();
    }

    @Override
    public String getType() {
        return "userCredential";
    }
}
