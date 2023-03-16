package org.egov.project.validator;

import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.project.helper.ProjectResourceBulkRequestTestBuilder;
import org.egov.project.helper.ProjectResourceTestBuilder;
import org.egov.project.repository.ProjectResourceRepository;
import org.egov.project.validator.resource.PrNonExistentEntityValidator;
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
public class NonExistentEntityValidatorTest {

    @InjectMocks
    private PrNonExistentEntityValidator prNonExistentEntityValidator;

    @Mock
    private ProjectResourceRepository projectResourceRepository;

    @Test
    @DisplayName("should add to error details map if entity not found")
    void shouldAddToErrorDetailsMapIfEntityNotFound() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder()
                .withProjectResourceId("some-id").withRequestInfo().build();
        when(projectResourceRepository.findById(anyList(), anyBoolean(), anyString())).
                thenReturn(Collections.emptyList());

        Map<ProjectResource, List<Error>> errorDetailsMap = prNonExistentEntityValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error details map if entity found")
    void shouldNotAddToErrorDetailsMapIfEntityFound() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder()
                .withProjectResourceId("some-id").withRequestInfo().build();
        when(projectResourceRepository.findById(anyList(), anyBoolean(), anyString())).
                thenReturn(Collections.singletonList(ProjectResourceTestBuilder.builder()
                        .withProjectResource().withId("some-id").build()));

        Map<ProjectResource, List<Error>> erorrDetailsMap = prNonExistentEntityValidator.validate(request);

        assertEquals(0, erorrDetailsMap.size());
    }

}
