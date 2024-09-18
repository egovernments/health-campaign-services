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

    private RequestInfoWrapper requestInfoWrapper;

    public HrmsUtil(RestTemplate restTemplate, Configuration configs, RequestInfoWrapper requestInfoWrapper)
    {
        this.restTemplate = restTemplate;
        this.configs = configs;
        this.requestInfoWrapper = requestInfoWrapper;
    }

    public List<Employee> fetchHrmsData(RequestInfo requestInfo, String employeeId, String tenantId) {

        StringBuilder uri = new StringBuilder();
        uri.append(configs.getHrmsHost()).append(configs.getHrmsEndPoint()).append("?limit={limit}&tenantId={tenantId}&offset={offset}&ids={employeeId}");

        Map<String, String> uriParameters = new HashMap<>();
        uriParameters.put("limit", configs.getDefaultLimit().toString());
        uriParameters.put("tenantId", tenantId);
        uriParameters.put("offset", configs.getDefaultOffset().toString());
        uriParameters.put("employeeId", employeeId);

        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
        EmployeeResponse employeeResponse = new EmployeeResponse();

        try {
            employeeResponse  = restTemplate.postForObject(uri.toString(), requestInfoWrapper, EmployeeResponse.class, uriParameters);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_DATA_FROM_HRMS, e);
        }

        return employeeResponse.getEmployees();
    }


}
