package org.egov.project.validator;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.Error;
import org.egov.common.models.project.ProjectResource;
import org.egov.common.models.project.ProjectResourceBulkRequest;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkRequest;
import org.egov.project.helper.ProjectResourceTestBuilder;
import org.egov.project.helper.ProjectStaffTestBuilder;
import org.egov.project.repository.ProjectResourceRepository;
import org.egov.project.repository.ProjectStaffRepository;
import org.egov.project.validator.resource.PrUniqueCombinationValidator;
import org.egov.project.validator.staff.PsUniqueCombinationValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UniqueCombinationValidatorTest {

    @InjectMocks
    private PrUniqueCombinationValidator resourceValidator;

    @InjectMocks
    private PsUniqueCombinationValidator staffValidator;

    @Mock
    private ProjectResourceRepository projectResourceRepository;

    @Mock
    private ProjectStaffRepository projectStaffRepository;

    @BeforeEach
    void setUp() {
        lenient().when(projectResourceRepository.findById(any(List.class), anyBoolean(), anyString()))
                .thenReturn(Collections.emptyList());
        lenient().when(projectStaffRepository.findById(any(List.class), anyBoolean(), anyString()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("should add to error if duplicate mapping is found for resource")
    void shouldAddErrorDetailsIfDuplicateResourceFound() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequest.builder().projectResource(Arrays.asList(
                        ProjectResourceTestBuilder.builder().withProjectId("ID101").withProductVariantId("PV101").build(),
                        ProjectResourceTestBuilder.builder().withProjectId("ID101").withProductVariantId("PV101").build()
                )).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        Map<ProjectResource, List<Error>> errorDetailsMap = resourceValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error if duplicate mapping is not found for resource")
    void shouldNotAddErrorDetailsIfDuplicateResourceNotFound() {
        ProjectResourceBulkRequest request = ProjectResourceBulkRequest.builder().projectResource(Arrays.asList(
                ProjectResourceTestBuilder.builder().withProjectId("ID101").withProductVariantId("PV101").build(),
                ProjectResourceTestBuilder.builder().withProjectId("ID101").withProductVariantId("PV102").build()
        )).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        Map<ProjectResource, List<Error>> errorDetailsMap = resourceValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }



    @Test
    @DisplayName("should add to error if duplicate mapping is found for staff")
    void shouldAddErrorDetailsIfDuplicateStaffFound() {
        ProjectStaffBulkRequest request = ProjectStaffBulkRequest.builder().projectStaff(Arrays.asList(
                ProjectStaffTestBuilder.builder().withProjectId("ID101").withUserId("PV101").build(),
                ProjectStaffTestBuilder.builder().withProjectId("ID101").withUserId("PV101").build()
        )).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        Map<ProjectStaff, List<Error>> errorDetailsMap = staffValidator.validate(request);

        assertEquals(1, errorDetailsMap.size());
    }

    @Test
    @DisplayName("should not add to error if duplicate mapping is not found for staff")
    void shouldNotAddErrorDetailsIfDuplicateStaffNotFound() {
        ProjectStaffBulkRequest request = ProjectStaffBulkRequest.builder().projectStaff(Arrays.asList(
                ProjectStaffTestBuilder.builder().withProjectId("ID101").withUserId("PV101").build(),
                ProjectStaffTestBuilder.builder().withProjectId("ID101").withUserId("PV102").build()
        )).requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build()).build();

        Map<ProjectStaff, List<Error>> errorDetailsMap = staffValidator.validate(request);

        assertEquals(0, errorDetailsMap.size());
    }
}
