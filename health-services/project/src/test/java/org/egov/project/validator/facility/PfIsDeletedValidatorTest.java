package org.egov.project.validator.facility;


import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.project.helper.ProjectFacilityBulkRequestTestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class PfIsDeletedValidatorTest {

    @InjectMocks
    private PfIsDeletedValidator pfIsDeletedValidator;

    @Test
    @DisplayName("should add project facility to error details if is Deleted is true")
    void shouldAddProjectFacilityToErrorDetailsIfIsDeletedIsTrue() {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();
        request.getProjectFacilities().get(0).setIsDeleted(true);

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfIsDeletedValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add project facility to error details if is Deleted is false")
    void shouldNotAddProjectFacilityToErrorDetailsIfIsDeletedIsFalse() {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfIsDeletedValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }
}
