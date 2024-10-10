package digit.service.validator;

import digit.config.Configuration;
import digit.repository.CensusRepository;
import digit.util.BoundaryUtil;
import digit.util.PlanEmployeeAssignmnetUtil;
import digit.web.models.Census;
import digit.web.models.CensusRequest;
import digit.web.models.CensusSearchCriteria;
import digit.web.models.CensusSearchRequest;
import digit.web.models.boundary.BoundarySearchResponse;
import digit.web.models.boundary.EnrichedBoundary;
import digit.web.models.boundary.HierarchyRelation;
import digit.web.models.plan.PlanEmployeeAssignmentResponse;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
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

    public CensusValidator(BoundaryUtil boundaryUtil, PlanEmployeeAssignmnetUtil employeeAssignmnetUtil, Configuration configs, CensusRepository repository) {
        this.boundaryUtil = boundaryUtil;
        this.employeeAssignmnetUtil = employeeAssignmnetUtil;
        this.configs = configs;
        this.repository = repository;
    }

    /**
     * Validates boundary cade, partner assignment and jurisdiction for census create request
     *
     * @param request The create request for census
     */
    public void validateCreate(CensusRequest request) {
        Census census = request.getCensus();
        BoundarySearchResponse boundarySearchResponse = boundaryUtil.fetchBoundaryData(request.getRequestInfo(), census.getBoundaryCode(), census.getTenantId(), census.getHierarchyType(), Boolean.TRUE, Boolean.FALSE);
        boolean flag = true;

        // Validate boundary code against boundary service
        validateBoundaryCode(boundarySearchResponse, census);

        // Validate the user information in the request
        validateUserInfo(request);

        // Validate partner assignment and jurisdiction against plan service
        validatePartnerForCensus(request.getRequestInfo(), census, flag);
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
        enrichBoundaryPath(census, tenantBoundary);
    }

    /**
     * Validates the user information within the provided CensusRequest.
     *
     * @param request the CensusRequest containing the user information to be validated
     * @throws CustomException if the user information is missing in the request
     */
    public void validateUserInfo(CensusRequest request) {
        if (ObjectUtils.isEmpty(request.getRequestInfo().getUserInfo())) {
            throw new CustomException(USERINFO_MISSING_CODE, USERINFO_MISSING_MESSAGE);
        }
    }

    /**
     * Validates partner assignment and jurisdiction against plan service
     *
     * @param requestInfo the requestInfo from the request
     * @param census      the census to be validated
     * @param flag        Validates only when flag is true
     */
    private void validatePartnerForCensus(RequestInfo requestInfo, Census census, boolean flag) {
        if (flag) {
            User userInfo = requestInfo.getUserInfo();
            List<String> jurisdiction = Arrays.asList(census.getBoundaryAncestralPath().get(0).split("\\|"));
            PlanEmployeeAssignmentResponse employeeAssignmentResponse = employeeAssignmnetUtil.fetchPlanEmployeeAssignment(requestInfo, userInfo.getUuid(), census.getSource(), census.getTenantId(), configs.getAllowedCensusRoles(), jurisdiction);

            if (CollectionUtils.isEmpty(employeeAssignmentResponse.getPlanEmployeeAssignment())) {
                throw new CustomException(INVALID_PARTNER_CODE, INVALID_PARTNER_MESSAGE);
            }
        }
    }

    /**
     * Enriches the boundary ancestral path for the provided boundary code in the census request.
     *
     * @param census         The census record whose boundary ancestral path has to be enriched.
     * @param tenantBoundary boundary relationship from the boundary service for the given boundary code.
     */
    private void enrichBoundaryPath(Census census, HierarchyRelation tenantBoundary) {
        EnrichedBoundary boundary = tenantBoundary.getBoundary().get(0);
        StringBuilder boundaryAncestralPath = new StringBuilder(boundary.getCode());

        // Iterate through the child boundary until there are no more
        while (!CollectionUtils.isEmpty(boundary.getChildren())) {
            boundary = boundary.getChildren().get(0);
            boundaryAncestralPath.append("|").append(boundary.getCode());
        }

        // Setting the boundary ancestral path for the provided boundary
        census.setBoundaryAncestralPath(Collections.singletonList(boundaryAncestralPath.toString()));
    }

    /**
     * Validates the search request for Census.
     *
     * @param request The search request for Census
     */
    public void validateSearch(CensusSearchRequest request) {
        validateSearchCriteria(request.getCensusSearchCriteria());
    }

    /**
     * Validates the census search criteria.
     *
     * @param searchCriteria the search criteria for census search
     */
    private void validateSearchCriteria(CensusSearchCriteria searchCriteria) {
        if (ObjectUtils.isEmpty(searchCriteria)) {
            throw new CustomException(SEARCH_CRITERIA_EMPTY_CODE, SEARCH_CRITERIA_EMPTY_MESSAGE);
        }

        if (StringUtils.isEmpty(searchCriteria.getTenantId())) {
            throw new CustomException(TENANT_ID_EMPTY_CODE, TENANT_ID_EMPTY_MESSAGE);
        }
    }

    /**
     * Validates partner assignment and jurisdiction for census update request.
     *
     * @param request the update request for Census.
     */
    public void validateUpdate(CensusRequest request) {
        boolean flag = true;

        // Validate if Census record to be updated exists
        Census census = validateCensusExistence(request);

        // Validate partner assignment and jurisdiction against plan service
        validatePartnerForCensus(request.getRequestInfo(), census, flag);
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
