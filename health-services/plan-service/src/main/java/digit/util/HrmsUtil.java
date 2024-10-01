package digit.util;


import digit.config.Configuration;
import digit.web.models.hrms.*;
import digit.web.models.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


import java.util.*;

import static digit.config.ServiceConstants.*;

@Slf4j
@Component
public class HrmsUtil {

    private RestTemplate restTemplate;

    private Configuration configs;


    public HrmsUtil(RestTemplate restTemplate, Configuration configs) {
        this.restTemplate = restTemplate;
        this.configs = configs;
    }

    /**
     * This method fetches data from HRMS service for provided employeeId
     *
     * @param employeeId  employee id provided in the request
     * @param requestInfo request info from the request
     * @param tenantId    tenant id from the request
     */
    public EmployeeResponse fetchHrmsData(RequestInfo requestInfo, String employeeId, String tenantId) {

        // Create HRMS uri
        Map<String, String> uriParameters = new HashMap<>();
        StringBuilder uri = getHrmsUri(uriParameters, tenantId, employeeId);

        // Create request body
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
        EmployeeResponse employeeResponse = new EmployeeResponse();

        try {
            employeeResponse = restTemplate.postForObject(uri.toString(), requestInfoWrapper, EmployeeResponse.class, uriParameters);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_DATA_FROM_HRMS, e);
        }

        return employeeResponse;
    }

    /**
     * This method creates HRMS uri with query parameters
     *
     * @param uriParameters map that stores values corresponding to the placeholder in uri
     * @param tenantId      the tenant id from plan employee assignment request
     * @param employeeId    the employee id from plan employee assignment request
     * @return a complete HRMS uri
     */
    private StringBuilder getHrmsUri(Map<String, String> uriParameters, String tenantId, String employeeId) {
        StringBuilder uri = new StringBuilder();
        uri.append(configs.getHrmsHost()).append(configs.getHrmsEndPoint()).append("?limit={limit}&tenantId={tenantId}&offset={offset}&ids={employeeId}");

        uriParameters.put("limit", configs.getDefaultLimit().toString());
        uriParameters.put("tenantId", tenantId);
        uriParameters.put("offset", configs.getDefaultOffset().toString());
        uriParameters.put("employeeId", employeeId);

        return uri;
    }


}
