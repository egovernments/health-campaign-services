package digit.util;

import digit.config.Configuration;
import digit.web.models.GeopodeBoundaryRequest;
import digit.web.models.boundaryService.*;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
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

	public BoundaryTypeHierarchyResponse createBoundaryHierarchy(GeopodeBoundaryRequest request, List<BoundaryTypeHierarchy> boundaryHierarchyList) {
		BoundaryTypeHierarchyRequest boundaryTypeHierarchyRequest = buildBoundaryHierarchyCreateRequest(request, boundaryHierarchyList);
		StringBuilder url = new StringBuilder(config.getBoundaryServiceHost() + config.getBoundaryHierarchyCreateEndpoint());
		BoundaryTypeHierarchyResponse response = null;

		try{
			response = restTemplate.postForObject(url.toString(), boundaryTypeHierarchyRequest, BoundaryTypeHierarchyResponse.class);
		} catch(CustomException e) {
			log.error(ERROR_CREATING_BOUNDARY_HIERARCHY_WITH_GIVEN_HIERARCHY + LOG_PLACEHOLDER, HIERARCHY_TYPE, e);
		}

		return response;
	}

	private BoundaryTypeHierarchyRequest buildBoundaryHierarchyCreateRequest(GeopodeBoundaryRequest request, List<BoundaryTypeHierarchy> boundaryHierarchyList) {
		return BoundaryTypeHierarchyRequest.builder()
				.requestInfo(request.getRequestInfo())
				.boundaryHierarchy(BoundaryTypeHierarchyDefinition.builder()
						.tenantId(request.getGeopodeBoundary().getTenantId())
						.hierarchyType(HIERARCHY_TYPE) //TODO will hierarchy type be incoming?
						.boundaryHierarchy(boundaryHierarchyList)
						.build()).build();
	}

	public BoundaryHierarchyDefinitionResponse fetchBoundaryHierarchyDefinition( BoundaryHierarchyDefinitionSearchRequest request){
			StringBuilder url=new StringBuilder(config.getBoundaryServiceHost() + config.getBoundaryHierarchySearchEndpoint());
			BoundaryHierarchyDefinitionResponse response = null;

			try{
				response=restTemplate.postForObject(url.toString(),request, BoundaryHierarchyDefinitionResponse.class);
			} catch(CustomException e) {
				log.error(ERROR_IN_SEARCH, e);
			}

			return response;
	}

	public BoundaryResponse sendBoundaryRequest(BoundaryRequest boundaryRequest) {
		//        List<List<List<Double>>> rings=null;
		//        // Access geometry
		//        if (arcresponse.getFeatures() != null && !arcresponse.getFeatures().isEmpty()) {
		//            Geometry geometry = arcresponse.getFeatures().get(0).getGeometry();  // First feature
		//            rings = geometry.getRings();  // Actual coordinates
		//        }
		//        ObjectMapper mapper = new ObjectMapper();
		//        JsonNode ringsNode = mapper.valueToTree(rings);
		String url = config.getBoundaryServiceHost() + config.getBoundaryEntityCreateEndpoint();

		try {
			return restTemplate.postForObject(url, boundaryRequest, BoundaryResponse.class);
		} catch (Exception e) {
			log.error(ERROR_FETCHING_FROM_BOUNDARY, e);
			throw new CustomException("BOUNDARY_CREATION_FAILED", "Failed to create boundary");
		}
	}

	public BoundaryRequest buildBoundaryRequest(Optional<String> countryName, RequestInfo requestInfo){
		return BoundaryRequest.builder()
				.requestInfo(requestInfo)
				.boundary(List.of(
						Boundary.builder()
								.code(countryName.orElse(null))
								.tenantId(config.getTenantId())
								.build()
				))
				.build();
	}
}
