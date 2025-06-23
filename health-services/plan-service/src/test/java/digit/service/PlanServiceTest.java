package digit.service;

import digit.repository.PlanRepository;
import digit.service.validator.PlanValidator;
import digit.service.workflow.WorkflowService;
import digit.web.models.*;
import org.egov.common.contract.request.RequestInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanServiceTest {

    private PlanValidator planValidator;
    private PlanEnricher planEnricher;
    private PlanRepository planRepository;
    private WorkflowService workflowService;
    private PlanService planService;

    @BeforeEach
    void setUp() {
        planValidator = mock(PlanValidator.class);
        planEnricher = mock(PlanEnricher.class);
        planRepository = mock(PlanRepository.class);
        workflowService = mock(WorkflowService.class);
        planService = new PlanService(planValidator, planEnricher, planRepository, workflowService);
    }

    @Test
    void testCreatePlan() {
        PlanRequest request = mock(PlanRequest.class);
        Plan plan = mock(Plan.class);
        when(request.getPlan()).thenReturn(plan);
        when(request.getRequestInfo()).thenReturn(mock(RequestInfo.class));

        PlanResponse response = planService.createPlan(request);

        verify(planValidator).validatePlanCreate(request);
        verify(planEnricher).enrichPlanCreate(request);
        verify(workflowService).invokeWorkflowForStatusUpdate(request);
        verify(planRepository).create(request);
        assertNotNull(response);
        assertEquals(1, response.getPlan().size());
    }

    @Test
    void testSearchPlan() {
        PlanSearchRequest request = mock(PlanSearchRequest.class);
        PlanSearchCriteria criteria = mock(PlanSearchCriteria.class);
        when(request.getPlanSearchCriteria()).thenReturn(criteria);
        when(request.getRequestInfo()).thenReturn(mock(RequestInfo.class));
        List<Plan> plans = Arrays.asList(mock(Plan.class));
        when(planRepository.search(criteria)).thenReturn(plans);
        when(planRepository.count(criteria)).thenReturn(1);
        when(planRepository.statusCount(request)).thenReturn(Collections.singletonMap("APPROVED", 1));

        PlanResponse response = planService.searchPlan(request);

        verify(planEnricher).enrichSearchRequest(request);
        verify(planRepository).search(criteria);
        verify(planRepository).count(criteria);
        verify(planRepository).statusCount(request);
        assertEquals(plans, response.getPlan());
        assertEquals(1, response.getTotalCount());
        assertEquals(1, response.getStatusCount().get("APPROVED"));
    }

    @Test
    void testUpdatePlan() {
        PlanRequest request = mock(PlanRequest.class);
        Plan plan = mock(Plan.class);
        when(request.getPlan()).thenReturn(plan);
        when(request.getRequestInfo()).thenReturn(mock(RequestInfo.class));

        PlanResponse response = planService.updatePlan(request);

        verify(planValidator).validatePlanUpdate(request);
        verify(planEnricher).enrichPlanUpdate(request);
        verify(workflowService).invokeWorkflowForStatusUpdate(request);
        verify(planRepository).update(request);
        assertNotNull(response);
        assertEquals(1, response.getPlan().size());
    }

    @Test
    void testBulkUpdate() {
        BulkPlanRequest bulkRequest = mock(BulkPlanRequest.class);
        List<Plan> plans = Arrays.asList(mock(Plan.class), mock(Plan.class));
        when(bulkRequest.getPlans()).thenReturn(plans);
        when(bulkRequest.getRequestInfo()).thenReturn(mock(RequestInfo.class));

        PlanResponse response = planService.bulkUpdate(bulkRequest);

        verify(planValidator).validateBulkPlanUpdate(bulkRequest);
        verify(workflowService).invokeWorkflowForStatusUpdate(bulkRequest);
        verify(planRepository).bulkUpdate(bulkRequest);
        assertEquals(plans, response.getPlan());
    }
}
