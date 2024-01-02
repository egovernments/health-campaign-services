package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SideEffectService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;


    private final CommonUtils commonUtils;

    public SideEffectService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, CommonUtils commonUtils) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.commonUtils = commonUtils;
    }

}