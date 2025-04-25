package digit.util;

import digit.config.Configuration;
import digit.web.models.GeopodeBoundaryRequest;
import digit.web.models.boundaryService.BoundaryTypeHierarchy;
import digit.web.models.boundaryService.BoundaryTypeHierarchyDefinition;
import digit.web.models.boundaryService.BoundaryTypeHierarchyRequest;
import digit.web.models.boundaryService.BoundaryTypeHierarchyResponse;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

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

}
