package digit.util;

import digit.config.Configuration;
import digit.models.coremodels.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.workflow.BusinessService;
import org.egov.common.contract.workflow.BusinessServiceResponse;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static digit.config.ServiceConstants.*;

@Slf4j
@Component
public class BusinessServiceUtil {

    private RestTemplate restTemplate;

    private Configuration config;

    public BusinessServiceUtil(RestTemplate restTemplate, Configuration configs) {
        this.restTemplate = restTemplate;
        this.config = configs;
    }

    /**
     * This method fetches business service details for the given tenant id and business service.
     *
     * @param requestInfo     the request info from request.
     * @param businessService businessService whose details are to be searched.
     * @param tenantId        tenantId from request.
     * @return returns the business service response for the given tenant id and business service.
     */
    public BusinessService fetchBusinessService(RequestInfo requestInfo, String businessService, String tenantId) {

        // Get business service uri
        Map<String, String> uriParameters = new HashMap<>();
        String uri = getBusinessServiceUri(businessService, tenantId, uriParameters);

        // Create request body
        RequestInfoWrapper requestInfoWrapper = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
        BusinessServiceResponse businessServiceResponse = new BusinessServiceResponse();

        try {
            businessServiceResponse = restTemplate.postForObject(uri, requestInfoWrapper, BusinessServiceResponse.class, uriParameters);
        } catch (Exception e) {
            log.error(ERROR_WHILE_FETCHING_BUSINESS_SERVICE_DETAILS, e);
        }

        if (CollectionUtils.isEmpty(businessServiceResponse.getBusinessServices())) {
            throw new CustomException(NO_BUSINESS_SERVICE_DATA_FOUND_CODE, NO_BUSINESS_SERVICE_DATA_FOUND_MESSAGE);
        }

        return businessServiceResponse.getBusinessServices().get(0);
    }

    /**
     * This method creates business service uri with query parameters
     *
     * @param businessService businessService whose details are to be searched.
     * @param tenantId        tenant id from the request.
     * @param uriParameters   map that stores values corresponding to the placeholder in uri
     * @return
     */
    private String getBusinessServiceUri(String businessService, String tenantId, Map<String, String> uriParameters) {

        StringBuilder uri = new StringBuilder();
        uri.append(config.getWfHost()).append(config.getBusinessServiceSearchEndpoint()).append(BUSINESS_SERVICE_QUERY_TEMPLATE);

        uriParameters.put(TENANT_ID, tenantId);
        uriParameters.put(BUSINESS_SERVICE, businessService);

        return uri.toString();
    }
}
