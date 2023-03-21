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
class PfUniqueEntityValidatorTest {

    @InjectMocks
    private PfUniqueEntityValidator pfUniqueEntityValidator;

    @Test
    @DisplayName("should add to error if duplicate entity is found")
    void shouldAddErrorDetailsIfDuplicateFound() {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withProjectFacility().withProjectFacility().withRequestInfo().build();
        request.getProjectFacilities().get(0).setId("some-id");
        request.getProjectFacilities().get(1).setId("some-id");

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfUniqueEntityValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error if duplicate entity is not found")
    void shouldNotAddErrorDetailsIfDuplicateNotFound() {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withProjectFacility().withProjectFacility().withRequestInfo().build();
        request.getProjectFacilities().get(0).setId("some-id");
        request.getProjectFacilities().get(1).setId("some-other-id");

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfUniqueEntityValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }
}
