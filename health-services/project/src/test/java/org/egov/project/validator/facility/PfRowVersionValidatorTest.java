package org.egov.project.validator.facility;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectFacility;
import org.egov.common.models.project.ProjectFacilityBulkRequest;
import org.egov.project.helper.ProjectFacilityBulkRequestTestBuilder;
import org.egov.project.helper.ProjectFacilityTestBuilder;
import org.egov.project.repository.ProjectFacilityRepository;
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
class PfRowVersionValidatorTest {

    @InjectMocks
    private PfRowVersionValidator pfRowVersionValidator;

    @Mock
    private ProjectFacilityRepository facilityRepository;


    @Test
    @DisplayName("should add to error if row version mismatch found")
    void shouldAddToErrorDetailsIfRowVersionMismatchFound() throws InvalidTenantIdException {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacilityHavingIdAndTenantId("some-id", "some-tenant-id").withRequestInfo().build();
        request.getProjectFacilities().get(0).setRowVersion(2);
        when(facilityRepository.findById(anyString(), anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.singletonList(ProjectFacilityTestBuilder.builder().withId("some-id").build()));

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfRowVersionValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error if row version is similar")
    void shouldNotAddToErrorDetailsIfRowVersionSimilar() throws InvalidTenantIdException {
        ProjectFacilityBulkRequest request = ProjectFacilityBulkRequestTestBuilder.builder()
                .withOneProjectFacilityHavingIdAndTenantId("some-id", "some-tenant-id")
                .withRequestInfo().build();
        when(facilityRepository.findById(anyString(), anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.singletonList(ProjectFacilityTestBuilder.builder()
                                .withTenantId("some-tenant-id")
                        .withId("some-id").build()));

        Map<ProjectFacility, List<Error>> errorDetailsMap = pfRowVersionValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }
}
