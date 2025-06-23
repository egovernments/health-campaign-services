package digit.service;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import digit.repository.PlanFacilityRepository;
import digit.service.enrichment.PlanFacilityEnricher;
import digit.service.validator.PlanFacilityValidator;
import digit.util.ResponseInfoFactory;
import digit.web.models.*;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.utils.ResponseInfoUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class PlanFacilityServiceTest {

    @Mock
    private PlanFacilityValidator planFacilityValidator;

    @Mock
    private PlanFacilityEnricher planFacilityEnricher;

    @Mock
    private PlanFacilityRepository planFacilityRepository;

    @Mock
    private ResponseInfoFactory responseInfoFactory;

    @InjectMocks
    private PlanFacilityService service;

    @Test
    void createsPlanFacilitySuccessfully() {
        PlanFacilityRequest request = new PlanFacilityRequest();
        PlanFacility planFacility = new PlanFacility();
        request.setPlanFacility(planFacility);

        when(responseInfoFactory.createResponseInfoFromRequestInfo(any(), eq(true)))
                .thenReturn(new ResponseInfo());
        doNothing().when(planFacilityValidator).validatePlanFacilityCreate(request);
        doNothing().when(planFacilityEnricher).enrichPlanFacilityCreate(request);
        doNothing().when(planFacilityRepository).create(request);

        PlanFacilityResponse response = service.createPlanFacility(request);

        verify(planFacilityValidator).validatePlanFacilityCreate(request);
        verify(planFacilityEnricher).enrichPlanFacilityCreate(request);
        verify(planFacilityRepository).create(request);
        assertNotNull(response);
        assertEquals(Collections.singletonList(planFacility), response.getPlanFacility());
    }

    @Test
    void failsToCreatePlanFacilityWhenValidationFails() {
        PlanFacilityRequest request = new PlanFacilityRequest();
        doThrow(new IllegalArgumentException("Validation failed"))
                .when(planFacilityValidator).validatePlanFacilityCreate(request);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            service.createPlanFacility(request);
        });

        verify(planFacilityValidator).validatePlanFacilityCreate(request);
        verify(planFacilityEnricher, never()).enrichPlanFacilityCreate(request);
        verify(planFacilityRepository, never()).create(request);
        assertEquals("Validation failed", exception.getMessage());
    }

    @Test
    void searchesPlanFacilitySuccessfully() {
        PlanFacilitySearchRequest searchRequest = new PlanFacilitySearchRequest();
        PlanFacilitySearchCriteria criteria = new PlanFacilitySearchCriteria();
        searchRequest.setPlanFacilitySearchCriteria(criteria);

        List<PlanFacility> expectedList = List.of(new PlanFacility());
        when(planFacilityRepository.search(criteria)).thenReturn(expectedList);
        when(planFacilityRepository.count(criteria)).thenReturn(1);
        PlanFacilityResponse response = service.searchPlanFacility(searchRequest);

        verify(planFacilityRepository).search(criteria);
        verify(planFacilityRepository).count(criteria);
        assertNotNull(response);
        assertEquals(expectedList, response.getPlanFacility());
        assertEquals(1, response.getTotalCount());
    }

    @Test
    void updatesPlanFacilitySuccessfully() {
        PlanFacilityRequest request = new PlanFacilityRequest();
        PlanFacility planFacility = new PlanFacility();
        request.setPlanFacility(planFacility);
        doNothing().when(planFacilityValidator).validatePlanFacilityUpdate(request);
        doNothing().when(planFacilityEnricher).enrichPlanFacilityUpdate(request);
        doNothing().when(planFacilityRepository).update(request);

        PlanFacilityResponse response = service.updatePlanFacility(request);

        verify(planFacilityValidator).validatePlanFacilityUpdate(request);
        verify(planFacilityEnricher).enrichPlanFacilityUpdate(request);
        verify(planFacilityRepository).update(request);
        assertNotNull(response);
        assertEquals(Collections.singletonList(planFacility), response.getPlanFacility());
    }

    @Test
    void failsToUpdatePlanFacilityWhenValidationFails() {
        PlanFacilityRequest request = new PlanFacilityRequest();
        doThrow(new IllegalArgumentException("Validation failed"))
                .when(planFacilityValidator).validatePlanFacilityUpdate(request);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            service.updatePlanFacility(request);
        });

        verify(planFacilityValidator).validatePlanFacilityUpdate(request);
        verify(planFacilityEnricher, never()).enrichPlanFacilityUpdate(request);
        verify(planFacilityRepository, never()).update(request);
        assertEquals("Validation failed", exception.getMessage());
    }
}
