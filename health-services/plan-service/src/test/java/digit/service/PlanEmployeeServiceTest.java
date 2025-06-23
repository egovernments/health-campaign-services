package digit.service;

import digit.repository.PlanEmployeeAssignmentRepository;
import digit.repository.PlanFacilityRepository;
import digit.service.enrichment.PlanEmployeeAssignmentEnricher;
import digit.service.enrichment.PlanFacilityEnricher;
import digit.service.validator.PlanEmployeeAssignmentValidator;
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
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;



@ExtendWith(MockitoExtension.class)
public class PlanEmployeeServiceTest {

    @Mock
    private PlanEmployeeAssignmentValidator validator;

    @Mock
    private PlanEmployeeAssignmentEnricher enricher;

    @Mock
    private PlanEmployeeAssignmentRepository repository;

    @Mock
    private ResponseInfoFactory responseInfoFactory;

    @InjectMocks
    private PlanEmployeeService service;

    @Test
    void createsPlanEmployeeAssignmentSuccessfully() {
        PlanEmployeeAssignmentRequest request = new PlanEmployeeAssignmentRequest();
        PlanEmployeeAssignment planEmployeeAssignment = new PlanEmployeeAssignment();
        request.setPlanEmployeeAssignment(planEmployeeAssignment);

        doNothing().when(validator).validateCreate(request);
        doNothing().when(enricher).enrichCreate(request);
        doNothing().when(repository).create(request);

        PlanEmployeeAssignmentResponse response = service.create(request);

        verify(validator).validateCreate(request);
        verify(enricher).enrichCreate(request);
        verify(repository).create(request);
        assertNotNull(response);
        assertEquals(Collections.singletonList(planEmployeeAssignment), response.getPlanEmployeeAssignment());
    }

    @Test
    void failsToCreatePlanEmployeeAssignmentWhenValidationFails() {
        PlanEmployeeAssignmentRequest request = new PlanEmployeeAssignmentRequest();
        doThrow(new IllegalArgumentException("Validation failed"))
                .when(validator).validateCreate(request);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            service.create(request);
        });

        verify(validator).validateCreate(request);
        verify(enricher, never()).enrichCreate(request);
        verify(repository, never()).create(request);
        assertEquals("Validation failed", exception.getMessage());
    }

    @Test
    void searchesPlanEmployeeAssignmentSuccessfully() {
        PlanEmployeeAssignmentSearchRequest searchRequest = new PlanEmployeeAssignmentSearchRequest();
        PlanEmployeeAssignmentSearchCriteria criteria = new PlanEmployeeAssignmentSearchCriteria();
        searchRequest.setPlanEmployeeAssignmentSearchCriteria(criteria);

        List<PlanEmployeeAssignment> expectedList = List.of(new PlanEmployeeAssignment());
        when(repository.search(criteria)).thenReturn(expectedList);
        when(repository.count(criteria)).thenReturn(1);
        when(responseInfoFactory.createResponseInfoFromRequestInfo(any(), eq(true)))
                .thenReturn(new ResponseInfo());

        PlanEmployeeAssignmentResponse response = service.search(searchRequest);

        verify(repository).search(criteria);
        verify(repository).count(criteria);
        assertNotNull(response);
        assertEquals(expectedList, response.getPlanEmployeeAssignment());
        assertEquals(1, response.getTotalCount());
    }

    @Test
    void updatesPlanEmployeeAssignmentSuccessfully() {
        PlanEmployeeAssignmentRequest request = new PlanEmployeeAssignmentRequest();
        PlanEmployeeAssignment planEmployeeAssignment = new PlanEmployeeAssignment();
        request.setPlanEmployeeAssignment(planEmployeeAssignment);

        doNothing().when(validator).validateUpdate(request);
        doNothing().when(enricher).enrichUpdate(request);
        doNothing().when(repository).update(request);

        PlanEmployeeAssignmentResponse response = service.update(request);

        verify(validator).validateUpdate(request);
        verify(enricher).enrichUpdate(request);
        verify(repository).update(request);
        assertNotNull(response);
        assertEquals(Collections.singletonList(planEmployeeAssignment), response.getPlanEmployeeAssignment());
    }

    @Test
    void failsToUpdatePlanEmployeeAssignmentWhenValidationFails() {
        PlanEmployeeAssignmentRequest request = new PlanEmployeeAssignmentRequest();
        doThrow(new IllegalArgumentException("Validation failed"))
                .when(validator).validateUpdate(request);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            service.update(request);
        });

        verify(validator).validateUpdate(request);
        verify(enricher, never()).enrichUpdate(request);
        verify(repository, never()).update(request);
        assertEquals("Validation failed", exception.getMessage());
    }
}
