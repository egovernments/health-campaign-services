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
class PfNullIdValidatorTest {

    @InjectMocks
    private PfNullIdValidator pfNullIdValidator;

    @Test
    @DisplayName("should add to error details if id is null")
    void shouldAddErrorDetailsIfIdNull() {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfNullIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 1);
    }

    @Test
    @DisplayName("should not add to error details if id is not  null")
    void shouldNotAddErrorDetailsIfIdNotNull() {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacility().withRequestInfo().build();
        request.getProjectFacilities().get(0).setId("some-id");

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfNullIdValidator.validate(request);
        assertEquals(errorDetailsMap.size(), 0);
    }
}
