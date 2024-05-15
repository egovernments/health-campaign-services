package org.egov.project.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import digit.models.coremodels.mdms.MasterDetail;
import digit.models.coremodels.mdms.MdmsCriteria;
import digit.models.coremodels.mdms.MdmsCriteriaReq;
import digit.models.coremodels.mdms.ModuleDetail;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectRequest;
import org.egov.project.config.ProjectConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.egov.project.util.ProjectConstants.MASTER_ATTENDANCE_SESSION;
import static org.egov.project.util.ProjectConstants.MASTER_DEPARTMENT;
import static org.egov.project.util.ProjectConstants.MASTER_NATUREOFWORK;
import static org.egov.project.util.ProjectConstants.MASTER_PROJECTTYPE;
import static org.egov.project.util.ProjectConstants.MASTER_TENANTS;
import static org.egov.project.util.ProjectConstants.MDMS_COMMON_MASTERS_MODULE_NAME;
import static org.egov.project.util.ProjectConstants.MDMS_HCM_ATTENDANCE_MODULE_NAME;
import static org.egov.project.util.ProjectConstants.MDMS_TENANT_MODULE_NAME;

@Component
@Slf4j
public class MDMSUtils {

    @Autowired
    private ServiceRequestClient serviceRequestRepository;

    @Autowired
    private ProjectConfiguration config;

    public static final String filterCode = "$.*.code";

    public static final String filterActiveTrue = "$.[?(@.active==true)]";

    public Object mDMSCall(ProjectRequest request, String tenantId) {
        RequestInfo requestInfo = request.getRequestInfo();
        MdmsCriteriaReq mdmsCriteriaReq = getMDMSRequest(requestInfo, tenantId, request);
        Object result = null;
        try {
            result = serviceRequestRepository.fetchResult(getMdmsSearchUrl(), mdmsCriteriaReq, LinkedHashMap.class);
        } catch (Exception e) {
            log.error("error while calling mdms", e);
            throw new CustomException("MDMS_ERROR", "error while calling mdms");
        }
        return result;
    }

    public MdmsCriteriaReq getMDMSRequest(RequestInfo requestInfo, String tenantId, ProjectRequest request) {

        ModuleDetail projectMDMSModuleDetail = getMDMSModuleRequestData(request);
        ModuleDetail projectDepartmentModuleDetail = getDepartmentModuleRequestData(request);
        ModuleDetail projectTenantModuleDetail = getTenantModuleRequestData(request);
        ModuleDetail attendanceModuleDetail = getAttendanceModuleRequestData(request);

        List<ModuleDetail> moduleDetails = new LinkedList<>();
        moduleDetails.add(projectMDMSModuleDetail);
        moduleDetails.add(projectDepartmentModuleDetail);
        moduleDetails.add(projectTenantModuleDetail);
        moduleDetails.add(attendanceModuleDetail);

        MdmsCriteria mdmsCriteria = MdmsCriteria.builder().moduleDetails(moduleDetails).tenantId(tenantId)
                .build();

        MdmsCriteriaReq mdmsCriteriaReq = MdmsCriteriaReq.builder().mdmsCriteria(mdmsCriteria)
                .requestInfo(requestInfo).build();
        return mdmsCriteriaReq;
    }

    private ModuleDetail getMDMSModuleRequestData(ProjectRequest request) {
        List<MasterDetail> projectMDMSMasterDetails = new ArrayList<>();

        MasterDetail projectTypeMasterDetails = MasterDetail.builder().name(MASTER_PROJECTTYPE)
                .filter(filterActiveTrue)
                .build();
        MasterDetail natureOfWorkMasterDetails = MasterDetail.builder().name(MASTER_NATUREOFWORK)
                .filter(filterActiveTrue)
                .build();
        projectMDMSMasterDetails.add(projectTypeMasterDetails);
        projectMDMSMasterDetails.add(natureOfWorkMasterDetails);


        ModuleDetail projectMDMSModuleDetail = ModuleDetail.builder().masterDetails(projectMDMSMasterDetails)
                .moduleName(config.getMdmsModule()).build();

        return projectMDMSModuleDetail;
    }

    private ModuleDetail getDepartmentModuleRequestData(ProjectRequest request) {
        List<Project> projects = request.getProjects();
        List<MasterDetail> projectDepartmentMasterDetails = new ArrayList<>();

        MasterDetail departmentMasterDetails = MasterDetail.builder().name(MASTER_DEPARTMENT)
                .filter(filterActiveTrue).build();
        projectDepartmentMasterDetails.add(departmentMasterDetails);

        ModuleDetail projectDepartmentModuleDetail = ModuleDetail.builder().masterDetails(projectDepartmentMasterDetails)
                .moduleName(MDMS_COMMON_MASTERS_MODULE_NAME).build();

        return projectDepartmentModuleDetail;
    }

    public StringBuilder getMdmsSearchUrl() {
        return new StringBuilder().append(config.getMdmsHost()).append(config.getMdmsEndPoint());
    }

    private ModuleDetail getTenantModuleRequestData(ProjectRequest request) {
        List<MasterDetail> tenantMasterDetails = new ArrayList<>();

        MasterDetail tenantMasterDetail = MasterDetail.builder().name(MASTER_TENANTS)
                .filter(filterCode).build();

        tenantMasterDetails.add(tenantMasterDetail);

        ModuleDetail tenantModuleDetail = ModuleDetail.builder().masterDetails(tenantMasterDetails)
                .moduleName(MDMS_TENANT_MODULE_NAME).build();

        return tenantModuleDetail;
    }

    private ModuleDetail getAttendanceModuleRequestData(ProjectRequest request) {
        List<MasterDetail> attendanceMasterDetails = new ArrayList<>();

        MasterDetail attendanceSessionsMasterDetails = MasterDetail.builder().name(MASTER_ATTENDANCE_SESSION)
                .filter(filterCode)
                .build();

        attendanceMasterDetails.add(attendanceSessionsMasterDetails);

        ModuleDetail attendanceModuleDetail = ModuleDetail.builder().masterDetails(attendanceMasterDetails)
                .moduleName(MDMS_HCM_ATTENDANCE_MODULE_NAME).build();

        return attendanceModuleDetail;
    }

}