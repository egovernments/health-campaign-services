package org.egov.product.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.product.config.ProductConfiguration;
import org.egov.product.web.models.*;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import static org.egov.product.config.ServiceConstants.*;

@Service
@Slf4j
public class MdmsV2Service {
    private final RestTemplate restTemplate;

    private final ObjectMapper mapper;

    private final ProductConfiguration configs;

    @Autowired
    public MdmsV2Service(RestTemplate restTemplate, @Qualifier("objectMapper") ObjectMapper mapper, ProductConfiguration configs) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.configs = configs;
    }


    public List<Mdms> fetchMdmsData(RequestInfo requestInfo, String tenantId, Boolean isProduct, List <String> ids, Integer limit, Integer offset) {
        StringBuilder uri = new StringBuilder();
        uri.append(configs.getMdmsHost()).append(configs.getMdmsEndPoint());
        MdmsCriteriaReqV2 mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId, isProduct, ids, limit, offset);
        Object mdmsResponseMap  = new HashMap<>();
        MdmsResponseV2 mdmsResponse = new MdmsResponseV2();
        try {
            mdmsResponseMap  = restTemplate.postForObject(uri.toString(), mdmsCriteriaReq, Map.class);
            mdmsResponse = mapper.convertValue(mdmsResponseMap , MdmsResponseV2.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_MDMS, e);
        }

        List <Mdms> result = mdmsResponse.getMdms();
        if (result == null || ObjectUtils.isEmpty(result)) {
            log.error(NO_MDMS_DATA_FOUND_FOR_GIVEN_PARAMETERS_MESSAGE + " - " + tenantId);
            throw new CustomException(NO_MDMS_DATA_FOUND_FOR_GIVEN_PARAMETERS, NO_MDMS_DATA_FOUND_FOR_GIVEN_PARAMETERS_MESSAGE);
        }
        log.info(result.toString());
        return result;
    }

    public MdmsCriteriaReqV2 getMdmsRequest(RequestInfo requestInfo, String tenantId, Boolean isProduct, List <String> ids, Integer limit, Integer offset) {

        MdmsCriteriaV2 mdmsCriteria = isProduct ? getProductsMdmsCriteria(tenantId,ids, limit, offset) : getProductVariantsMdmsCriteria(tenantId, ids, limit, offset);

        return MdmsCriteriaReqV2.builder().mdmsCriteria(mdmsCriteria).requestInfo(requestInfo).build();
    }

    private MdmsCriteriaV2 getProductVariantsMdmsCriteria(String tenantId,List <String> ids, Integer limit, Integer offset) {
        final String schemaCode = MDMS_PRODUCT_VARIANT_MODULE_NAME + DOT_SEPARATOR + MDMS_PRODUCT_VARIANT_MASTER_NAME;

        return MdmsCriteriaV2.builder().tenantId(tenantId).schemaCode(schemaCode).uniqueIdentifiers(ids).offset(offset).limit(limit).build();
    }

    private MdmsCriteriaV2 getProductsMdmsCriteria(String tenantId, List <String> ids, Integer limit, Integer offset) {
        final String schemaCode = MDMS_PRODUCT_MODULE_NAME + DOT_SEPARATOR + MDMS_PRODUCT_MASTER_NAME;

        return MdmsCriteriaV2.builder().tenantId(tenantId).schemaCode(schemaCode).uniqueIdentifiers(ids).limit(limit).offset(offset).build();
    }
}
