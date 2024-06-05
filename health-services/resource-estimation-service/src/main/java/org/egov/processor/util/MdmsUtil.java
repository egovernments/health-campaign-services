package org.egov.processor.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.mdms.model.*;

import org.egov.processor.config.Configuration;
import org.egov.processor.web.models.File;
import org.egov.tracer.model.CustomException;
import org.flywaydb.core.internal.util.JsonUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import static org.egov.processor.config.ServiceConstants.*;
import static org.flywaydb.core.internal.util.JsonUtils.parseJson;

@Slf4j
@Component
public class MdmsUtil {

    private RestTemplate restTemplate;

    private ObjectMapper mapper;

    private Configuration configs;

    public MdmsUtil(RestTemplate restTemplate, ObjectMapper mapper, Configuration configs) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
        this.configs = configs;
    }

    public Object fetchMdmsData(RequestInfo requestInfo, String tenantId) {
        StringBuilder uri = new StringBuilder();
        uri.append(configs.getMdmsHost()).append(configs.getMdmsEndPoint());
        MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId);
        Object response = new HashMap<>();
        MdmsResponse mdmsResponse = new MdmsResponse();
        try {
            response = restTemplate.postForObject(uri.toString(), mdmsCriteriaReq, Map.class);
            mdmsResponse = mapper.convertValue(response, MdmsResponse.class);
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

    public MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId) {

        ModuleDetail assumptionModuleDetail = getPlanModuleDetail();

        List<ModuleDetail> moduleDetails = new LinkedList<>();
        moduleDetails.add(assumptionModuleDetail);

        MdmsCriteria mdmsCriteria = MdmsCriteria.builder().moduleDetails(moduleDetails).tenantId(tenantId).build();

        return MdmsCriteriaReq.builder().mdmsCriteria(mdmsCriteria).requestInfo(requestInfo).build();
    }

    private ModuleDetail getPlanModuleDetail() {
        List<MasterDetail> assumptionMasterDetails = new ArrayList<>();
        MasterDetail schemaDetails = MasterDetail.builder().name(MDMS_MASTER_SCHEMAS).build();
        assumptionMasterDetails.add(schemaDetails);

        return ModuleDetail.builder().masterDetails(assumptionMasterDetails).moduleName(MDMS_PLAN_MODULE_NAME).build();
    }

    public static List<Map<String, Object>> filterMasterData(String masterDataJson, File.InputFileTypeEnum fileType, String templateIdentifier) {
        List<Map<String, Object>> filteredData = new ArrayList<>();
        Map<String, Object> masterData = JsonUtils.parseJson(masterDataJson, Map.class);

        List<Map<String, Object>> schemas = (List<Map<String, Object>>) masterData.get("Schemas");

        for (Map<String, Object> schema : schemas) {
            if (schema.get(MDMS_SCHEMA_TYPE).equals(fileType) && schema.get(MDMS_SCHEMA_SECTION).equals("Population")) {
                Map<String, Object> schemaProperties = (Map<String, Object>) schema.get("schema");
                Map<String, Object> properties = (Map<String, Object>) schemaProperties.get("Properties");

                filteredData.add(properties);
            }
        }

        return filteredData;
    }



}