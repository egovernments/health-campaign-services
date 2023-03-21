package org.egov.project.validator.facility;

import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.project.helper.ProjectFacilityBulkRequestTestBuilder;
import org.egov.project.repository.ProjectFacilityRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PfUniqueCombinationValidatorTest {

    @InjectMocks
    private PfUniqueCombinationValidator pfUniqueCombinationValidator;

    @Mock
    ProjectFacilityRepository projectFacilityRepository;

    @BeforeEach
    void setUp() {
        when(projectFacilityRepository.findById(any(List.class), anyBoolean(), anyString()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("should add to error if duplicate mapping is found")
    void shouldAddErrorDetailsIfDuplicateFound() {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withProjectFacility().withProjectFacility().withRequestInfo().build();

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfUniqueCombinationValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error if duplicate mapping is not found")
    void shouldNotAddErrorDetailsIfDuplicateNotFound() {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withProjectFacility().withProjectFacility().withRequestInfo().build();
        request.getProjectFacilities().get(0).setProjectId("some-id");

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfUniqueCombinationValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }
}
