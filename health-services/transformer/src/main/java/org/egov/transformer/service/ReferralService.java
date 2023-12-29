package org.egov.transformer.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.referralmanagement.sideeffect.*;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.http.client.ServiceRequestClient;
import org.egov.transformer.utils.CommonUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.egov.transformer.Constants.*;

@Service
@Slf4j
public class ReferralService {

    private final TransformerProperties properties;

    private final ServiceRequestClient serviceRequestClient;


    private final CommonUtils commonUtils;

    public ReferralService(TransformerProperties stockConfiguration, ServiceRequestClient serviceRequestClient, CommonUtils commonUtils) {
        this.properties = stockConfiguration;
        this.serviceRequestClient = serviceRequestClient;
        this.commonUtils = commonUtils;
    }

}
