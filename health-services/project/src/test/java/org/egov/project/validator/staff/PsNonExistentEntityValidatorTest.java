package org.egov.project.validator.staff;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkRequest;
import org.egov.project.helper.ProjectStaffBulkRequestTestBuilder;
import org.egov.project.helper.ProjectStaffTestBuilder;
import org.egov.project.repository.ProjectStaffRepository;
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
class PsNonExistentEntityValidatorTest {

    @InjectMocks
    private PsNonExistentEntityValidator psNonExistentEntityValidator;

    @Mock
    private ProjectStaffRepository repository;

    @Test
    @DisplayName("should add to error details map if entity not found")
    void shouldAddToErrorDetailsMapIfEntityNotFound() throws InvalidTenantIdException {
        ProjectStaffBulkRequest request = ProjectStaffBulkRequestTestBuilder.builder()
                .withOneProjectStaffHavingId().build();
        when(repository.findById(anyString(), anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.emptyList());

        Map<ProjectStaff, List<Error>> errorDetailsMap = psNonExistentEntityValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error details map if entity found in the same tenant")
    void shouldNotAddToErrorDetailsMapIfEntityFoundInSameTenant() throws InvalidTenantIdException {
        ProjectStaffBulkRequest request = ProjectStaffBulkRequestTestBuilder.builder()
                .withOneProjectStaffHavingId().build();
        when(repository.findById(anyString(), anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.singletonList(ProjectStaffTestBuilder.builder().withId().build()));

        Map<ProjectStaff, List<Error>> errorDetailsMap = psNonExistentEntityValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should add to error details map if entity exists only in another tenant")
    void shouldAddToErrorDetailsMapIfEntityBelongsToAnotherTenant() throws InvalidTenantIdException {
        ProjectStaffBulkRequest request = ProjectStaffBulkRequestTestBuilder.builder()
                .withOneProjectStaffHavingId().build();
        ProjectStaff otherTenantStaff = ProjectStaffTestBuilder.builder().withId().build();
        otherTenantStaff.setTenantId("other-tenant-id");
        when(repository.findById(anyString(), anyList(), anyBoolean(), anyString()))
                .thenReturn(Collections.singletonList(otherTenantStaff));

        Map<ProjectStaff, List<Error>> errorDetailsMap = psNonExistentEntityValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }
}
