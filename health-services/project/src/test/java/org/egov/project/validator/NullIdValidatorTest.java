package org.egov.project.validator;

import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.project.helper.ProjectResourceBulkRequestTestBuilder;
import org.egov.project.validator.resource.PrNullIdValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class NullIdValidatorTest {

    @InjectMocks
    PrNullIdValidator prNullIdValidator;

    @Test
    @DisplayName("should add to error details if id is null")
    void shouldAddToErrorDetailsIfIdNull() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource()
                .withRequestInfo().build();
        request.getProjectResource().get(0).setId(null);

        Map<ProjectResource, List<Error>> errorDetailsMap = prNullIdValidator.validate(request);

        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add to error details if id is not null")
    void shouldNotAddToErrorDetailsIfIdIsNotNull() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder()
                .withProjectResource().build();
        request.getProjectResource().get(0).setId("some-id");

        Map<ProjectResource, List<Error>> errorDetailsMap = prNullIdValidator.validate(request);

        assertEquals(errorDetailsMap.size(), 0);
    }
}
