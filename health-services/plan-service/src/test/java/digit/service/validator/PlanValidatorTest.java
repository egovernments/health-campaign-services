package digit.service.validator;

import digit.config.Configuration;
import digit.repository.impl.PlanRepositoryImpl;
import digit.service.PlanEmployeeService;
import digit.service.PlanEnricher;
import digit.util.BoundaryUtil;
import digit.util.CampaignUtil;
import digit.util.CommonUtil;
import digit.util.MdmsUtil;
import digit.web.models.*;
import digit.web.models.boundary.*;
import digit.web.models.projectFactory.CampaignDetail;
import digit.web.models.projectFactory.CampaignResponse;
import org.egov.common.contract.models.Workflow;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static digit.config.ErrorConstants.INVALID_PLAN_CONFIG_ID_CODE;
import static digit.config.ErrorConstants.INVALID_PLAN_CONFIG_ID_MESSAGE;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PlanValidatorTest {
    @InjectMocks
    private PlanValidator planValidator;

    @Mock private PlanRepositoryImpl planRepository;
    @Mock private MdmsUtil mdmsUtil;
    @Mock private MultiStateInstanceUtil centralInstanceUtil;
    @Mock private CommonUtil commonUtil;
    @Mock private CampaignUtil campaignUtil;
    @Mock private PlanEmployeeService planEmployeeService;
    @Mock private Configuration config;
    @Mock private PlanEnricher planEnricher;
    @Mock private BoundaryUtil boundaryUtil;
    private Plan commonPlan;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        planValidator = new PlanValidator(
                planRepository, mdmsUtil, centralInstanceUtil, commonUtil, campaignUtil,
                planEmployeeService, config, planEnricher, boundaryUtil
        );

        commonPlan = Plan.builder()
                .id("id")
                .tenantId("t1")
                .planConfigurationId("cfg1")
                .campaignId("cmp-123")
                .activities(List.of(Activity.builder().code("A").plannedStartDate(1L).plannedEndDate(2L).build())) // Non-empty list
                .resources(Collections.emptyList())
                .targets(Collections.emptyList())
                .workflow(Workflow.builder().action("CREATE").build())
                .status("Draft")
                .locality("loc")
                .boundaryAncestralPath("some.path")
                .build();
    }

    @Test
    void validateCampaignId_shouldThrow_whenEmpty() {
        CampaignResponse response = new CampaignResponse();
        response.setCampaignDetails(Collections.emptyList());
        assertThatThrownBy(() -> {
            // Use reflection to call private method
            var m = PlanValidator.class.getDeclaredMethod("validateCampaignId", CampaignResponse.class);
            m.setAccessible(true);
            m.invoke(planValidator, response);
        }).hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void validateActivityDependencies_shouldCallSubValidations() {
        Plan plan = Plan.builder().activities(Collections.emptyList()).build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        assertThatCode(() -> {
            var m = PlanValidator.class.getDeclaredMethod("validateActivityDependencies", PlanRequest.class);
            m.setAccessible(true);
            m.invoke(planValidator, request);
        }).doesNotThrowAnyException();
    }

    @Test
    void checkForCycleInActivityDependencies_shouldThrowOnCycle() {
        Activity a1 = Activity.builder().code("A").dependencies(List.of("B")).build();
        Activity a2 = Activity.builder().code("B").dependencies(List.of("A")).build();
        Plan plan = Plan.builder().activities(List.of(a1, a2)).build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        assertThatThrownBy(() -> {
            var m = PlanValidator.class.getDeclaredMethod("checkForCycleInActivityDependencies", PlanRequest.class);
            m.setAccessible(true);
            m.invoke(planValidator, request);
        }).hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void validateDependentActivityCodes_shouldThrowOnInvalidDependency() {
        Activity a1 = Activity.builder().code("A").dependencies(List.of("B")).build();
        Plan plan = Plan.builder().activities(List.of(a1)).build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        assertThatThrownBy(() -> {
            var m = PlanValidator.class.getDeclaredMethod("validateDependentActivityCodes", PlanRequest.class);
            m.setAccessible(true);
            m.invoke(planValidator, request);
        }).hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void validateActivities_shouldThrowOnNullActivities() {
        Plan plan = Plan.builder().activities(null).build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        assertThatThrownBy(() -> {
            var m = PlanValidator.class.getDeclaredMethod("validateActivities", PlanRequest.class);
            m.setAccessible(true);
            m.invoke(planValidator, request);
        }).hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void validatePlanConfigurationExistence_shouldThrow_whenInvalid() {
        Plan plan = Plan.builder().planConfigurationId("id").tenantId("t1").build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        when(commonUtil.searchPlanConfigId(any(), any())).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> planValidator.validatePlanConfigurationExistence(request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void validateResources_shouldThrow_whenMandatory() {
        Plan plan = Plan.builder().planConfigurationId(null).resources(Collections.emptyList()).build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        assertThatThrownBy(() -> {
            var m = PlanValidator.class.getDeclaredMethod("validateResources", PlanRequest.class);
            m.setAccessible(true);
            m.invoke(planValidator, request);
        }).hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void validateResourceActivityLinkage_shouldThrow_whenInvalid() {
        Activity a = Activity.builder().code("A").build();
        Resource r = Resource.builder().activityCode("B").build();
        Plan plan = Plan.builder().planConfigurationId(null).activities(List.of(a)).resources(List.of(r)).build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        assertThatThrownBy(() -> {
            var m = PlanValidator.class.getDeclaredMethod("validateResourceActivityLinkage", PlanRequest.class);
            m.setAccessible(true);
            m.invoke(planValidator, request);
        }).hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void validateTargetActivityLinkage_shouldThrow_whenInvalid() {
        Activity a = Activity.builder().code("A").build();
        Target t = Target.builder().activityCode("B").build();
        Plan plan = Plan.builder().activities(List.of(a)).targets(List.of(t)).build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        assertThatThrownBy(() -> {
            var m = PlanValidator.class.getDeclaredMethod("validateTargetActivityLinkage", PlanRequest.class);
            m.setAccessible(true);
            m.invoke(planValidator, request);
        }).hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void validateTargetUuidUniqueness_shouldThrow_whenDuplicate() {
        Target t1 = Target.builder().id("1").build();
        Target t2 = Target.builder().id("1").build();
        Plan plan = Plan.builder().targets(List.of(t1, t2)).build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        assertThatThrownBy(() -> {
            var m = PlanValidator.class.getDeclaredMethod("validateTargetUuidUniqueness", PlanRequest.class);
            m.setAccessible(true);
            m.invoke(planValidator, request);
        }).hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void validateResourceUuidUniqueness_shouldThrow_whenDuplicate() {
        Resource duplicateResource1 = Resource.builder().id("1").build();
        Resource duplicateResource2 = Resource.builder().id("1").build();

        Plan planWithDuplicateResources = Plan.builder().resources(List.of(duplicateResource1, duplicateResource2)).build();
        PlanRequest planRequest = PlanRequest.builder().plan(planWithDuplicateResources).build();
        assertThatThrownBy(() -> {
            var method = PlanValidator.class.getDeclaredMethod("validateResourceUuidUniqueness", PlanRequest.class);
            method.setAccessible(true);
            method.invoke(planValidator, planRequest);
        }).hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void validateActivitiesUuidUniqueness_shouldThrow_whenDuplicate() {
        Activity a1 = Activity.builder().id("1").build();
        Activity a2 = Activity.builder().id("1").build();
        Plan plan = Plan.builder().activities(List.of(a1, a2)).build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        assertThatThrownBy(() -> {
            var m = PlanValidator.class.getDeclaredMethod("validateActivitiesUuidUniqueness", PlanRequest.class);
            m.setAccessible(true);
            m.invoke(planValidator, request);
        }).hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void validatePlanExistence_shouldThrow_whenNotFound() {
        Plan plan = Plan.builder().id("id").build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        when(planRepository.search(any())).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> {
            var m = PlanValidator.class.getDeclaredMethod("validatePlanExistence", PlanRequest.class);
            m.setAccessible(true);
            m.invoke(planValidator, request);
        }).hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void validateTargetMetrics_shouldThrow_whenMetricNotFound() {
        Target t = Target.builder().metric("M1").build();
        Plan plan = Plan.builder().targets(List.of(t)).build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        Object mdmsData = new Object();
        assertThatThrownBy(() -> planValidator.validateTargetMetrics(request, mdmsData))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void validateMetricDetailUnit_shouldThrow_whenUnitNotFound() {
        MetricDetail md = MetricDetail.builder().metricUnit("U1").build();
        Target t = Target.builder().metricDetail(md).build();
        Plan plan = Plan.builder().targets(List.of(t)).build();
        PlanRequest request = PlanRequest.builder().plan(plan).build();
        Object mdmsData = new Object();
        assertThatThrownBy(() -> planValidator.validateMetricDetailUnit(request, mdmsData))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void validatePlanEmployeeAssignmentAndJurisdiction_shouldThrow_whenAssignmentNotFound() {
        Plan plan = Plan.builder().planConfigurationId("id").locality("loc").build();
        RequestInfo requestInfo = RequestInfo.builder().userInfo(User.builder().uuid("u1").build()).build();
        PlanRequest planRequest = PlanRequest.builder().plan(plan).requestInfo(requestInfo).build();
        when(planEmployeeService.search(any())).thenReturn(PlanEmployeeAssignmentResponse.builder().planEmployeeAssignment(Collections.emptyList()).build());
        when(config.getPlanEstimationApproverRoles()).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> planValidator.validatePlanEmployeeAssignmentAndJurisdiction(planRequest))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void validateJurisdiction_shouldThrow_whenNotFound() {
        Plan plan = Plan.builder().boundaryAncestralPath("A|B|C").build();
        Set<String> jurisdictions = Set.of("X");
        assertThatThrownBy(() -> planValidator.validateJurisdiction(plan, jurisdictions))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void validateBoundaryCode_shouldThrow_whenNoBoundary() {
        BoundarySearchResponse response = mock(BoundarySearchResponse.class);
        HierarchyRelation relation = mock(HierarchyRelation.class);
        when(response.getTenantBoundary()).thenReturn(List.of(relation));
        when(relation.getBoundary()).thenReturn(Collections.emptyList());
        Plan plan = Plan.builder().build();
        assertThatThrownBy(() -> {
            var m = PlanValidator.class.getDeclaredMethod("validateBoundaryCode", BoundarySearchResponse.class, Plan.class);
            m.setAccessible(true);
            m.invoke(planValidator, response, plan);
        }).hasCauseInstanceOf(CustomException.class);
    }

    @Test
    void validatePlanConfigurationExistence_shouldThrowException_whenPlanConfigurationIdIsInvalid() {
        Plan plan = Plan.builder()
                .planConfigurationId("invalid-config-id")
                .tenantId("tenant-id")
                .build();

        PlanRequest request = PlanRequest.builder()
                .plan(plan)
                .build();

        when(commonUtil.searchPlanConfigId("invalid-config-id", "tenant-id")).thenReturn(Collections.emptyList());

        Throwable thrown = catchThrowable(() -> planValidator.validatePlanConfigurationExistence(request));

        assertThat(thrown)
                .isInstanceOf(CustomException.class)
                .extracting("code", "message")
                .containsExactly(INVALID_PLAN_CONFIG_ID_CODE, INVALID_PLAN_CONFIG_ID_MESSAGE);
    }

    @Test
    void validatePlanConfigurationExistence_shouldNotThrowException_whenPlanConfigurationIdIsValid() {
        Plan plan = Plan.builder()
                .planConfigurationId("valid-config-id")
                .tenantId("tenant-id")
                .build();

        PlanRequest request = PlanRequest.builder()
                .plan(plan)
                .build();

        when(commonUtil.searchPlanConfigId("valid-config-id", "tenant-id"))
                .thenReturn(List.of(new PlanConfiguration()));

        assertThatCode(() -> planValidator.validatePlanConfigurationExistence(request))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePlanConfigurationExistence_shouldNotThrowException_whenPlanConfigurationIdIsEmpty() {
        PlanRequest request = new PlanRequest();
        Plan plan = new Plan();
        plan.setPlanConfigurationId(null);
        request.setPlan(plan);

        assertThatCode(() -> planValidator.validatePlanConfigurationExistence(request))
                .doesNotThrowAnyException();
    }
}