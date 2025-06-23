package digit.service;

import digit.repository.PlanConfigurationRepository;
import digit.service.enrichment.EnrichmentService;
import digit.service.validator.PlanConfigurationValidator;
import digit.service.validator.WorkflowValidator;
import digit.service.workflow.WorkflowService;
import digit.util.CommonUtil;
import digit.util.ResponseInfoFactory;
import digit.web.models.*;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.junit.jupiter.api.BeforeEach;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlanConfigurationServiceTest {

    @Mock
    private EnrichmentService enrichmentService;

    @Mock
    private PlanConfigurationValidator validator;

    @Mock
    private PlanConfigurationRepository repository;

    @Mock
    private ResponseInfoFactory responseInfoFactory;

    @Mock
    private WorkflowService workflowService;

    @Mock
    private CommonUtil commonUtil;

    @Mock
    private PlanService planService;

    @Mock
    private WorkflowValidator workflowValidator;

    @InjectMocks
    private PlanConfigurationService service;

    @BeforeEach
    void setUp() {
        // No manual instantiation needed — Mockito will auto-wire all fields
    }

    @Test
    void create_ShouldEnrichValidateAndPersistPlanConfiguration() {
        PlanConfiguration planConfig = new PlanConfiguration();
        PlanConfigurationRequest request = new PlanConfigurationRequest();
        request.setPlanConfiguration(planConfig);

        when(responseInfoFactory.createResponseInfoFromRequestInfo(any(), eq(true)))
                .thenReturn(new ResponseInfo());
        doNothing().when(enrichmentService).enrichPlanConfigurationBeforeValidation(request);
        doNothing().when(validator).validateCreate(request);
        doNothing().when(enrichmentService).enrichCreate(request);
        doNothing().when(repository).create(request);

        PlanConfigurationResponse response = service.create(request);

        verify(enrichmentService).enrichPlanConfigurationBeforeValidation(request);
        verify(validator).validateCreate(request);
        verify(enrichmentService).enrichCreate(request);
        verify(repository).create(request);
        assertNotNull(response);
        assertEquals(Collections.singletonList(planConfig), response.getPlanConfiguration());
    }

    @Test
    void search_ShouldReturnPlanConfigurationsFromRepository() {
        PlanConfigurationSearchRequest searchRequest = new PlanConfigurationSearchRequest();
        PlanConfigurationSearchCriteria criteria = new PlanConfigurationSearchCriteria();
        searchRequest.setPlanConfigurationSearchCriteria(criteria);
        searchRequest.setRequestInfo(new RequestInfo());

        List<PlanConfiguration> expectedList = List.of(new PlanConfiguration());
        when(repository.search(criteria)).thenReturn(expectedList);
        when(repository.count(criteria)).thenReturn(1);
        when(responseInfoFactory.createResponseInfoFromRequestInfo(any(), eq(true)))
                .thenReturn(new ResponseInfo());

        PlanConfigurationResponse response = service.search(searchRequest);

        verify(repository).search(criteria);
        verify(repository).count(criteria);
        assertNotNull(response);
        assertEquals(expectedList, response.getPlanConfiguration());
        assertEquals(1, response.getTotalCount());
    }

    @Test
    void update_ShouldValidateEnrichAndUpdatePlanConfiguration() {
        // Arrange
        PlanConfiguration planConfig = new PlanConfiguration();
        PlanConfigurationRequest request = new PlanConfigurationRequest();
        request.setPlanConfiguration(planConfig);
        request.setRequestInfo(new RequestInfo());

        PlanConfigurationResponse expectedResponse = new PlanConfigurationResponse();
        expectedResponse.setPlanConfiguration(Collections.singletonList(planConfig));
        expectedResponse.setResponseInfo(new ResponseInfo());

        // Required stubbings (only what's necessary)
        doNothing().when(validator).validateUpdateRequest(request);
        doNothing().when(enrichmentService).enrichUpdate(request);
        doNothing().when(workflowValidator).validateWorkflow(request);
        doNothing().when(workflowService).invokeWorkflowForStatusUpdate(request);
        doNothing().when(repository).update(request);

        // Act
        PlanConfigurationResponse actualResponse = service.update(request);

        // Assert
        verify(validator).validateUpdateRequest(request);
        verify(enrichmentService).enrichUpdate(request);
        verify(workflowValidator).validateWorkflow(request);
        verify(workflowService).invokeWorkflowForStatusUpdate(request);
        verify(repository).update(request);

        assertNotNull(actualResponse);
        assertEquals(expectedResponse.getPlanConfiguration(), actualResponse.getPlanConfiguration());
    }
}
