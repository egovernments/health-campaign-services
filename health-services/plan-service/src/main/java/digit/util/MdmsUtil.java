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

        ModuleDetail tenantModuleDetail = getTenantModuleRequestData();
        ModuleDetail assumptionModuleDetail = getAssumptionModuleDetail();

        List<ModuleDetail> moduleDetails = new LinkedList<>();
        moduleDetails.add(tenantModuleDetail);
        moduleDetails.add(assumptionModuleDetail);

        MdmsCriteria mdmsCriteria = MdmsCriteria.builder().moduleDetails(moduleDetails).tenantId(tenantId).build();

        return MdmsCriteriaReq.builder().mdmsCriteria(mdmsCriteria).requestInfo(requestInfo).build();
    }

    private ModuleDetail getTenantModuleRequestData() {
        List<MasterDetail> tenantMasterDetails = new ArrayList<>();

        MasterDetail tenantMasterDetail = MasterDetail.builder().name(MDSM_MASTER_TENANTS).filter(FILTER_CODE).build();

        tenantMasterDetails.add(tenantMasterDetail);

        return ModuleDetail.builder().masterDetails(tenantMasterDetails).moduleName(MDMS_TENANT_MODULE_NAME).build();
    }

    private ModuleDetail getAssumptionModuleDetail() {
        List<MasterDetail> assumptionMasterDetails = new ArrayList<>();

        MasterDetail assumptionMasterDetail = MasterDetail.builder().name(MDMS_MASTER_ASSUMPTION).filter(FILTER_CODE).build();

        assumptionMasterDetails.add(assumptionMasterDetail);

        return ModuleDetail.builder().masterDetails(assumptionMasterDetails).moduleName(MDMS_PLAN_ASSUMPTION_MODULE_NAME).build();
    }

}