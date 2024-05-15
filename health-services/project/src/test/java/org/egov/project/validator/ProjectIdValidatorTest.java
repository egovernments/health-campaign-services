package org.egov.project.validator;

import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.project.helper.ProjectResourceBulkRequestTestBuilder;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.validator.resource.PrProjectIdValidator;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProjectIdValidatorTest {

    @InjectMocks
    PrProjectIdValidator prProjectIdValidator;

    @Mock
    private ProjectRepository projectRepository;

    @Test
    @DisplayName("Should add to error details if project id is not found")
    void shouldAddToErrorDetailsIfProjectIdIsNotFound() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();
        when(projectRepository.validateIds(anyList(), anyString())).thenReturn(Collections.emptyList());

        Map<ProjectResource, List<Error>> errorDetailsMap = prProjectIdValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

}
