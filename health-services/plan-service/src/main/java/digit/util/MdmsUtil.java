package digit.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;

import java.util.LinkedList;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.mdms.model.*;

import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static digit.config.ServiceConstants.*;

@Slf4j
@Component
public class MdmsUtil {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private Configuration configs;


    public Object fetchMdmsData(RequestInfo requestInfo, String tenantId) {
        StringBuilder uri = new StringBuilder();
        uri.append(configs.getMdmsHost()).append(configs.getMdmsEndPoint());
        MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId);
        Object mdmsResponseMap = new HashMap<>();
        MdmsResponse mdmsResponse = new MdmsResponse();
        try {
            mdmsResponseMap = restTemplate.postForObject(uri.toString(), mdmsCriteriaReq, Map.class);
            mdmsResponse = mapper.convertValue(mdmsResponseMap, MdmsResponse.class);
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

    /**
     * This method constructs the criteria request for MDMS Api call
     *
     * @param requestInfo requestInfo from the provided request
     * @param tenantId    tenant id from the provided request
     * @return Returns the mdms criteria request
     */
    public MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId) {

        ModuleDetail assumptionModuleDetail = getPlanModuleDetail();
        ModuleDetail adminConsoleModuleDetail = getAdminConsoleModuleDetail();

        List<ModuleDetail> moduleDetails = new LinkedList<>();
        moduleDetails.add(assumptionModuleDetail);
        moduleDetails.add(adminConsoleModuleDetail);

        MdmsCriteria mdmsCriteria = MdmsCriteria.builder().moduleDetails(moduleDetails).tenantId(tenantId).build();

        return MdmsCriteriaReq.builder().mdmsCriteria(mdmsCriteria).requestInfo(requestInfo).build();
    }

    private ModuleDetail getAdminConsoleModuleDetail() {
        List<MasterDetail> adminConsoleMasters = new ArrayList<>();

        MasterDetail hierarchyConfigMaster = MasterDetail.builder().name(MDMS_MASTER_HIERARCHY_SCHEMA).build();

        adminConsoleMasters.add(hierarchyConfigMaster);

        return ModuleDetail.builder().masterDetails(adminConsoleMasters).moduleName(MDMS_ADMIN_CONSOLE_MODULE_NAME).build();
    }

    /**
     * This method constructs module detail object for 'hcm-microplanning' module
     *
     * @return Returns the module details for 'hcm-microplanning' module
     */
    private ModuleDetail getPlanModuleDetail() {
        List<MasterDetail> assumptionMasterDetails = new ArrayList<>();

        MasterDetail assumptionMasterDetail = MasterDetail.builder().name(MDMS_MASTER_ASSUMPTION).build();
        MasterDetail uploadConfigMasterDetail = MasterDetail.builder().name(MDMS_MASTER_UPLOAD_CONFIGURATION).build();
        MasterDetail ruleConfigureInputsMasterDetail = MasterDetail.builder().name(MDMS_MASTER_RULE_CONFIGURE_INPUTS).filter(FILTER_DATA).build();
        MasterDetail schemaDetails = MasterDetail.builder().name(MDMS_MASTER_SCHEMAS).build();
        MasterDetail metricDetails = MasterDetail.builder().name(MDMS_MASTER_METRIC).build();
        MasterDetail unitDetails = MasterDetail.builder().name(MDMS_MASTER_UOM).build();
        MasterDetail namingRegexDetails = MasterDetail.builder().name(MDMS_MASTER_NAME_VALIDATION).build();

        assumptionMasterDetails.add(assumptionMasterDetail);
        assumptionMasterDetails.add(uploadConfigMasterDetail);
        assumptionMasterDetails.add(ruleConfigureInputsMasterDetail);
        assumptionMasterDetails.add(schemaDetails);
        assumptionMasterDetails.add(metricDetails);
        assumptionMasterDetails.add(unitDetails);
        assumptionMasterDetails.add(namingRegexDetails);

        return ModuleDetail.builder().masterDetails(assumptionMasterDetails).moduleName(MDMS_PLAN_MODULE_NAME).build();
    }

}