package org.egov.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.project.*;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.repository.RegisterRepository;

import org.egov.service.AttendanceRegisterService;
import org.egov.service.AttendeeService;
import org.egov.service.StaffService;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.*;

import org.egov.web.models.Hrms.Employee;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    @Autowired
    private HRMSUtil hrmsUtil;

    @Autowired
    private AttendeeService attendeeService;


    public void createRegistryForSupervisor(ProjectStaff projectStaff, RequestInfo requestInfo, Individual individual)
    {
        String tenantId = projectStaff.getTenantId();

        log.info("Match Found for Supervisor Role");
        Project projectsearch = Project.builder().id(projectStaff.getProjectId()).tenantId(tenantId).build();
        List<Project> projectList = getProject(tenantId,projectsearch,requestInfo);
        if(projectList.isEmpty())
            throw new CustomException("INVALID_PROJECT_ID","No Project found for the given project ID - "+projectStaff.getProjectId());

        Project project = projectList.get(0);

        AttendanceRegister attendanceRegister = AttendanceRegister.builder().tenantId(tenantId)
                .name(project.getName())
                .referenceId(projectStaff.getProjectId())
                .serviceCode(String.valueOf(UUID.randomUUID()))
                .startDate(BigDecimal.valueOf(project.getStartDate()))
                .endDate(BigDecimal.valueOf(project.getEndDate()))
                .build();
        AttendanceRegisterRequest request = AttendanceRegisterRequest.builder().attendanceRegister(Collections.singletonList(attendanceRegister)).requestInfo(requestInfo).build();
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
        log.info("Staff Successfully enrolled with Staff Id - "+ individual.getId());

    }

    public void enrollAttendeetoRegister(ProjectStaff projectStaff, RequestInfo requestInfo, Individual individual)
    {
        log.info("Match Found for Attendee Role");
        String tenantId = projectStaff.getTenantId();

        List<Employee> employeeList = hrmsUtil.getEmployee(tenantId,Collections.singletonList(individual.getId()),requestInfo);
        if(employeeList.isEmpty())
            throw new CustomException("NO_EMPLOYEE_FOUND","No Employee found for Individual Id - "+individual.getId());

        String reportingToUuid;
        if(!employeeList.get(0).getAssignments().isEmpty())
            reportingToUuid = employeeList.get(0).getAssignments().get(0).getReportingTo();
        else
            throw new CustomException("INDIVIDUAL_REPORTING_TO_INVALID","Did not find reportingTo Uuid in HRMS Employee object");

        IndividualSearch individualSearch = IndividualSearch.builder().userUuid(reportingToUuid).build();
        List<Individual> individualList = individualServiceUtil.getIndividualDetailsFromSearchCriteria(individualSearch,requestInfo, tenantId);

        if(individualList.isEmpty())
            throw new CustomException("INVALID_STAFF_ID","No Individual found for the reportingTo Uuid - "+reportingToUuid);

        Individual reportingToIndividual=individualList.get(0);

        AttendanceRegisterSearchCriteria searchCriteria = AttendanceRegisterSearchCriteria.builder().staffId(reportingToIndividual.getId()).referenceId(projectStaff.getProjectId()).build();
        List<AttendanceRegister> attendanceRegisters = registerRepository.getRegister(searchCriteria);

        if(attendanceRegisters.isEmpty())
            throw new CustomException("NO_REGISTER_FOUND","No Register found with project Id - "+projectStaff.getProjectId() + "and Staff Id - "+ reportingToIndividual.getId());

        String registerId = attendanceRegisters.get(0).getId();

        IndividualEntry individualEntry = IndividualEntry.builder().individualId(individual.getId()).registerId(registerId).tenantId(projectStaff.getTenantId()).build();
        AttendeeCreateRequest attendeeCreateRequest = AttendeeCreateRequest.builder().attendees(Collections.singletonList(individualEntry)).requestInfo(requestInfo).build();
        AttendeeCreateRequest enrichedAttendeeCreateRequest = attendeeService.createAttendee(attendeeCreateRequest);

        if(enrichedAttendeeCreateRequest.getAttendees().isEmpty())
            throw new CustomException("UNABLE_TO_ENROLL_ATTENDEE","Unable to enroll attendee with the given criteria");

        log.info("Successfully created Attendee with Attendee Id - "+individual.getId());
    }

    /**
     * Gets the Employee for the given list of uuids and tenantId of employees
     * @param tenantId
     * @param project
     * @param requestInfo
     * @return
     */
    public List<Project> getProject(String tenantId, Project project, RequestInfo requestInfo){

        StringBuilder url = getProjectURL(tenantId);
        ProjectRequest projectRequest = ProjectRequest.builder().projects(Collections.singletonList(project)).requestInfo(requestInfo).build();
        ProjectResponse projectResponse = serviceRequestClient.fetchResult(url,projectRequest,ProjectResponse.class);

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
        builder.append(config.getProjectSearchEndpoint()).append(LIMIT_OFFSET);
        builder.append("&tenantId=").append(tenantId);
        return builder;
    }

}
