package org.egov.processor.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.processor.config.Configuration;
import org.egov.processor.web.models.mdmsV2.Mdms;
import org.egov.processor.web.models.mdmsV2.MdmsCriteriaReqV2;
import org.egov.processor.web.models.mdmsV2.MdmsCriteriaV2;
import org.egov.processor.web.models.mdmsV2.MdmsResponseV2;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.egov.processor.config.ServiceConstants.*;

@Slf4j
@Component
public class MdmsV2Util {

    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;

    private Configuration config;

    public MdmsV2Util(RestTemplate restTemplate, ObjectMapper objectMapper, Configuration config)
    {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    public List<Mdms> fetchMdmsV2Data(RequestInfo requestInfo, String tenantId, String schemaCode, String uniqueIdentifier)
    {
        StringBuilder uri = getMdmsV2Uri();
        MdmsCriteriaReqV2 mdmsCriteriaReqV2 = getMdmsV2Request(requestInfo, tenantId, schemaCode, uniqueIdentifier);
        MdmsResponseV2 mdmsResponseV2 = null;
        try {
            mdmsResponseV2 = restTemplate.postForObject(uri.toString(), mdmsCriteriaReqV2, MdmsResponseV2.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_MDMS, e);
        }

        if(ObjectUtils.isEmpty(mdmsResponseV2.getMdms()))
        {
            log.error(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE + " - " + tenantId);
            throw new CustomException(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE, NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE);
        }

        return mdmsResponseV2.getMdms();
    }

    private StringBuilder getMdmsV2Uri()
    {
        StringBuilder uri = new StringBuilder();
        return uri.append(config.getMdmsHost()).append(config.getMdmsV2EndPoint());
    }

    private MdmsCriteriaReqV2 getMdmsV2Request(RequestInfo requestInfo, String tenantId, String schemaCode, String uniqueIdentifier)
    {
        MdmsCriteriaV2 mdmsCriteriaV2 = MdmsCriteriaV2.builder()
                .tenantId(tenantId)
                .schemaCode(schemaCode)
                .uniqueIdentifiers(uniqueIdentifier != null ? Collections.singletonList(uniqueIdentifier) : null)
                .limit(config.getDefaultLimit())
                .offset(config.getDefaultOffset())
                .build();

        return MdmsCriteriaReqV2.builder()
                .requestInfo(requestInfo)
                .mdmsCriteriaV2(mdmsCriteriaV2).build();
    }

}
