package digit.service.validator;

import digit.config.Configuration;
import digit.repository.PlanEmployeeAssignmentRepository;
import digit.util.*;
import digit.web.models.*;
import digit.web.models.projectFactory.Boundary;
import digit.web.models.projectFactory.CampaignDetail;
import digit.web.models.projectFactory.CampaignResponse;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.user.UserDetailResponse;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Component;
import org.springframework.util.*;

import java.util.*;
import java.util.stream.Collectors;

import static digit.config.ServiceConstants.*;
import static digit.config.ErrorConstants.*;


@Slf4j
@Component
public class PlanEmployeeAssignmentValidator {

    private MultiStateInstanceUtil centralInstanceUtil;

    private MdmsUtil mdmsUtil;

    private UserUtil userUtil;

    private CommonUtil commonUtil;

    private CampaignUtil campaignUtil;

    private PlanEmployeeAssignmentRepository repository;

    private Configuration config;

    public PlanEmployeeAssignmentValidator(MultiStateInstanceUtil centralInstanceUtil, MdmsUtil mdmsUtil, UserUtil userUtil, CommonUtil commonUtil, CampaignUtil campaignUtil, PlanEmployeeAssignmentRepository repository, Configuration config) {
        this.centralInstanceUtil = centralInstanceUtil;
        this.mdmsUtil = mdmsUtil;
        this.userUtil = userUtil;
        this.commonUtil = commonUtil;
        this.campaignUtil = campaignUtil;
        this.repository = repository;
        this.config = config;
    }

    /**
     * This method validates the create request for plan employee assignment.
     *
     * @param request The create request for plan employee assignment
     */
    public void validateCreate(PlanEmployeeAssignmentRequest request) {
        PlanEmployeeAssignment planEmployeeAssignment = request.getPlanEmployeeAssignment();
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(request.getPlanEmployeeAssignment().getTenantId());
        List<PlanConfiguration> planConfigurations = commonUtil.searchPlanConfigId(planEmployeeAssignment.getPlanConfigurationId(), rootTenantId);
        UserDetailResponse userDetailResponse = userUtil.fetchUserDetail(userUtil.getUserSearchReq(request));

        // Validate if a same assignment already exists
        validateDuplicateRecord(request);

        // Validate if plan config id exists
        validatePlanConfigId(planConfigurations);

        // Validate role of employee against User Service
        validateRoleAgainstUserService(planEmployeeAssignment, userDetailResponse);

        // Validate if role of employee is a conflicting role
        validateRoleConflict(planEmployeeAssignment);

        // Validate campaign id, employee jurisdiction and highest root jurisdiction in case of Root role
        validateCampaignDetails(planConfigurations.get(0).getCampaignId(), rootTenantId, request);

    }

    /**
     * This method validates the provided roles of the employee against User Service
     *
     * @param planEmployeeAssignment The plan employee assignment provided in request
     * @param userDetailResponse     The user detail response from user service for the provided employeeId
     */
    private void validateRoleAgainstUserService(PlanEmployeeAssignment planEmployeeAssignment, UserDetailResponse userDetailResponse) {

        // Validate if employee exists against User Service
        if (CollectionUtils.isEmpty(userDetailResponse.getUser())) {
            throw new CustomException(INVALID_EMPLOYEE_ID_CODE, INVALID_EMPLOYEE_ID_MESSAGE);
        }

        List<String> userRolesFromUserService = userDetailResponse.getUser().get(0).getRoles().stream()
                .map(Role::getCode)
                .toList();

        if (!userRolesFromUserService.contains(planEmployeeAssignment.getRole())) {
            throw new CustomException(INVALID_EMPLOYEE_ROLE_CODE, INVALID_EMPLOYEE_ROLE_MESSAGE);
        }
    }

