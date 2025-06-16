package digit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.repository.PlanRepository;
import digit.service.workflow.WorkflowService;
import digit.web.models.PlanRequest;
import digit.web.models.PlanResponse;
import java.lang.Exception;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.fasterxml.jackson.core.type.TypeReference;
import digit.web.models.Plan;
import digit.web.models.PlanSearchCriteria;
import digit.web.models.PlanSearchRequest;
import java.util.List;
import java.util.Map;
import static io.unlogged.UnloggedTestUtils.*;
import static org.mockito.ArgumentMatchers.*;

public final class TestPlanServiceV {

    private PlanService planService;

    private PlanEnricher planEnricher;

    private PlanRepository planRepository;

    private PlanValidator planValidator;

    private WorkflowService workflowService;


    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() throws Exception {
        planEnricher = Mockito.mock(PlanEnricher.class);
        planRepository = Mockito.mock(PlanRepository.class);
        planValidator = Mockito.mock(PlanValidator.class);
        workflowService = Mockito.mock(WorkflowService.class);
        planService = new PlanService(planValidator, planEnricher, planRepository, workflowService);
    }

    @Test
    public void testMethodCreatePlan() throws Exception {
        PlanRequest body = objectMapper.readValue("{}", PlanRequest.class);
        PlanResponse planResponse = planService.createPlan(body);
        PlanResponse planResponseExpected = null;
        Assertions.assertEquals(planResponseExpected, planResponse);
    }

    @Test
    public void testMethodSearchPlan() throws Exception {
        // 
        Mockito.when(planRepository.count(any(PlanSearchCriteria.class))).thenReturn(0);
        // 
        // 
        Map<String, Integer> statusCountResult = objectMapper.readValue("{\"keyFromClassMap\": \"0\"}", new TypeReference<Map<String, Integer>>() {
        });
        Mockito.when(planRepository.statusCount(any(PlanSearchRequest.class))).thenReturn(statusCountResult);
        // 
        // 
        List<Plan> searchResult = objectMapper.readValue("[{\"id\": \"string\", \"tenantId\": \"string\", \"locality\": \"string\", \"campaignId\": \"string\", \"planConfigurationId\": \"string\", \"status\": \"string\", \"assignee\": [\"string\"], \"additionalDetails\": \"0\", \"activities\": [{\"id\": \"string\", \"code\": \"string\", \"description\": \"string\", \"plannedStartDate\": \"0\", \"plannedEndDate\": \"0\", \"dependencies\": [\"string\"], \"conditions\": [{\"id\": \"string\", \"entity\": \"string\", \"entityProperty\": \"string\", \"expression\": \"string\"}]}], \"resources\": [{\"id\": \"string\", \"resourceType\": \"string\", \"estimatedNumber\": {\"intVal\": {\"signum\": \"0\", \"mag\": [\"0\"], \"bitCountPlusOne\": \"0\", \"bitLengthPlusOne\": \"0\", \"lowestSetBitPlusTwo\": \"0\", \"firstNonzeroIntNumPlusTwo\": \"0\"}, \"scale\": \"0\", \"precision\": \"0\", \"stringCache\": \"string\", \"intCompact\": \"0\"}, \"activityCode\": \"string\"}], \"targets\": [{\"id\": \"string\", \"metric\": \"string\", \"metricDetail\": {\"metricValue\": {\"intVal\": {\"signum\": \"0\", \"mag\": [\"0\"], \"bitCountPlusOne\": \"0\", \"bitLengthPlusOne\": \"0\", \"lowestSetBitPlusTwo\": \"0\", \"firstNonzeroIntNumPlusTwo\": \"0\"}, \"scale\": \"0\", \"precision\": \"0\", \"stringCache\": \"string\", \"intCompact\": \"0\"}, \"metricComparator\": \"GREATER_THAN\", \"metricUnit\": \"string\"}, \"activityCode\": \"string\"}], \"auditDetails\": {\"createdBy\": \"string\", \"lastModifiedBy\": \"string\", \"createdTime\": \"0\", \"lastModifiedTime\": \"0\"}, \"jurisdictionMapping\": {\"keyFromClassMap\": \"string\"}, \"additionalFields\": [{\"id\": \"string\", \"key\": \"string\", \"value\": {\"intVal\": {\"signum\": \"0\", \"mag\": [\"0\"], \"bitCountPlusOne\": \"0\", \"bitLengthPlusOne\": \"0\", \"lowestSetBitPlusTwo\": \"0\", \"firstNonzeroIntNumPlusTwo\": \"0\"}, \"scale\": \"0\", \"precision\": \"0\", \"stringCache\": \"string\", \"intCompact\": \"0\"}, \"showOnUi\": \"true\", \"editable\": \"true\", \"order\": \"0\"}], \"boundaryAncestralPath\": \"string\", \"isRequestFromResourceEstimationConsumer\": true, \"assigneeJurisdiction\": [\"string\"], \"workflow\": {\"action\": \"string\", \"comments\": \"string\", \"documents\": [{\"id\": \"string\", \"documentType\": \"string\", \"fileStore\": \"string\", \"documentUid\": \"string\", \"additionalDetails\": \"0\"}], \"assignes\": [\"string\"], \"rating\": \"0\"}}]", new TypeReference<List<Plan>>() {
        });
        Mockito.when(planRepository.search(any(PlanSearchCriteria.class))).thenReturn(searchResult);
        // 
        PlanSearchRequest body = objectMapper.readValue("{}", PlanSearchRequest.class);
        PlanResponse planResponse = planService.searchPlan(body);
        PlanResponse planResponseExpected = null;
        Assertions.assertEquals(planResponseExpected, planResponse);
    }

    @Test
    public void testMethodUpdatePlan() throws Exception {
        PlanRequest body = objectMapper.readValue("{}", PlanRequest.class);
        PlanResponse planResponse = planService.updatePlan(body);
        PlanResponse planResponseExpected = null;
        Assertions.assertEquals(planResponseExpected, planResponse);
    }
}
