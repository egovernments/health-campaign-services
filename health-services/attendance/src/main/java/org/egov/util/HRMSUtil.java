package org.egov.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.repository.ServiceRequestRepository;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.Hrms.Employee;
import org.egov.web.models.Hrms.EmployeeResponse;
import org.egov.web.models.RequestInfoWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HRMSUtil {


    @Autowired
    private ServiceRequestRepository serviceRequestRepository;

    @Autowired
    private AttendanceServiceConfiguration config;

    @Autowired
    private ServiceRequestClient serviceRequestClient;

    @Autowired
    @Qualifier("objectMapper")
    private ObjectMapper mapper;

    private String HRMS_EMPLOYEE_JSONPATH = "$.Employees.*";
    /**
     * Gets the Employee for the given list of uuids and tenantId of employees
     * @param tenantId
     * @param uuids
     * @param requestInfo
     * @return
     */
    public List<Employee> getEmployee(String tenantId, List<String> uuids, RequestInfo requestInfo){

        StringBuilder url = getHRMSURI(tenantId, uuids);

        EmployeeResponse employeeResponse = serviceRequestClient.fetchResult(url, RequestInfoWrapper.builder().requestInfo(requestInfo).build(), EmployeeResponse.class);
        List<Employee> employeeList = employeeResponse.getEmployees();
        if(employeeList.isEmpty())
            throw new CustomException("NO_EMPLOYEE_FOUND_FOR_INDIVIDUALID","The Employees with uuid: "+uuids.toString()+" is not found");

        return employeeList;

    }

    /**
     * Builds HRMS search URL
     * @param uuids
     * @param tenantId
     * @return URL
     */

    public StringBuilder getHRMSURI(String tenantId, List<String> uuids){

        StringBuilder builder = new StringBuilder(config.getHrmsHost());
        builder.append(config.getHrmsEndPoint());
        builder.append("?tenantId=").append(tenantId);
        builder.append("&uuids=");
        builder.append(StringUtils.join(uuids, ","));

        return builder;
    }

}
