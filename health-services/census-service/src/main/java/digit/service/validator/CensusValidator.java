package digit.service.validator;

import digit.config.Configuration;
import digit.repository.CensusRepository;
import digit.service.enrichment.CensusEnrichment;
import digit.util.BoundaryUtil;
import digit.util.PlanEmployeeAssignmnetUtil;
import digit.web.models.Census;
import digit.web.models.CensusRequest;
import digit.web.models.CensusSearchCriteria;
import digit.web.models.CensusSearchRequest;
import digit.web.models.boundary.BoundarySearchResponse;
import digit.web.models.boundary.HierarchyRelation;
import digit.web.models.plan.PlanEmployeeAssignmentResponse;
import digit.web.models.plan.PlanEmployeeAssignmentSearchCriteria;
import digit.web.models.plan.PlanEmployeeAssignmentSearchRequest;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;

import static digit.config.ServiceConstants.*;
import static digit.config.ServiceConstants.USERINFO_MISSING_MESSAGE;

@Component
public class CensusValidator {

    private BoundaryUtil boundaryUtil;

    private PlanEmployeeAssignmnetUtil employeeAssignmnetUtil;

    private Configuration configs;

    private CensusRepository repository;

    private CensusEnrichment enrichment;

    public CensusValidator(BoundaryUtil boundaryUtil, PlanEmployeeAssignmnetUtil employeeAssignmnetUtil, Configuration configs, CensusRepository repository, CensusEnrichment enrichment) {
        this.boundaryUtil = boundaryUtil;
        this.employeeAssignmnetUtil = employeeAssignmnetUtil;
        this.configs = configs;
        this.repository = repository;
        this.enrichment = enrichment;
    }

    /**
     * Validates boundary cade, partner assignment and jurisdiction for census create request
     *
     * @param request The create request for census
     */
    public void validateCreate(CensusRequest request) {
        Census census = request.getCensus();
        BoundarySearchResponse boundarySearchResponse = boundaryUtil.fetchBoundaryData(request.getRequestInfo(), census.getBoundaryCode(), census.getTenantId(), census.getHierarchyType(), Boolean.TRUE, Boolean.FALSE);

        // Validate boundary code against boundary service
        validateBoundaryCode(boundarySearchResponse, census);

        // Validate partner assignment and jurisdiction against plan service
        validatePartnerForCensus(request);
    }

    /**
     * Validates the boundary code provided in census request against boundary service.
     *
     * @param boundarySearchResponse response from the boundary service.
     * @param census                 Census record whose boundary code is to be validated.
     */
    private void validateBoundaryCode(BoundarySearchResponse boundarySearchResponse, Census census) {
        HierarchyRelation tenantBoundary = boundarySearchResponse.getTenantBoundary().get(0);

        if (CollectionUtils.isEmpty(tenantBoundary.getBoundary())) {
            throw new CustomException(NO_BOUNDARY_DATA_FOUND_FOR_GIVEN_BOUNDARY_CODE_CODE, NO_BOUNDARY_DATA_FOUND_FOR_GIVEN_BOUNDARY_CODE_MESSAGE);
        }

        // Enrich the boundary ancestral path for the provided boundary code
        enrichment.enrichBoundaryAncestralPath(census, tenantBoundary);
    }

    /**
     * Validates partner assignment and jurisdiction against plan service
     * Also validates the user information within the provided CensusRequest.
     *
     * @param request the census request
     */
    private void validatePartnerForCensus(CensusRequest request) {

        Census census = request.getCensus();

        // Validate the user information in the request
        if (ObjectUtils.isEmpty(request.getRequestInfo().getUserInfo())) {
            throw new CustomException(USERINFO_MISSING_CODE, USERINFO_MISSING_MESSAGE);
        }

        if (census.isPartnerAssignmentValidationEnabled()) {
            User userInfo = request.getRequestInfo().getUserInfo();
            List<String> jurisdiction = Arrays.asList(request.getCensus().getBoundaryAncestralPath().get(0).split("\\|"));

            PlanEmployeeAssignmentSearchCriteria searchCriteria = PlanEmployeeAssignmentSearchCriteria.builder()
                    .employeeId(Collections.singletonList(userInfo.getUuid()))
                    .planConfigurationId(request.getCensus().getSource())
                    .tenantId(request.getCensus().getTenantId())
                    .role(configs.getAllowedCensusRoles())
                    .jurisdiction(jurisdiction)
                    .build();

            PlanEmployeeAssignmentResponse employeeAssignmentResponse = employeeAssignmnetUtil.fetchPlanEmployeeAssignment(PlanEmployeeAssignmentSearchRequest.builder()
                    .requestInfo(request.getRequestInfo())
                    .planEmployeeAssignmentSearchCriteria(searchCriteria)
                    .build());

            if (CollectionUtils.isEmpty(employeeAssignmentResponse.getPlanEmployeeAssignment())) {
                throw new CustomException(INVALID_PARTNER_CODE, INVALID_PARTNER_MESSAGE);
            }

            //enrich jurisdiction of current assignee
            request.getCensus().setAssigneeJurisdiction(employeeAssignmentResponse.getPlanEmployeeAssignment().get(0).getJurisdiction());
        }
    }

    /**
     * Validates the search request for Census.
     *
     * @param request The search request for Census
     */
    public void validateSearch(CensusSearchRequest request) {
        if (ObjectUtils.isEmpty(request.getCensusSearchCriteria())) {
            throw new CustomException(SEARCH_CRITERIA_EMPTY_CODE, SEARCH_CRITERIA_EMPTY_MESSAGE);
        }

        if (StringUtils.isEmpty(request.getCensusSearchCriteria().getTenantId())) {
            throw new CustomException(TENANT_ID_EMPTY_CODE, TENANT_ID_EMPTY_MESSAGE);
        }
    }

    /**
     * Validates partner assignment and jurisdiction for census update request.
     *
     * @param request the update request for Census.
     */
    public void validateUpdate(CensusRequest request) {

        // Validate if Census record to be updated exists
        Census census = validateCensusExistence(request);
        request.getCensus().setBoundaryAncestralPath(census.getBoundaryAncestralPath());

        // Validate partner assignment and jurisdiction against plan service
        validatePartnerForCensus(request);
    }

    /**
     * Validates the existence of census record in the repository
     *
     * @param request The request containing the census to be validated
     * @return the census that exist in the repository
     */
    private Census validateCensusExistence(CensusRequest request) {
        Census census = request.getCensus();
        CensusSearchCriteria searchCriteria = CensusSearchCriteria.builder()
                .id(census.getId())
                .tenantId(census.getTenantId())
                .areaCodes(Collections.singletonList(census.getBoundaryCode()))
                .build();

        List<Census> censusList = repository.search(searchCriteria);

        if (CollectionUtils.isEmpty(censusList)) {
            throw new CustomException(INVALID_CENSUS_CODE, INVALID_CENSUS_MESSAGE);
        }

        return censusList.get(0);
    }

}
