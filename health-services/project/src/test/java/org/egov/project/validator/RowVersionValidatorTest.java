package org.egov.project.validator;

import org.egov.common.models.Error;
import org.egov.project.helper.ProjectResourceBulkRequestTestBuilder;
import org.egov.project.helper.ProjectResourceTestBuilder;
import org.egov.project.repository.ProjectResourceRepository;
import org.egov.project.validator.resource.PrRowVersionValidator;
import org.egov.project.web.models.ProjectResource;
import org.egov.project.web.models.ProjectResourceBulkRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RowVersionValidatorTest {

    @Mock
    PrRowVersionValidator prRowVersionValidator;

    @Mock
    ProjectResourceRepository projectResourceRepository;

    @Test
    @DisplayName("Should add to error details if row version mismatch found")
    void shouldAddToErrorDetailsIfRowVersionMismatchFound() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResourceId("some-id").withRequestInfo().build();
        request.getProjectResource().get(0).setRowVersion(2);

        when(projectResourceRepository.findById(anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.singletonList(ProjectResourceTestBuilder.builder().withProjectResource().withId("some-id").build()));


        Map<ProjectResource, List<Error>> errorDetailsMap = prRowVersionValidator.validate(request);
        assertEquals(1, errorDetailsMap.size());

    }

}
