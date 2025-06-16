package digit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TestPlanConfigurationServiceV {

    private PlanConfigurationService planConfigurationService;

    private PlanConfigurationRepository repository;
    private WorkflowValidator workflowValidator;
    private PlanConfigurationValidator validator;
    private CommonUtil commonUtil;
    private ResponseInfoFactory responseInfoFactory;
    private EnrichmentService enrichmentService;
    private WorkflowService workflowService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        repository = mock(PlanConfigurationRepository.class);
        workflowValidator = mock(WorkflowValidator.class);
        validator = mock(PlanConfigurationValidator.class);
        commonUtil = mock(CommonUtil.class);
        responseInfoFactory = mock(ResponseInfoFactory.class);
        enrichmentService = mock(EnrichmentService.class);
        workflowService = mock(WorkflowService.class);

        planConfigurationService = new PlanConfigurationService(
                enrichmentService, validator, repository,
                responseInfoFactory, workflowService, workflowValidator, commonUtil
        );
    }

    @Test
    void testCreate() {
        PlanConfigurationRequest request = new PlanConfigurationRequest();
        request.setRequestInfo(new RequestInfo());

        when(responseInfoFactory.createResponseInfoFromRequestInfo(any(), eq(true)))
                .thenReturn(new ResponseInfo());

//        when(repository.save(any())).thenReturn(Collections.emptyList());

        PlanConfigurationResponse actual = planConfigurationService.create(request);

        assertEquals(new ResponseInfo(), actual.getResponseInfo());
        assertEquals(Collections.emptyList(), actual.getPlanConfiguration());
    }

    @Test
    void testSearch() {
        PlanConfigurationSearchRequest request = PlanConfigurationSearchRequest.builder()
                .requestInfo(new RequestInfo())
                .planConfigurationSearchCriteria(new PlanConfigurationSearchCriteria()).build();

        when(repository.search(any())).thenReturn(Collections.emptyList());
        when(repository.count(any())).thenReturn(0);
        when(responseInfoFactory.createResponseInfoFromRequestInfo(any(), eq(false)))
                .thenReturn(new ResponseInfo());

        PlanConfigurationResponse actual = planConfigurationService.search(request);

        assertEquals(new ResponseInfo(), actual.getResponseInfo());
        assertEquals(Collections.emptyList(), actual.getPlanConfiguration());
    }

    @Test
    void testUpdate() {
        PlanConfigurationRequest request = new PlanConfigurationRequest();
        request.setRequestInfo(new RequestInfo());

        when(responseInfoFactory.createResponseInfoFromRequestInfo(any(), eq(false)))
                .thenReturn(new ResponseInfo());

//        when(repository.update(any())).thenReturn(Collections.emptyList());

        PlanConfigurationResponse actual = planConfigurationService.update(request);

        assertEquals(new ResponseInfo(), actual.getResponseInfo());
        assertEquals(Collections.emptyList(), actual.getPlanConfiguration());
    }
}
