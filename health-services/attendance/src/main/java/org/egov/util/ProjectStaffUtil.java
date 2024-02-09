package org.egov.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.project.*;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.repository.RegisterRepository;
import org.egov.repository.ServiceRequestRepository;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.AttendanceRegister;
import org.egov.web.models.AttendanceRegisterSearchCriteria;
import org.egov.web.models.Hrms.Employee;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.util.AttendanceServiceConstants.LIMIT_OFFSET;

@Component
@Slf4j
public class ProjectStaffUtil {

    @Autowired
    private AttendanceServiceConfiguration config;

    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    @Qualifier("objectMapper")
    private ObjectMapper mapper;

    @Autowired
    private RegisterRepository registerRepository;


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
        Object res = serviceRequestRepository.fetchResult(url, projectStaffSearchRequest);

        ProjectStaffBulkResponse projectStaffBulkResponse = null;

        try {
              projectStaffBulkResponse = mapper.convertValue(res, ProjectStaffBulkResponse.class);
        }
        catch (Exception e){
            throw new CustomException("PARSING_ERROR","Failed to parse Project Staff response");
        }
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

}
