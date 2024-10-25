package digit.service.validator;

import digit.config.Configuration;
import digit.repository.CensusRepository;
import digit.service.enrichment.CensusEnrichment;
import digit.util.BoundaryUtil;
import digit.util.PlanEmployeeAssignmnetUtil;
import digit.web.models.*;
import digit.web.models.boundary.BoundarySearchResponse;
import digit.web.models.boundary.HierarchyRelation;
import digit.web.models.plan.PlanEmployeeAssignmentResponse;
import digit.web.models.plan.PlanEmployeeAssignmentSearchCriteria;
import digit.web.models.plan.PlanEmployeeAssignmentSearchRequest;
import org.egov.common.contract.request.User;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;

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

        if (census.getPartnerAssignmentValidationEnabled()) {
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
     * Validates partner assignment and jurisdiction for census update request.
     *
     * @param request the update request for Census.
     */
    public void validateUpdate(CensusRequest request) {

        // Validate if Census record to be updated exists
        validateCensusExistence(request);

        // Validate partner assignment and jurisdiction against plan service
        validatePartnerForCensus(request);
    }

    /**
     * Validates the existence of census record in the repository
     *
     * @param request The request containing the census to be validated
     */
    private void validateCensusExistence(CensusRequest request) {
        Census census = request.getCensus();
        CensusSearchCriteria searchCriteria = CensusSearchCriteria.builder()
                .id(census.getId())
                .build();

        List<Census> censusList = repository.search(searchCriteria);

        if (CollectionUtils.isEmpty(censusList)) {
            throw new CustomException(INVALID_CENSUS_CODE, INVALID_CENSUS_MESSAGE);
        }

        request.getCensus().setBoundaryAncestralPath(censusList.get(0).getBoundaryAncestralPath());
    }

    /**
     * Validates census records for bulk update.
     * Validates the census attributes, checks if census records to be validated exist.
     * Also validates the partner assignment and jurisdiction for the given census records.
     *
     * @param request the census bulk update request.
     */
    public void validateBulkUpdate(BulkCensusRequest request) {

        // Validate attributes across each census in the bulk update request
        validateCensusAttributes(request);

        // Validate if census in request body exists
        validateCensusExistence(request);

        // Validate partner assignment and jurisdiction against plan service
        validatePartnerForCensus(request);
    }

    /**
     * Validates partner assignment and jurisdiction against plan service
     * Also validates the user information within the provided CensusRequest.
     *
     * @param request the census bulk update request
     */
    private void validatePartnerForCensus(BulkCensusRequest request) {

        // Validate the user information in the request
        if (ObjectUtils.isEmpty(request.getRequestInfo().getUserInfo())) {
            throw new CustomException(USERINFO_MISSING_CODE, USERINFO_MISSING_MESSAGE);
        }

        List<String> jurisdiction = Arrays.asList(request.getCensus().get(0).getBoundaryAncestralPath().get(0).split("\\|"));

        PlanEmployeeAssignmentSearchCriteria searchCriteria = PlanEmployeeAssignmentSearchCriteria.builder()
                .employeeId(Collections.singletonList(request.getRequestInfo().getUserInfo().getUuid()))
                .planConfigurationId(request.getCensus().get(0).getSource())
                .tenantId(request.getCensus().get(0).getTenantId())
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

        //enrich jurisdiction of current assignee in all census records
        request.getCensus().forEach(census -> census.setAssigneeJurisdiction(employeeAssignmentResponse.getPlanEmployeeAssignment().get(0).getJurisdiction()));
    }

    /**
     * Validates the existence of bulk census records in the repository
     *
     * @param request The request containing all the census records to be validated
     */
    private void validateCensusExistence(BulkCensusRequest request) {

        // Get all census ids to validate existence
        List<Census> censusListFromDatabase = repository.search(CensusSearchCriteria.builder()
                .ids(request.getCensus().stream().map(Census::getId).collect(Collectors.toSet()))
                .offset(0)
                .limit(request.getCensus().size())
                .build());

        // If census id provided is invalid, throw an exception
        if (censusListFromDatabase.size() != request.getCensus().size()) {
            throw new CustomException(INVALID_CENSUS_CODE, INVALID_CENSUS_MESSAGE);
        }

        // Enrich boundary ancestral path for each census object being passed in the request
        enrichBoundaryAncestralPath(request, censusListFromDatabase);

    }

    /**
     * Enriches the census records with boundary ancestral path from repository.
     *
     * @param request                bulk request with all census records.
     * @param censusListFromDatabase existing census records from the repository.
     */
    private void enrichBoundaryAncestralPath(BulkCensusRequest request, List<Census> censusListFromDatabase) {
        Map<String, String> censusIdVsBoundaryAncestralPathMap = censusListFromDatabase.stream()
                .collect(Collectors.toMap(Census::getId, census -> census.getBoundaryAncestralPath().get(0)));

        request.getCensus().forEach(census ->
                census.setBoundaryAncestralPath(Collections.singletonList(censusIdVsBoundaryAncestralPathMap
                        .get(census.getId())))
        );
    }

    /**
     * Validates if census records provided in bulk update are unique.
     * Checks if all the records have same source and tenant id.
     * Also validates if records have same workflow status and action to be taken.
     *
     * @param request the census bulk update request
     */
    private void validateCensusAttributes(BulkCensusRequest request) {

        if (request.getCensus().stream().map(Census::getId).collect(Collectors.toSet()).size()
                != request.getCensus().size()) {
            throw new CustomException(DUPLICATE_CENSUS_ID_IN_BULK_UPDATE_CODE, DUPLICATE_CENSUS_ID_IN_BULK_UPDATE_MESSAGE);
        }

        if (!request.getCensus().stream().allMatch(census ->
                census.getTenantId().equals(request.getCensus().get(0).getTenantId()) &&
                        census.getSource().equals(request.getCensus().get(0).getSource()))) {
            throw new CustomException(INVALID_SOURCE_OR_TENANT_ID_FOR_BULK_UPDATE_CODE, INVALID_SOURCE_OR_TENANT_ID_FOR_BULK_UPDATE_MESSAGE);
        }

        request.getCensus().forEach(census -> {
            if (ObjectUtils.isEmpty(census.getWorkflow())) {
                throw new CustomException(WORKFLOW_NOT_FOUND_FOR_BULK_UPDATE_CODE, WORKFLOW_NOT_FOUND_FOR_BULK_UPDATE_MESSAGE);
            }
        });

        if (!request.getCensus().stream().allMatch(census ->
                census.getStatus().equals(request.getCensus().get(0).getStatus()) &&
                        census.getWorkflow().getAction().equals(request.getCensus().get(0).getWorkflow().getAction()))) {
            throw new CustomException(DIFFERENT_WORKFLOW_FOR_BULK_UPDATE_CODE, DIFFERENT_WORKFLOW_FOR_BULK_UPDATE_MESSAGE);
        }
    }
}

