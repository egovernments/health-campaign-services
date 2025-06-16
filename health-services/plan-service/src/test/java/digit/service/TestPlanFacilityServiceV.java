package digit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import digit.repository.PlanFacilityRepository;
import digit.service.enrichment.PlanFacilityEnricher;
import digit.service.validator.PlanFacilityValidator;
import digit.util.ResponseInfoFactory;
import digit.web.models.PlanFacilityRequest;
import digit.web.models.PlanFacilityResponse;
import java.lang.Exception;
import org.egov.common.contract.request.RequestInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.fasterxml.jackson.core.type.TypeReference;
import digit.web.models.PlanFacility;
import digit.web.models.PlanFacilitySearchCriteria;
import digit.web.models.PlanFacilitySearchRequest;
import java.util.List;
import static io.unlogged.UnloggedTestUtils.*;
import static org.mockito.ArgumentMatchers.*;

public final class TestPlanFacilityServiceV {

    private PlanFacilityService planFacilityService;

    private ResponseInfoFactory responseInfoFactory;

    private PlanFacilityValidator planFacilityValidator;

    private PlanFacilityRepository planFacilityRepository;

    private PlanFacilityEnricher planFacilityEnricher;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    public void setup() throws Exception {
        responseInfoFactory = Mockito.mock(ResponseInfoFactory.class);
        planFacilityValidator = Mockito.mock(PlanFacilityValidator.class);
        planFacilityRepository = Mockito.mock(PlanFacilityRepository.class);
        planFacilityEnricher = Mockito.mock(PlanFacilityEnricher.class);
        planFacilityService = new PlanFacilityService(planFacilityValidator, responseInfoFactory, planFacilityEnricher, planFacilityRepository);
    }

    @Test
    public void testMethodCreatePlanFacility() throws Exception {
        // 
        PlanFacilityResponse createResponseInfoFromRequestInfoResult = objectMapper.readValue("{\"apiId\": \"string\", \"ver\": \"string\", \"ts\": \"0\", \"resMsgId\": \"string\", \"msgId\": \"string\", \"status\": \"string\"}", PlanFacilityResponse.class);
        Mockito.when(responseInfoFactory.createResponseInfoFromRequestInfo(any(RequestInfo.class), eq(false))).thenReturn(createResponseInfoFromRequestInfoResult.getResponseInfo());
        // 
        PlanFacilityRequest planFacilityRequest = objectMapper.readValue("{}", PlanFacilityRequest.class);
        PlanFacilityResponse planFacilityResponse = planFacilityService.createPlanFacility(planFacilityRequest);
        PlanFacilityResponse planFacilityResponseExpected = null;
        Assertions.assertEquals(planFacilityResponseExpected, planFacilityResponse);
    }

    @Test
    public void testMethodSearchPlanFacility() throws Exception {
        // 
        PlanFacilityResponse countResult = objectMapper.readValue("\"0\"", PlanFacilityResponse.class);
        Mockito.when(planFacilityRepository.count(any(PlanFacilitySearchCriteria.class))).thenReturn(countResult.getTotalCount());
        // 
        // 
        List<PlanFacility> searchResult = objectMapper.readValue("[{\"id\": \"string\", \"tenantId\": \"string\", \"planConfigurationId\": \"string\", \"planConfigurationName\": \"string\", \"boundaryAncestralPath\": \"string\", \"facilityId\": \"string\", \"facilityName\": \"string\", \"residingBoundary\": \"string\", \"serviceBoundaries\": [\"string\"], \"initiallySetServiceBoundaries\": [\"string\"], \"jurisdictionMapping\": {\"keyFromClassMap\": \"string\"}, \"additionalDetails\": \"0\", \"active\": \"true\", \"auditDetails\": {\"createdBy\": \"string\", \"lastModifiedBy\": \"string\", \"createdTime\": \"0\", \"lastModifiedTime\": \"0\"}}]", new TypeReference<List<PlanFacility>>() {
        });
        Mockito.when(planFacilityRepository.search(any(PlanFacilitySearchCriteria.class))).thenReturn(searchResult);
        // 
        PlanFacilitySearchRequest planFacilitySearchRequest = objectMapper.readValue("{}", PlanFacilitySearchRequest.class);
        PlanFacilityResponse planFacilityResponse = planFacilityService.searchPlanFacility(planFacilitySearchRequest);
        PlanFacilityResponse planFacilityResponseExpected = null;
        Assertions.assertEquals(planFacilityResponseExpected, planFacilityResponse);
    }

    @Test
    public void testMethodUpdatePlanFacility() throws Exception {
        PlanFacilityRequest planFacilityRequest = objectMapper.readValue("{}", PlanFacilityRequest.class);
        PlanFacilityResponse planFacilityResponse = planFacilityService.updatePlanFacility(planFacilityRequest);
        PlanFacilityResponse planFacilityResponseExpected = null;
        Assertions.assertEquals(planFacilityResponseExpected, planFacilityResponse);
    }
}
