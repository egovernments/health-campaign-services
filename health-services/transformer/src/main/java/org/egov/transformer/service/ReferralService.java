package org.egov.transformer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ReferralService {
    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;

    private final CommonUtils commonUtils;

    public ReferralService(TransformerProperties transformerProperties, ServiceRequestClient serviceRequestClient, CommonUtils commonUtils) {
        this.properties = transformerProperties;
        this.serviceRequestClient = serviceRequestClient;
        this.commonUtils = commonUtils;
    }
}
