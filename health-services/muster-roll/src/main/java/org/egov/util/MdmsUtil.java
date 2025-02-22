package org.egov.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.config.MusterRollServiceConfiguration;
import org.egov.mdms.model.MasterDetail;
import org.egov.mdms.model.MdmsCriteria;
import org.egov.mdms.model.MdmsCriteriaReq;
import org.egov.mdms.model.ModuleDetail;
import org.egov.repository.ServiceRequestRepository;
import org.egov.web.models.MusterRollRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.egov.util.MusterRollServiceConstants.*;

@Slf4j
@Component
public class MdmsUtil {

    private final MusterRollServiceConfiguration config;

    private final ServiceRequestRepository serviceRequestRepository;

    @Autowired
    public MdmsUtil(MusterRollServiceConfiguration config, ServiceRequestRepository serviceRequestRepository) {
        this.config = config;
        this.serviceRequestRepository = serviceRequestRepository;
    }

    /**
     * Calls MDMS service to fetch works master data
     *
     * @param request
     * @param tenantId
     * @return
     */
    public Object mDMSCall(MusterRollRequest request, String tenantId) {
        RequestInfo requestInfo = request.getRequestInfo();
        MdmsCriteriaReq mdmsCriteriaReq = getMDMSRequest(requestInfo, tenantId);
        return serviceRequestRepository.fetchResult(getMdmsV2SearchUrl(), mdmsCriteriaReq);
    }

    /**
     * Calls MDMS service to fetch muster roll data
     *
     * @param request
     * @param tenantId
     * @return
     */
    public Object mDMSCallMuster(MusterRollRequest request, String tenantId) {
        RequestInfo requestInfo = request.getRequestInfo();
        MdmsCriteriaReq mdmsCriteriaReq = getMDMSRequestMuster(requestInfo, tenantId);
        return serviceRequestRepository.fetchResult(getMdmsV2SearchUrl(), mdmsCriteriaReq);
    }

    public Object mDMSV2CallMuster(MusterRollRequest request, String tenantId) {
        RequestInfo requestInfo = request.getRequestInfo();
        MdmsCriteriaReq mdmsCriteriaReq = getMDMSRequestMusterV2(requestInfo, tenantId);
        return serviceRequestRepository.fetchResult(getMdmsV2SearchUrl(), mdmsCriteriaReq);
    }

    public MdmsCriteriaReq getMDMSRequestMusterV2(RequestInfo requestInfo, String tenantId) {
        ModuleDetail musterRollModuleDetail = getMusterRollModuleRequestDataV2();

        List<ModuleDetail> moduleDetails = new LinkedList<>();
        moduleDetails.add(musterRollModuleDetail);

        MdmsCriteria mdmsCriteria = MdmsCriteria.builder().moduleDetails(moduleDetails).tenantId(tenantId)
                .build();
        return MdmsCriteriaReq.builder().mdmsCriteria(mdmsCriteria)
                .requestInfo(requestInfo).build();
    }

    /**
     * Returns mdms search criteria based on the tenantId
     *
     * @param requestInfo
     * @param tenantId
     * @param request
     * @return
     */
    public MdmsCriteriaReq getMDMSRequest(RequestInfo requestInfo, String tenantId) {

        ModuleDetail tenantModuleDetail = getTenantModuleRequestData();

        List<ModuleDetail> moduleDetails = new LinkedList<>();
        moduleDetails.add(tenantModuleDetail);

        MdmsCriteria mdmsCriteria = MdmsCriteria.builder().moduleDetails(moduleDetails).tenantId(tenantId)
                .build();

        return MdmsCriteriaReq.builder().mdmsCriteria(mdmsCriteria)
                .requestInfo(requestInfo).build();
    }

    /**
     * Returns mdms search criteria based on the tenantId
     *
     * @param requestInfo
     * @param tenantId
     * @param request
     * @return
     */
    public MdmsCriteriaReq getMDMSRequestMuster(RequestInfo requestInfo, String tenantId) {

        ModuleDetail musterRollModuleDetail = getMusterRollModuleRequestData();

        List<ModuleDetail> moduleDetails = new LinkedList<>();
        moduleDetails.add(musterRollModuleDetail);

        MdmsCriteria mdmsCriteria = MdmsCriteria.builder().moduleDetails(moduleDetails).tenantId(tenantId)
                .build();

        return MdmsCriteriaReq.builder().mdmsCriteria(mdmsCriteria)
                .requestInfo(requestInfo).build();
    }

    private ModuleDetail getTenantModuleRequestData() {
        List<MasterDetail> musterRollTenantMasterDetails = new ArrayList<>();

        MasterDetail tenantMasterDetails = MasterDetail.builder().name(MASTER_TENANTS)
                .filter(FILTER_CODE).build();

        musterRollTenantMasterDetails.add(tenantMasterDetails);

        return ModuleDetail.builder().masterDetails(musterRollTenantMasterDetails)
                .moduleName(MDMS_TENANT_MODULE_NAME).build();
    }

    private ModuleDetail getMusterRollModuleRequestData() {

        List<MasterDetail> musterRollMasterDetails = new ArrayList<>();

        MasterDetail musterAttendanceMasterDetails = MasterDetail.builder().name(MASTER_MUSTER_ROLL).build();
        musterRollMasterDetails.add(musterAttendanceMasterDetails);

        MasterDetail musterWageSeekerSkillsMasterDetails = MasterDetail.builder().name(MASTER_WAGER_SEEKER_SKILLS).build();
        musterRollMasterDetails.add(musterWageSeekerSkillsMasterDetails);

        return ModuleDetail.builder().masterDetails(musterRollMasterDetails)
                .moduleName(MDMS_COMMON_MASTERS_MODULE_NAME).build();
    }

    private ModuleDetail getMusterRollModuleRequestDataV2() {

        List<MasterDetail> musterRollMasterDetails = new ArrayList<>();
        MasterDetail musterWageSeekerSkillMasterDetails = MasterDetail.builder().name("SOR").filter("[?(@.sorType=='L')]").build();
        musterRollMasterDetails.add(musterWageSeekerSkillMasterDetails);
        return ModuleDetail.builder().masterDetails(musterRollMasterDetails)
                .moduleName("WORKS-SOR").build();
    }

    /**
     * Returns the url for mdms search endpoint
     *
     * @return url for mdms search endpoint
     */
    public StringBuilder getMdmsSearchUrl() {
        return new StringBuilder().append(config.getMdmsHost()).append(config.getMdmsEndPoint());
    }
    public StringBuilder getMdmsV2SearchUrl() {
        return new StringBuilder().append(config.getMdmsV2Host()).append(config.getMdmsV2EndPoint());
    }

}