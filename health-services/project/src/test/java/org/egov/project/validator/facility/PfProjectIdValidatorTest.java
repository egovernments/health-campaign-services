package org.egov.project.validator.facility;


import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.project.helper.ProjectFacilityBulkRequestTestBuilder;
import org.egov.project.repository.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PfProjectIdValidatorTest {

    @InjectMocks
    private PfProjectIdValidator pfProjectIdValidator;

    @Mock
    private ProjectRepository projectRepository;

    @Test
    @DisplayName("should add project facility to error details if is Deleted is true")
    void shouldAddProjectFacilityToErrorDetailsIfIsDeletedIsTrue() {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();

        when(projectRepository.validateIds(any(List.class), any(String.class)))
                .thenReturn(Collections.emptyList());

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfProjectIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add project facility to error details if is Deleted is false")
    void shouldNotAddProjectFacilityToErrorDetailsIfIsDeletedIsFalse() {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();

        List<String> projectIdList = new ArrayList<>();
        projectIdList.add("some-project-id");

        when(projectRepository.validateIds(any(List.class), any(String.class)))
                .thenReturn(projectIdList);

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfProjectIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }
}
