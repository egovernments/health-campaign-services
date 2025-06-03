package digit.util;

import digit.config.Configuration;
import digit.web.models.GeopodeBoundaryRequest;
import digit.web.models.boundaryService.*;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
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

	/**
	 * Creates a boundary hierarchy by making a POST request to the boundary service.
	 *
	 * @param request               The request containing requestInfo and tenantId.
	 * @param boundaryHierarchyList List of boundary type hierarchy definitions to be created.
	 * @return BoundaryTypeHierarchyResponse from the boundary service if successful; null otherwise.
	 */
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

	/**
	 * Builds the BoundaryTypeHierarchyRequest object required for boundary hierarchy creation.
	 *
	 * @param request               Original request containing tenantId and requestInfo.
	 * @param boundaryHierarchyList List of hierarchy definitions.
	 * @return A fully built BoundaryTypeHierarchyRequest object.
	 */
	private BoundaryTypeHierarchyRequest buildBoundaryHierarchyCreateRequest(GeopodeBoundaryRequest request, List<BoundaryTypeHierarchy> boundaryHierarchyList) {
		return BoundaryTypeHierarchyRequest.builder()
				.requestInfo(request.getRequestInfo())
				.boundaryHierarchy(BoundaryTypeHierarchyDefinition.builder()
						.tenantId(request.getGeopodeBoundary().getTenantId())
						.hierarchyType(HIERARCHY_TYPE) //TODO will hierarchy type be incoming?
						.boundaryHierarchy(boundaryHierarchyList)
						.build()).build();
	}

<<<<<<< HEAD
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
=======
	/**
	 * Creates boundary entities by posting boundary codes to the boundary service.
	 *
	 * @param request       The request containing requestInfo and tenantId.
	 * @param boundaryCodes List of boundary codes to be created.
	 * @return BoundaryResponse from the boundary service if successful; null otherwise.
	 */
    public BoundaryResponse createBoundaryEntity(GeopodeBoundaryRequest request, List<String> boundaryCodes) {
		BoundaryRequest boundaryRequest = buildBoundaryEntityRequest(request, boundaryCodes);
		StringBuilder url = new StringBuilder(config.getBoundaryServiceHost() + config.getBoundaryEntityCreateEndpoint());
		BoundaryResponse response = null;

		try{
			response = restTemplate.postForObject(url.toString(), boundaryRequest, BoundaryResponse.class);
		} catch(CustomException e) {
			log.error(ERROR_CREATING_BOUNDARY_ENTITY_WITH_GIVEN_BOUNDARY_CODES, e);
		}

		return response;
    }

	/**
	 * Builds the BoundaryRequest object to create boundary entities.
	 *
	 * @param request       Original request with tenantId and requestInfo.
	 * @param boundaryCodes List of boundary codes to include.
	 * @return A constructed BoundaryRequest with required fields.
	 */
	private BoundaryRequest buildBoundaryEntityRequest(GeopodeBoundaryRequest request, List<String> boundaryCodes) {
		List<Boundary> boundaries = new ArrayList<>();
		boundaryCodes.forEach(boundaryCode -> boundaries.add(Boundary.builder()
                .tenantId(request.getGeopodeBoundary().getTenantId())
                .code(boundaryCode)
                .build()));

		return BoundaryRequest.builder().requestInfo(request.getRequestInfo())
				.boundary(boundaries)
				.build();
	}

	/**
	 * Creates a boundary relationship between a boundary and its parent by making a POST request to the boundary service.
	 *
	 * @param request        The request containing requestInfo and tenantId.
	 * @param boundaryCode   The boundary code for which the relationship is being created.
	 * @param parent         The parent boundary code.
	 * @param hierarchyType  The hierarchy type code.
	 * @param boundaryType   The boundary type code.
	 * @return BoundaryRelationshipResponse from the boundary service if successful; null otherwise.
	 */
	public BoundaryRelationshipResponse createBoundaryRelationship(GeopodeBoundaryRequest request, String boundaryCode,
																   String parent, String hierarchyType, String boundaryType) {
		BoundaryRelationshipRequest boundaryRelationshipRequest = buildBoundaryRelationshipRequest(request, boundaryCode, parent, hierarchyType, boundaryType);
		StringBuilder url = new StringBuilder(config.getBoundaryServiceHost() + config.getBoundaryRelationshipCreateEndpoint());
		BoundaryRelationshipResponse response = null;

		try{
			response = restTemplate.postForObject(url.toString(), boundaryRelationshipRequest, BoundaryRelationshipResponse.class);
		} catch(CustomException e) {
			log.error(ERROR_CREATING_BOUNDARY_RELATIONSHIP + LOG_PLACEHOLDER, boundaryCode, e);
		}

		return response;
	}

	/**
	 * Builds the BoundaryRelationshipRequest used for creating a boundary relationship.
	 *
	 * @param request         Original request containing tenantId and requestInfo.
	 * @param boundaryCode    The boundary code to associate.
	 * @param parentBoundary  The parent boundary code.
	 * @param hierarchyType   The hierarchy type code.
	 * @param boundaryType    The boundary type code.
	 * @return A constructed BoundaryRelationshipRequest object.
	 */
	private BoundaryRelationshipRequest buildBoundaryRelationshipRequest(GeopodeBoundaryRequest request, String boundaryCode,
																		 String parentBoundary, String hierarchyType, String boundaryType) {
		return BoundaryRelationshipRequest.builder()
				.requestInfo(request.getRequestInfo())
				.boundaryRelationship(BoundaryRelation.builder()
						.tenantId(request.getGeopodeBoundary().getTenantId())
						.code(boundaryCode)
						.boundaryType(boundaryType)
						.hierarchyType(hierarchyType)
						.parent(parentBoundary)
						.build()).build();

	}

>>>>>>> a426ce93c8ba0af2c7a32739c191133af8188959
}
