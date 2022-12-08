package org.egov.common.service;

import com.jayway.jsonpath.JsonPath;
import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnExpression("!'${egov.mdms.integration.enabled}'.isEmpty() && ${egov.mdms.integration.enabled:false} && !'${egov.mdms.host}'.isEmpty() && !'${egov.mdms.master.name}'.isEmpty()  && !'${egov.mdms.search.endpoint}'.isEmpty()")
public class MdmsService {

    private final ServiceRequestClient restRepo;
    private final String mdmsHost;
    private final String mdmsUrl;
    private String masterName;
    private String moduleName;

    @Autowired
    public MdmsService(ServiceRequestClient restRepo,
                    @Value("${egov.mdms.host}") String mdmsHost,
                    @Value("${egov.mdms.search.endpoint}") String mdmsUrl,
                    @Value("${egov.mdms.master.name}") String masterName,
                    @Value("${egov.mdms.module.name}") String moduleName) {
        this.restRepo = restRepo;
        this.mdmsHost = mdmsHost;
        this.mdmsUrl = mdmsUrl;
        this.masterName = masterName;
        this.moduleName = moduleName;
    }

    public Integer fetchFromMdms(RequestInfo requestInfo, String tenantId) {
        StringBuilder uri = new StringBuilder();
        uri.append(mdmsHost).append(mdmsUrl);
        MdmsCriteriaReq mdmsCriteriaReq = getMdmsRequest(requestInfo, tenantId);
        Object response;
        Integer rate = 0;
        try {
            response = restRepo.fetchResult(uri, mdmsCriteriaReq, Map.class);
            rate = JsonPath.read(response, "$.MdmsRes.VTR.RegistrationCharges.[0].amount");
        }catch(Exception e) {
            log.error("Exception occurred while fetching category lists from mdms: ",e);
        }
        return rate;
    }

    private MdmsCriteriaReq getMdmsRequest(RequestInfo requestInfo, String tenantId) {
        MasterDetail masterDetail = new MasterDetail();
        masterDetail.setName(masterName);
        List<MasterDetail> masterDetailList = new ArrayList<>();
        masterDetailList.add(masterDetail);
        ModuleDetail moduleDetail = new ModuleDetail();
        moduleDetail.setMasterDetails(masterDetailList);
        moduleDetail.setModuleName(moduleName);
        List<ModuleDetail> moduleDetailList = new ArrayList<>();
        moduleDetailList.add(moduleDetail);
        MdmsCriteria mdmsCriteria = new MdmsCriteria();
        mdmsCriteria.setTenantId(tenantId.split("\\.")[0]);
        mdmsCriteria.setModuleDetails(moduleDetailList);
        MdmsCriteriaReq mdmsCriteriaReq = new MdmsCriteriaReq();
        mdmsCriteriaReq.setMdmsCriteria(mdmsCriteria);
        mdmsCriteriaReq.setRequestInfo(requestInfo);
        return mdmsCriteriaReq;
    }
}
