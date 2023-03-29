package org.egov.project.validator.facility;

import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.project.helper.ProjectFacilityBulkRequestTestBuilder;
import org.egov.project.helper.ProjectFacilityTestBuilder;
import org.egov.project.repository.ProjectFacilityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PfNonExistentEntityValidatorTest {

    @InjectMocks
    private PfNonExistentEntityValidator pfNonExistentEntityValidator;
    
    @Mock
    private ProjectFacilityRepository facilityRepository;
    
    @Test
    @DisplayName("should add to error details map if entity not found")
    void shouldAddToErrorDetailsMapIfEntityNotFound() {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacilityHavingId("some-id").withRequestInfo().build();
        when(facilityRepository.findById(anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.emptyList());

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfNonExistentEntityValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error details map if entity found")
    void shouldNotAddToErrorDetailsMapIfEntityFound() {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacilityHavingId("some-id").withRequestInfo().build();
        when(facilityRepository.findById(anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.singletonList(ProjectFacilityTestBuilder.builder().withId("some-id").build()));

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfNonExistentEntityValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }
}