    /**
     * Validates if the plan employee assignment for the provided details already exists
     *
     * @param request the employee assignment create request
     */
    private void validateDuplicateRecord(PlanEmployeeAssignmentRequest request) {
        PlanEmployeeAssignment employeeAssignment = request.getPlanEmployeeAssignment();

        List<PlanEmployeeAssignment> planEmployeeAssignmentsFromSearch = repository.search(PlanEmployeeAssignmentSearchCriteria.builder()
                .tenantId(employeeAssignment.getTenantId())
                .planConfigurationId(employeeAssignment.getPlanConfigurationId())
                .employeeId(Collections.singletonList(employeeAssignment.getEmployeeId()))
                .role(Collections.singletonList(employeeAssignment.getRole()))
                .build());

        if (!CollectionUtils.isEmpty(planEmployeeAssignmentsFromSearch)) {
            throw new CustomException(PLAN_EMPLOYEE_ASSIGNMENT_ALREADY_EXISTS_CODE, PLAN_EMPLOYEE_ASSIGNMENT_ALREADY_EXISTS_MESSAGE);
        }
    }

    /**
     * Validates that employee with National role is assigned to the highest root jurisdiction only against MDMS
     *
     * @param planEmployeeAssignment The plan employee assignment provided in request
     * @param mdmsData               mdms data from mdms v2
     * @param campaignDetail         the campaign details for the corresponding campaign id
     */
    private void validateRootEmployeeJurisdiction(PlanEmployeeAssignment planEmployeeAssignment, Object mdmsData, CampaignDetail campaignDetail) {
        if (planEmployeeAssignment.getRole().contains(ROOT_PREFIX)) {
            Set<String> jurisdiction = planEmployeeAssignment.getJurisdiction();

            // Validate that National role employee should not have more than one jurisdiction assigned
            if (jurisdiction.size() > 1) {
                throw new CustomException(INVALID_ROOT_EMPLOYEE_JURISDICTION_CODE, INVALID_ROOT_EMPLOYEE_JURISDICTION_MESSAGE);
            }

            String rootLevelJurisdiction = jurisdiction.stream().findFirst().orElse(null);

            // Fetch the highest hierarchy for Microplan from MDMS
            String highestHierarchy = commonUtil.getMicroplanHierarchy(mdmsData).get(HIGHEST_HIERARCHY_FIELD_FOR_MICROPLAN);

            // Filter out the boundary details for the jurisdiction assigned to employee
            // Throw exception if jurisdiction assigned to Root role employee is not the highest hierarchy
            campaignDetail.getBoundaries().stream()
                    .filter(boundary -> boundary.getCode().equals(rootLevelJurisdiction))
                    .forEach(boundary -> {
                        if (!boundary.getType().toLowerCase().equals(highestHierarchy)) {
                            throw new CustomException(INVALID_ROOT_EMPLOYEE_JURISDICTION_CODE, INVALID_ROOT_EMPLOYEE_JURISDICTION_MESSAGE);
                        }
                    });
        }
    }

    /**
     * This method checks if the employee's role provided is a conflicting role against the role map.
     *
     * @param planEmployeeAssignment The plan employee assignment provided in request
     */
    private void validateRoleConflict(PlanEmployeeAssignment planEmployeeAssignment) {

        // Fetch the role mappings from the configuration
        Map<String, String> roleMap = config.getRoleMap();

        // Check if the role of the employee exists in the role map
        if (roleMap.containsKey(planEmployeeAssignment.getRole())) {

            // Fetch existing role assignments for the employee based on their tenant, planConfig id, and employee ID
            // The search is conducted using the conflicting role
            List<PlanEmployeeAssignment> response = repository.search(PlanEmployeeAssignmentSearchCriteria.builder()
                    .tenantId(planEmployeeAssignment.getTenantId())
                    .planConfigurationId(planEmployeeAssignment.getPlanConfigurationId())
                    .employeeId(Collections.singletonList(planEmployeeAssignment.getEmployeeId()))
                    .role(Collections.singletonList(roleMap.get(planEmployeeAssignment.getRole()))).build());

            // If there are any conflicting assignments found, throw a custom exception
            if (!CollectionUtils.isEmpty(response)) {
                throw new CustomException(INVALID_EMPLOYEE_ROLE_CODE, INVALID_EMPLOYEE_ROLE_MESSAGE);
            }
        }
    }

