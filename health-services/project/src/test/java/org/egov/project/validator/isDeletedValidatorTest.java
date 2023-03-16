package org.egov.project.validator;

import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.project.helper.ProjectResourceBulkRequestTestBuilder;
import org.egov.project.validator.resource.PrIsDeletedValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class isDeletedValidatorTest {

    @InjectMocks
    PrIsDeletedValidator prIsDeletedValidator;

    @Test
    @DisplayName("should add project resource to error details if is deleted is true")
    void shouldAddProjectResourceToErrorDetailsIfIsDeletedIsTrue() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder()
                .withProjectResource().withRequestInfo().build();
        request.getProjectResource().get(0).setIsDeleted(true);

        Map<ProjectResource, List<Error>> errorDetailsMap = prIsDeletedValidator.validate(request);

        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add project resource to error details if is Deleted is false")
    void shouldNotAddProjectResourceToErrorDetailsIfIsDeletedIsFalse() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequestTestBuilder.builder()
                .withProjectResource().withRequestInfo().build();

        Map<ProjectResource, List<Error>> errorDetailsMap = prIsDeletedValidator.validate(request);
        
        assertEquals(errorDetailsMap.size(), 0);
    }
}
