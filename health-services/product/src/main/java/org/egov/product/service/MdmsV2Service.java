package org.egov.product.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.MdmsResponse;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.product.config.ProductConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class MdmsV2Service {

    public static final String ERROR_WHILE_FETCHING_FROM_MDMS = "Exception occurred while fetching category lists from mdms: ";

    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE = "NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT";
    public static final String NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE = "Invalid or incorrect TenantId. No mdms data found for provided Tenant.";

    public static final String MDMS_PRODUCT_MODULE_NAME = "HCM-Product";
    public static final String MDMS_PRODUCT_MASTER_NAME = "Products";

    public static final String MDMS_PRODUCT_VARIANT_MODULE_NAME = "HCM-Product";
    public static final String MDMS_PRODUCT_VARIANT_MASTER_NAME = "ProductVariants";

    private final RestTemplate restTemplate;

    private final ObjectMapper mapper;

    private final ProductConfiguration configs;

    @Autowired
    public MdmsV2Service(RestTemplate restTemplate, ObjectMapper mapper, ProductConfiguration configs) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.configs = configs;
    }

    public Object fetchMdmsData(RequestInfo requestInfo, String tenantId, Boolean isProduct) {
        StringBuilder uri = new StringBuilder();
        uri.append(configs.getMdmsHost()).append(configs.getMdmsEndPoint());
        MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId, isProduct);
        Object mdmsResponseMap  = new HashMap<>();
        MdmsResponse mdmsResponse = new MdmsResponse();
        try {
            mdmsResponseMap  = restTemplate.postForObject(uri.toString(), mdmsCriteriaReq, Map.class);
            mdmsResponse = mapper.convertValue(mdmsResponseMap , MdmsResponse.class);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_FROM_MDMS, e);
        }

        Object result = mdmsResponse.getMdmsRes();
        if (result == null || ObjectUtils.isEmpty(result)) {
            log.error(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE + " - " + tenantId);
            throw new CustomException(NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_CODE, NO_MDMS_DATA_FOUND_FOR_GIVEN_TENANT_MESSAGE);
        }
        return result;
    }

    public MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId, Boolean isProduct) {

        MdmsCriteria mdmsCriteria = isProduct ? getProductsMdmsCriteria(tenantId) : getProductVariantsMdmsCriteria(tenantId);

        return MdmsCriteriaReq.builder().mdmsCriteria(mdmsCriteria).requestInfo(requestInfo).build();
    }

    private MdmsCriteria getProductVariantsMdmsCriteria(String tenantId) {
        MasterDetail productMaster = MasterDetail.builder().name(MDMS_PRODUCT_VARIANT_MASTER_NAME).build();
        ModuleDetail moduleDetail = ModuleDetail.builder()
                .moduleName(MDMS_PRODUCT_VARIANT_MODULE_NAME)
                .masterDetails(Collections.singletonList(productMaster))
                .build();
        return MdmsCriteria.builder().moduleDetails(Collections.singletonList(moduleDetail)).tenantId(tenantId).build();
    }

    private MdmsCriteria getProductsMdmsCriteria(String tenantId) {
        MasterDetail productMaster = MasterDetail.builder().name(MDMS_PRODUCT_MASTER_NAME).build();
        ModuleDetail moduleDetail = ModuleDetail.builder()
                .moduleName(MDMS_PRODUCT_MODULE_NAME)
                .masterDetails(Collections.singletonList(productMaster))
                .build();
        return MdmsCriteria.builder().moduleDetails(Collections.singletonList(moduleDetail)).tenantId(tenantId).build();
    }




}