    /**
     * This method validates campaign id and employee's jurisdiction against project factory
     * If the employee has a national role, it validates that the employee has the highest root jurisdiction only
     *
     * @param campaignId                    the campaign id corresponding to the plan config id provided in the request
     * @param tenantId                      the tenant id provided in the request
     * @param planEmployeeAssignmentRequest the plan employee assignment request provided
     */
    private void validateCampaignDetails(String campaignId, String tenantId, PlanEmployeeAssignmentRequest planEmployeeAssignmentRequest) {
        PlanEmployeeAssignment planEmployeeAssignment = planEmployeeAssignmentRequest.getPlanEmployeeAssignment();
        CampaignResponse campaignResponse = campaignUtil.fetchCampaignData(planEmployeeAssignmentRequest.getRequestInfo(), campaignId, tenantId);
        Object mdmsData = mdmsUtil.fetchMdmsData(planEmployeeAssignmentRequest.getRequestInfo(), tenantId);

        // Validate if campaign id exists against project factory
        validateCampaignId(campaignResponse);

        // Validate the provided jurisdiction for the plan employee assignment
        validateEmployeeAssignmentJurisdiction(campaignResponse.getCampaignDetails().get(0), planEmployeeAssignment);

        // Validates the jurisdiction assigned to Root role employee against MDMS
        validateRootEmployeeJurisdiction(planEmployeeAssignment, mdmsData, campaignResponse.getCampaignDetails().get(0));

        // Validates the jurisdiction assigned to a non-Root employee against MDMS
        validateEmployeeJurisdiction(planEmployeeAssignment, mdmsData, campaignResponse.getCampaignDetails().get(0));
    }

    /**
     * Validates that a non-Root role employee is not assigned to the highest or lowest hierarchy against MDMS
     *
     * @param planEmployeeAssignment The plan employee assignment provided in request
     * @param mdmsData               mdms data from mdms v2
     * @param campaignDetail         the campaign details for the corresponding campaign id
     */
    private void validateEmployeeJurisdiction(PlanEmployeeAssignment planEmployeeAssignment, Object mdmsData, CampaignDetail campaignDetail) {
        if (!planEmployeeAssignment.getRole().contains(ROOT_PREFIX)) {
            Set<String> jurisdiction = planEmployeeAssignment.getJurisdiction();

            // Fetch the highest and lowest hierarchy for Microplan from MDMS
            Map<String, String> hierarchyMap = commonUtil.getMicroplanHierarchy(mdmsData);
            String lowestHierarchy = hierarchyMap.get(LOWEST_HIERARCHY_FIELD_FOR_MICROPLAN);
            String highestHierarchy = hierarchyMap.get(HIGHEST_HIERARCHY_FIELD_FOR_MICROPLAN);

            // Filter out the boundary details for the jurisdiction assigned to employee
            // Simultaneously validating if employee is assigned to lowest or highest hierarchy
            campaignDetail.getBoundaries().stream()
                    .filter(boundary -> jurisdiction.contains(boundary.getCode()))
                    .forEach(boundary -> {
                        if (boundary.getType().toLowerCase().equals(lowestHierarchy) ||
                                boundary.getType().toLowerCase().equals(highestHierarchy)) {
                            throw new CustomException(INVALID_EMPLOYEE_JURISDICTION_CODE, INVALID_EMPLOYEE_JURISDICTION_MESSAGE);
                        }
                    });
        }
    }

