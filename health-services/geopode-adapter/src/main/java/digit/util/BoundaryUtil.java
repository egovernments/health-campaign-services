package digit.util;

import digit.config.Configuration;
import digit.web.models.GeopodeBoundaryRequest;
import digit.web.models.boundaryService.*;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static digit.config.ServiceConstants.*;

@Component
@Slf4j
public class BoundaryUtil {

    private Configuration config;

    private RestTemplate restTemplate;

    public BoundaryUtil(Configuration config, RestTemplate restTemplate) {
        this.config = config;
        this.restTemplate = restTemplate;
    }

    /**
     * This method creates builds a request body for boundary-definition search
     *
     * @param request
     * @param boundaryHierarchyList
     * @return
     */
    private BoundaryTypeHierarchyRequest buildBoundaryHierarchyCreateRequest(GeopodeBoundaryRequest request, List<BoundaryTypeHierarchy> boundaryHierarchyList) {
        return BoundaryTypeHierarchyRequest.builder()
                .requestInfo(request.getRequestInfo())
                .boundaryHierarchy(BoundaryTypeHierarchyDefinition.builder()
                        .tenantId(request.getGeopodeBoundary().getTenantId())
                        .hierarchyType(HIERARCHY_TYPE)
                        .boundaryHierarchy(boundaryHierarchyList)
                        .build()).build();
    }

    /**
     * This method creates make a request boundary-definition search
     *
     * @param request
     * @return boundaryDefinitionResponse
     */
    public BoundaryHierarchyDefinitionResponse fetchBoundaryHierarchyDefinition(BoundaryHierarchyDefinitionSearchRequest request) {
        StringBuilder url = new StringBuilder(config.getBoundaryServiceHost() + config.getBoundaryHierarchySearchEndpoint());
        BoundaryHierarchyDefinitionResponse response = null;

        try {
            response = restTemplate.postForObject(url.toString(), request, BoundaryHierarchyDefinitionResponse.class);
        } catch (Exception e) {
            log.error(ERROR_IN_SEARCH, e);
        }

        return response;
    }

    /**
     * This method is for creating boundary entity
     *
     * @param boundaryRequest
     * @return
     */
    public BoundaryResponse sendBoundaryRequest(BoundaryRequest boundaryRequest) {
        /*
                List<List<List<Double>>> rings=null;
                // Access geometry
                if (arcresponse.getFeatures() != null && !arcresponse.getFeatures().isEmpty()) {
                    Geometry geometry = arcresponse.getFeatures().get(0).getGeometry();  // First feature
                    rings = geometry.getRings();  // Actual coordinates
                }
                ObjectMapper mapper = new ObjectMapper();
                JsonNode ringsNode = mapper.valueToTree(rings);
         */
        String url = config.getBoundaryServiceHost() + config.getBoundaryEntityCreateEndpoint();

        try {
            return restTemplate.postForObject(url, boundaryRequest, BoundaryResponse.class);
        } catch (Exception e) {
            log.error(ERROR_FETCHING_FROM_BOUNDARY, e);
            throw new CustomException("BOUNDARY_CREATION_FAILED", "Failed to create boundary");
        }
    }

    /**
     * This method is for creating request for create-boundary entity
     *
     * @param countryName
     * @param tenantId
     * @param requestInfo
     * @return
     */
    public BoundaryRequest buildBoundaryRequest(String countryName, String tenantId, RequestInfo requestInfo) {
        return BoundaryRequest.builder()
                .requestInfo(requestInfo)
                .boundary(List.of(
                        Boundary.builder()
                                .code(countryName)
                                .tenantId(config.getTenantId())
                                .build()
                ))
                .build();
    }

    /**
     * This method is for creating a unique code for the purpose of boundary-entity creation
     * If country already initialized in boundary-hierarchy return error
     *
     * @param code
     * @param currentLevel
     * @param requestInfo
     * @return
     */
    public List<String> createUniqueBoundaryName(String code, String currentLevel, RequestInfo requestInfo) {
        String baseCode = HIERARCHY_TYPE + '_' + currentLevel + '_' + code.trim().replaceAll("[^a-zA-Z0-9]", "_"); // base unique code
        String uniqueCode = baseCode;
        Boolean isAlreadyThere = checkBoundaryEntitySearchResponse(uniqueCode, requestInfo);

        //If country already initialized in boundary-hierarchy return error
        if (Objects.equals(currentLevel, ROOT_HIERARCHY_LEVEL) && isAlreadyThere) {
            throw new CustomException(ALREADY_EXISTS, "Country already exists for given HierarchyType");
        }

        if (isAlreadyThere) {
            String trimmedCode = code.trim().replaceAll("[^a-zA-Z0-9]", "_");
            int attempt = 1;

            // Try suffixes like 01, 02... while maintaining length < 64
            while (attempt < 100) { // limit to 99 attempts
                // Trim last 2 characters if length allows


                String suffix = String.format("%02d", attempt); // creates "01", "02", etc.
                uniqueCode = HIERARCHY_TYPE + '_' + currentLevel + '_' + trimmedCode + "_" + suffix;

                // Ensure uniqueCode length < 64
                if (uniqueCode.length() >= 64) {
                    // Trim the code further if needed
                    int excessLength = uniqueCode.length() - 63;
                    if (trimmedCode.length() > excessLength) {
                        trimmedCode = trimmedCode.substring(0, trimmedCode.length() - excessLength);
                        uniqueCode = HIERARCHY_TYPE + '_' + currentLevel + '_' + trimmedCode + '_' + suffix;
                    } else {
                        throw new RuntimeException("Cannot generate a unique boundary name under 64 characters.");
                    }
                }
                // Check if this version is unique
                if (!checkBoundaryEntitySearchResponse(uniqueCode, requestInfo)) {
                    break; // unique version found
                }
                attempt++;
            }
            if (attempt == 100) {
                throw new RuntimeException("Could not generate a unique boundary name after 99 attempts");
            }
        }

        return Arrays.asList(code, uniqueCode);
    }

    /**
     * This method is for searching if Boundary code already exists in Boundary-service
     *
     * @param code
     * @param requestInfo
     * @return
     */
    public Boolean checkBoundaryEntitySearchResponse(String code, RequestInfo requestInfo) {
        BoundaryRequest boundaryRequest = BoundaryRequest.builder().requestInfo(requestInfo).build();
        URI uri = UriComponentsBuilder.fromHttpUrl(config.getBoundaryServiceHost() + config.getBoundaryEntitySearchEndpoint())
                .queryParam("tenantId", config.getTenantId())
                .queryParam("codes", code)
                .build().encode().toUri();
        BoundaryResponse boundaryResponse = restTemplate.postForObject(uri, boundaryRequest, BoundaryResponse.class);
        Boolean hasBoundaries = boundaryResponse != null && boundaryResponse.getBoundary() != null && !boundaryResponse.getBoundary().isEmpty();
        return hasBoundaries;

    }

    /**
     * This method is building request for BoundaryHierarchyDefinitionSearch
     *
     * @param request
     * @return
     */
    public BoundaryHierarchyDefinitionSearchRequest buildBoundaryHierarchyDefinitionSearchRequest(GeopodeBoundaryRequest request) {
        return BoundaryHierarchyDefinitionSearchRequest.builder()
                .requestInfo(request.getRequestInfo()) // Extract request info from original request
                .boundaryTypeHierarchySearchCriteria( // Set the search criteria
                        BoundaryHierarchyDefinitionSearchCriteria.builder()
                                .hierarchyType(HIERARCHY_TYPE) // Use the defined hierarchy type
                                .build()
                )
                .build();
    }
}
