package org.egov.project.util;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import digit.models.coremodels.RequestInfoWrapper;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;


@Component
@Slf4j
public class BoundaryUtil {

    @Value("${egov.location.host}")
    private String locationHost;

    @Value("${egov.location.context.path}")
    private String locationContextPath;

    @Value("${egov.location.endpoint}")
    private String locationEndpoint;

    @Autowired
    private ServiceRequestClient serviceRequestRepository;

    public void validateBoundaryDetails(List<String> locations, String tenantId, RequestInfo requestInfo, String hierarchyTypeCode) {
        StringBuilder uri = new StringBuilder(locationHost);
        uri.append(locationContextPath).append(locationEndpoint);
        uri.append("?").append("tenantId=").append(tenantId);

        if (hierarchyTypeCode != null)
            uri.append("&").append("hierarchyTypeCode=").append(hierarchyTypeCode);

        uri.append("&").append("boundaryType=").append("Locality").append("&").append("code=")
                .append(StringUtils.join(locations, ','));

        Optional<LinkedHashMap> response = null;
        try {
            response = Optional.ofNullable(serviceRequestRepository.fetchResult(uri,
                    RequestInfoWrapper.builder().requestInfo(requestInfo).build(), LinkedHashMap.class));
        } catch (Exception e) {
            log.error("error while calling boundary service", e);
            throw new CustomException("BOUNDARY_ERROR", "error while calling boundary service");
        }

        if (response.isPresent()) {
            LinkedHashMap responseMap = response.get();
            if (CollectionUtils.isEmpty(responseMap))
                throw new CustomException("BOUNDARY ERROR", "The response from location service is empty or null");
            String jsonString = new JSONObject(responseMap).toString();

            for (String location: locations) {
                String jsonpath = "$..boundary[?(@.code==\"{}\")]";
                jsonpath = jsonpath.replace("{}", location);
                DocumentContext context = JsonPath.parse(jsonString);
                Object boundaryObject = context.read(jsonpath);

                if (!(boundaryObject instanceof ArrayList) || CollectionUtils.isEmpty((ArrayList) boundaryObject)) {
                    throw new CustomException("INVALID_BOUNDARY_DATA", "The boundary data for the code "
                            + location + " is not available");
                }
            }
        }
    }

}