    /**
     * This method validates if employee's jurisdiction exist in campaign details
     *
     * @param campaignDetail         the campaign details for the corresponding campaign id
     * @param planEmployeeAssignment the plan employee assignment provided in request
     */
    private void validateEmployeeAssignmentJurisdiction(CampaignDetail campaignDetail, PlanEmployeeAssignment planEmployeeAssignment) {

        // Collect all boundary code for the campaign
        Set<String> boundaryCode = campaignDetail.getBoundaries().stream()
                .filter(boundary -> planEmployeeAssignment.getHierarchyLevel().equals(boundary.getType()))
                .map(Boundary::getCode)
                .collect(Collectors.toSet());

        if(CollectionUtils.isEmpty(boundaryCode)) {
            throw new CustomException(INVALID_HIERARCHY_LEVEL_CODE, INVALID_HIERARCHY_LEVEL_MESSAGE);
        }

        planEmployeeAssignment.getJurisdiction()
                .forEach(jurisdiction -> {
                    if (!boundaryCode.contains(jurisdiction)) {
                        throw new CustomException(INVALID_JURISDICTION_CODE, INVALID_JURISDICTION_MESSAGE);
                    }
                });

    }

    /**
     * This method validates if the campaign id provided in the request exists
     *
     * @param campaignResponse The campaign details response from project factory
     */
    private void validateCampaignId(CampaignResponse campaignResponse) {
        if (CollectionUtils.isEmpty(campaignResponse.getCampaignDetails())) {
            throw new CustomException(NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_CODE, NO_CAMPAIGN_DETAILS_FOUND_FOR_GIVEN_CAMPAIGN_ID_MESSAGE);
        }
    }

    /**
     * This method validates if the plan configuration id provided in the request exists
     *
     * @param planConfigurations The list of plan configuration for the provided plan config id
     */
    private void validatePlanConfigId(List<PlanConfiguration> planConfigurations) {
        if (CollectionUtils.isEmpty(planConfigurations)) {
            throw new CustomException(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
        }
    }

    /**
     * This method validates the update request for plan employee assignment.
     *
     * @param request The update request for plan employee assignment.
     */
    public void validateUpdate(PlanEmployeeAssignmentRequest request) {
        PlanEmployeeAssignment planEmployeeAssignment = request.getPlanEmployeeAssignment();
        String rootTenantId = centralInstanceUtil.getStateLevelTenant(request.getPlanEmployeeAssignment().getTenantId());
        List<PlanConfiguration> planConfigurations = commonUtil.searchPlanConfigId(planEmployeeAssignment.getPlanConfigurationId(), rootTenantId);

        // Validate if Plan employee assignment exists
        validatePlanEmployeeAssignmentExistance(planEmployeeAssignment);

        // Validate campaign id and employee jurisdiction for active records
        if(planEmployeeAssignment.getActive())
            validateCampaignDetails(planConfigurations.get(0).getCampaignId(), rootTenantId, request);

    }

    /**
     * This method validates if the plan employee assignment provided in the update request exists
     *
     * @param planEmployeeAssignment The plan employee assignment details from the request
     */
    private void validatePlanEmployeeAssignmentExistance(PlanEmployeeAssignment planEmployeeAssignment) {
        if (ObjectUtils.isEmpty(planEmployeeAssignment.getId())) {
            throw new CustomException(PLAN_EMPLOYEE_ASSIGNMENT_ID_EMPTY_CODE, PLAN_EMPLOYEE_ASSIGNMENT_ID_EMPTY_MESSAGE);
        }

        // Validates the existence of plan employee assignment
        List<PlanEmployeeAssignment> planEmployeeAssignments = repository.search(PlanEmployeeAssignmentSearchCriteria.builder()
                .tenantId(planEmployeeAssignment.getTenantId())
                .id(planEmployeeAssignment.getId())
                .role(Collections.singletonList(planEmployeeAssignment.getRole()))
                .planConfigurationId(planEmployeeAssignment.getPlanConfigurationId())
                .employeeId(Collections.singletonList(planEmployeeAssignment.getEmployeeId()))
                .build());

        if (CollectionUtils.isEmpty(planEmployeeAssignments)) {
            throw new CustomException(INVALID_PLAN_EMPLOYEE_ASSIGNMENT_CODE, INVALID_PLAN_EMPLOYEE_ASSIGNMENT_MESSAGE);
        }
    }

}
