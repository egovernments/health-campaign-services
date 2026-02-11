package org.egov.common.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Service
@ConditionalOnExpression("!'${egov.mdms.integration.enabled}'.isEmpty() && ${egov.mdms.integration.enabled:false} && !'${egov.mdms.host}'.isEmpty() && !'${egov.mdms.search.endpoint}'.isEmpty()")
public class MdmsService {

    private final ServiceRequestClient restRepo;
    private final String mdmsHost;
    private final String mdmsUrl;

    @Autowired
    public MdmsService(ServiceRequestClient restRepo,
                       @Value("${egov.mdms.host}") String mdmsHost,
                       @Value("${egov.mdms.search.endpoint}") String mdmsUrl ) {
        this.restRepo = restRepo;
        this.mdmsHost = mdmsHost;
        this.mdmsUrl = mdmsUrl;
    }

    public <T> T fetchConfig(Object request, Class<T> clazz) throws Exception {
        T response;
        try {
            response = restRepo.fetchResult(new StringBuilder(mdmsHost+mdmsUrl), request, clazz);
        } catch (HttpClientErrorException e) {
            throw new CustomException("HTTP_CLIENT_ERROR",
                    String.format("%s - %s", e.getMessage(), e.getResponseBodyAsString()));
        }
        return response;
    }

}
