package org.egov.project.validator;

import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.project.helper.ProjectResourceBulkRequestTestBuilder;
import org.egov.project.validator.resource.PrUniqueEntityValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class UniqueEntityValidatorTest {

    @InjectMocks
    PrUniqueEntityValidator prUniqueEntityValidator;

    @Test
    @DisplayName("Should add to error details if duplicate entity is found")
    void shouldAddToErrorDetailsIfDuplicateEntityIsFound() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource().withProjectResource().withRequestInfo().build();
        request.getProjectResource().get(0).setId("some-id");
        request.getProjectResource().get(1).setId("some-id");

        Map<ProjectResource, List<Error>> errorDetailsMap = prUniqueEntityValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("Should not add to error details if duplicate entity is found")
    void shouldNotAddToErrorDetailsIfDuplicateEntityIsFound() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder().withProjectResource().withProjectResource().withRequestInfo().build();
        request.getProjectResource().get(0).setId("some-id");
        request.getProjectResource().get(1).setId("some-other-id");

        Map<ProjectResource, List<Error>> errorDetailsMap = prUniqueEntityValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }
}
