package org.egov.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.core.Role;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.project.*;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.repository.RegisterRepository;

import org.egov.service.AttendanceRegisterService;
import org.egov.service.StaffService;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static org.egov.util.AttendanceServiceConstants.LIMIT_OFFSET;

@Component
@Slf4j
public class ProjectStaffUtil {

    @Autowired
    private AttendanceServiceConfiguration config;

    @Autowired
    private RegisterRepository registerRepository;

    @Autowired
    private ServiceRequestClient serviceRequestClient;

    @Autowired
    private IndividualServiceUtil individualServiceUtil;

    @Autowired
    private AttendanceRegisterService attendanceRegisterService;

    @Autowired
    private StaffService staffService;


    public void createRegistryForSupervisor(ProjectStaffBulkRequest projectStaffBulkRequest)
    {
        RequestInfo requestInfo = projectStaffBulkRequest.getRequestInfo();

        for(ProjectStaff projectStaff:projectStaffBulkRequest.getProjectStaff())
        {
            String staffUserUuid = projectStaff.getUserId();
            String tenantId = projectStaff.getTenantId();

            IndividualSearch individualSearch = IndividualSearch.builder().userUuid(staffUserUuid).build();
            List<Individual> individualList = individualServiceUtil.getIndividualDetailsFromSearchCriteria(individualSearch,requestInfo, tenantId);

            if(individualList.isEmpty())
                throw new CustomException("INVALID_STAFF_ID","No Individual found for the given staff ID - "+staffUserUuid);
            Individual individual=individualList.get(0);

            List<Role> roleList = individual.getUserDetails().getRoles();
            List<String> roleCodeList = roleList.stream()
                    .map(Role::getCode)
                    .collect(Collectors.toList());

            boolean matchFound = roleCodeList.stream()
                    .anyMatch(config.getProjectSupervisorRoles()::contains);

            // If match found, call create() and break
            if (matchFound) {
                ProjectSearch projectSearch = ProjectSearch.builder().id(projectStaff.getProjectId()).tenantId(tenantId).build();
                List<Project> projectList = getProject(tenantId,projectSearch,requestInfo);
                if(projectList.isEmpty())
                    throw new CustomException("INVALID_PROJECT_ID","No Project found for the given project ID - "+projectStaff.getProjectId());

                Project project = projectList.get(0);

                AttendanceRegister attendanceRegister = AttendanceRegister.builder().tenantId(tenantId)
                        .name("name")
                        .referenceId(projectStaff.getProjectId())
                        .serviceCode(String.valueOf(UUID.randomUUID()))
                        .startDate(BigDecimal.valueOf(project.getStartDate()))
                        .endDate(BigDecimal.valueOf(project.getEndDate()))
                        .build();
                AttendanceRegisterRequest request = AttendanceRegisterRequest.builder().attendanceRegister(Collections.singletonList(attendanceRegister)).build();
                AttendanceRegisterRequest enrichedAttendanceRegisterRequest = attendanceRegisterService.createAttendanceRegister(request);

                //enroll Staff
                if(enrichedAttendanceRegisterRequest.getAttendanceRegister().isEmpty())
                    throw new CustomException("UNABLE_TO_CREATE_REGISTER","Unable to create Register with Project ID - "+projectStaff.getProjectId());

                String attendanceRegisterId = enrichedAttendanceRegisterRequest.getAttendanceRegister().get(0).getId();
                StaffPermission staffPermission = StaffPermission.builder().registerId(attendanceRegisterId).userId(individual.getId()).tenantId(tenantId).build();
                StaffPermissionRequest staffPermissionRequest = StaffPermissionRequest.builder().staff(Collections.singletonList(staffPermission)).requestInfo(requestInfo).build();

                StaffPermissionRequest enrichedRequest = staffService.createAttendanceStaff(staffPermissionRequest,false);
                if(enrichedRequest.getStaff().isEmpty())
                    throw new CustomException("UNABLE_TO_ENROLL_FIRST_STAFF","Unable to enroll first staff with Staff ID - "+individual.getId());

            }

        }
    }

    /**
     * Gets the Employee for the given list of uuids and tenantId of employees
     * @param tenantId
     * @param projectSearch
     * @param requestInfo
     * @return
     */
    public List<Project> getProject(String tenantId, ProjectSearch projectSearch, RequestInfo requestInfo){

        StringBuilder url = getProjectURL(tenantId);
        ProjectSearchRequest projectSearchRequest = ProjectSearchRequest.builder().project(projectSearch).requestInfo(requestInfo).build();
        ProjectResponse projectResponse = serviceRequestClient.fetchResult(url,projectSearchRequest,ProjectResponse.class);

        return projectResponse.getProject();

    }

    /**
     * Gets the Employee for the given list of uuids and tenantId of employees
     * @param tenantId
     * @param projectStaffSearch
     * @param requestInfo
     * @return
     */
    public List<ProjectStaff> getProjectStaff(String tenantId, ProjectStaffSearch projectStaffSearch, RequestInfo requestInfo){

        StringBuilder url = getProjectStaffURL(tenantId);
        ProjectStaffSearchRequest projectStaffSearchRequest = ProjectStaffSearchRequest.builder().projectStaff(projectStaffSearch).requestInfo(requestInfo).build();
//        ProjectStaffSearchRequest projectStaffSearchRequest = ProjectStaffSearchRequest.builder().projectStaff(projectStaffSearch).requestInfo(requestInfo).build();
        ProjectStaffBulkResponse projectStaffBulkResponse = serviceRequestClient.fetchResult(url, projectStaffSearchRequest, ProjectStaffBulkResponse.class);

        return projectStaffBulkResponse.getProjectStaff();

    }

    /**
     * Gets the Employee for the given list of uuids and tenantId of employees
     * @param tenantId
     * @param registerIds
     * @param requestInfo
     * @return
     */
    public Map<String, String>  getregisterIdVsProjectIdMap(String tenantId, List<String> registerIds, RequestInfo requestInfo){

        AttendanceRegisterSearchCriteria searchCriteria = AttendanceRegisterSearchCriteria.builder().ids(registerIds).build();
        List<AttendanceRegister> attendanceRegisters = registerRepository.getRegister(searchCriteria);
        Map<String, String> registerIdVsProjectId = attendanceRegisters.stream()
                .collect(Collectors.toMap(AttendanceRegister::getId, AttendanceRegister::getReferenceId));

        return registerIdVsProjectId;
    }


    /**
     * Builds Project Staff search URL
     * @param tenantId
     * @return URL
     */
    public StringBuilder getProjectStaffURL(String tenantId)
    {
        StringBuilder builder = new StringBuilder(config.getProjectHost());
        builder.append(config.getProjectStaffSearchEndpoint()).append(LIMIT_OFFSET);
        builder.append("&tenantId=").append(tenantId);
        return builder;
    }

    /**
     * Builds Project search URL
     * @param tenantId
     * @return URL
     */
    public StringBuilder getProjectURL(String tenantId)
    {
        StringBuilder builder = new StringBuilder(config.getProjectHost());
        builder.append(config.getProjectStaffSearchEndpoint()).append(LIMIT_OFFSET);
        builder.append("&tenantId=").append(tenantId);
        return builder;
    }

}
