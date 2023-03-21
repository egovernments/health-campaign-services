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
import java.util.Map;
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

    @Value("${egov.location.code.query.param:codes}")
    private String codeQueryParam;

    @Autowired
    private ServiceRequestClient serviceRequestRepository;

    /* Takes map of locations with boundaryType as key and list of boundaries as its values, tenantId, requestInfo and hierarchyType.
     * For each boundaryType, egov-location service is called with hierarchyType, tenantId and codes as parameters. The boundaries in request are validated against the egov-location response*/
    public void validateBoundaryDetails(Map<String, List<String>> locations, String tenantId, RequestInfo requestInfo, String hierarchyTypeCode) {
        for (Map.Entry<String, List<String>> entry : locations.entrySet()) {
            String boundaryType = entry.getKey();
            List<String> boundaries = entry.getValue();

            log.info("Validating boundary for boundary type " + boundaryType + " with hierarchyType " + hierarchyTypeCode);
            StringBuilder uri = new StringBuilder(locationHost);
            uri.append(locationContextPath).append(locationEndpoint);
            uri.append("?").append("tenantId=").append(tenantId);

            if (hierarchyTypeCode != null)
                uri.append("&").append("hierarchyTypeCode=").append(hierarchyTypeCode);

            uri.append("&").append("boundaryType=").append(boundaryType).append("&")
                    .append(String.format("%s=", codeQueryParam))
                    .append(StringUtils.join(boundaries, ','));

            Optional<Object> response = null;
            try {
                response = Optional.ofNullable(serviceRequestRepository.fetchResult(uri,
                        RequestInfoWrapper.builder().requestInfo(requestInfo).build(), LinkedHashMap.class));
            } catch (Exception e) {
                log.error("error while calling boundary service", e);
                throw new CustomException("BOUNDARY_ERROR", "error while calling boundary service");
            }

            if (response.isPresent()) {
                LinkedHashMap responseMap = (LinkedHashMap) response.get();
                if (CollectionUtils.isEmpty(responseMap))
                    throw new CustomException("BOUNDARY ERROR", "The response from location service is empty or null");
                String jsonString = new JSONObject(responseMap).toString();

                for (String boundary : boundaries) {
                    String jsonpath = "$..boundary[?(@.code==\"{}\")]";
                    jsonpath = jsonpath.replace("{}", boundary);
                    DocumentContext context = JsonPath.parse(jsonString);
                    Object boundaryObject = context.read(jsonpath);

                    if (!(boundaryObject instanceof ArrayList) || CollectionUtils.isEmpty((ArrayList) boundaryObject)) {
                        log.error("The boundary data for the code " + boundary + " is not available");
                        throw new CustomException("INVALID_BOUNDARY_DATA", "The boundary data for the code "
                                + boundary + " is not available");
                    }
                }
            }
            log.info("The boundaries " + StringUtils.join(boundaries, ',') + " validated for boundary type " + boundaryType + " with tenantId " + tenantId);
        }
    }

}
