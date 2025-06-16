package digit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import digit.kafka.Producer;
import digit.repository.PlanEmployeeAssignmentRepository;
import digit.service.enrichment.PlanEmployeeAssignmentEnricher;
import digit.service.validator.PlanEmployeeAssignmentValidator;
import digit.util.ResponseInfoFactory;
import digit.web.models.PlanEmployeeAssignmentRequest;
import digit.web.models.PlanEmployeeAssignmentResponse;
import java.lang.Exception;
import org.egov.common.contract.request.RequestInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.fasterxml.jackson.core.type.TypeReference;
import digit.web.models.PlanEmployeeAssignment;
import digit.web.models.PlanEmployeeAssignmentSearchCriteria;
import digit.web.models.PlanEmployeeAssignmentSearchRequest;
import java.util.List;
import static io.unlogged.UnloggedTestUtils.*;
import static org.mockito.ArgumentMatchers.*;

public final class TestPlanEmployeeServiceV {

    private PlanEmployeeService planEmployeeService;

    private ResponseInfoFactory responseInfoFactory;

    private Configuration config;

    private PlanEmployeeAssignmentEnricher enricher;

    private Producer producer;

    private PlanEmployeeAssignmentValidator validator;

    private PlanEmployeeAssignmentRepository repository;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() throws Exception {
        responseInfoFactory = Mockito.mock(ResponseInfoFactory.class);
        config = Mockito.mock(Configuration.class);
        enricher = Mockito.mock(PlanEmployeeAssignmentEnricher.class);
        producer = Mockito.mock(Producer.class);
        validator = Mockito.mock(PlanEmployeeAssignmentValidator.class);
        repository = Mockito.mock(PlanEmployeeAssignmentRepository.class);
        planEmployeeService = new PlanEmployeeService(producer, config, responseInfoFactory, repository, enricher, validator);
    }

    @Test
    public void testMethodCreate() throws Exception {
        // 
        PlanEmployeeAssignmentResponse createResponseInfoFromRequestInfoResult = objectMapper.readValue("{\"apiId\": \"string\", \"ver\": \"string\", \"ts\": \"0\", \"resMsgId\": \"string\", \"msgId\": \"string\", \"status\": \"string\"}", PlanEmployeeAssignmentResponse.class);
        Mockito.when(responseInfoFactory.createResponseInfoFromRequestInfo(any(RequestInfo.class), eq(false))).thenReturn(createResponseInfoFromRequestInfoResult.getResponseInfo());
        // 
        PlanEmployeeAssignmentRequest request = objectMapper.readValue("{}", PlanEmployeeAssignmentRequest.class);
        PlanEmployeeAssignmentResponse planEmployeeAssignmentResponse = planEmployeeService.create(request);
        PlanEmployeeAssignmentResponse planEmployeeAssignmentResponseExpected = null;
        Assertions.assertEquals(planEmployeeAssignmentResponseExpected, planEmployeeAssignmentResponse);
    }

    @Test
    public void testMethodSearch() throws Exception {
        // 
        PlanEmployeeAssignmentResponse countResult = objectMapper.readValue("\"0\"", PlanEmployeeAssignmentResponse.class);
        Mockito.when(repository.count(any(PlanEmployeeAssignmentSearchCriteria.class))).thenReturn(countResult.getTotalCount());
        // 
        // 
        List<PlanEmployeeAssignment> searchResult = objectMapper.readValue("[{\"id\": \"string\", \"tenantId\": \"string\", \"planConfigurationId\": \"string\", \"planConfigurationName\": \"string\", \"employeeId\": \"string\", \"role\": \"string\", \"hierarchyLevel\": \"string\", \"jurisdiction\": [\"string\"], \"additionalDetails\": \"0\", \"active\": \"true\", \"auditDetails\": {\"createdBy\": \"string\", \"lastModifiedBy\": \"string\", \"createdTime\": \"0\", \"lastModifiedTime\": \"0\"}}]", new TypeReference<List<PlanEmployeeAssignment>>() {
        });
        Mockito.when(repository.search(any(PlanEmployeeAssignmentSearchCriteria.class))).thenReturn(searchResult);
        // 
        // 
        PlanEmployeeAssignmentResponse createResponseInfoFromRequestInfoResult = objectMapper.readValue("{\"apiId\": \"string\", \"ver\": \"string\", \"ts\": \"0\", \"resMsgId\": \"string\", \"msgId\": \"string\", \"status\": \"string\"}", PlanEmployeeAssignmentResponse.class);
        Mockito.when(responseInfoFactory.createResponseInfoFromRequestInfo(any(RequestInfo.class), eq(false))).thenReturn(createResponseInfoFromRequestInfoResult.getResponseInfo());
        // 
        PlanEmployeeAssignmentSearchRequest request = objectMapper.readValue("{}", PlanEmployeeAssignmentSearchRequest.class);
        PlanEmployeeAssignmentResponse planEmployeeAssignmentResponse = planEmployeeService.search(request);
        PlanEmployeeAssignmentResponse planEmployeeAssignmentResponseExpected = null;
        Assertions.assertEquals(planEmployeeAssignmentResponseExpected, planEmployeeAssignmentResponse);
    }

    @Test
    public void testMethodUpdate() throws Exception {
        PlanEmployeeAssignmentRequest request = objectMapper.readValue("{}", PlanEmployeeAssignmentRequest.class);
        PlanEmployeeAssignmentResponse planEmployeeAssignmentResponse = planEmployeeService.update(request);
        PlanEmployeeAssignmentResponse planEmployeeAssignmentResponseExpected = null;
        Assertions.assertEquals(planEmployeeAssignmentResponseExpected, planEmployeeAssignmentResponse);
    }
}
