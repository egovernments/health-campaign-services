package org.egov.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.helper.StaffRequestBuilderTest;
import org.egov.util.AttendanceServiceUtil;
import org.egov.web.models.StaffPermissionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class StaffEnrichmentServiceTest {

    @Mock
    private AttendanceServiceUtil attendanceServiceUtil;

    @InjectMocks
    private StaffEnrichmentService staffEnrichmentService;

    @DisplayName("update enrollmentDate for staff")
    @Test
    public void shouldEnrichEnrollmentDateWhenEnrollmentDateIsNull() {
        StaffPermissionRequest staffPermissionRequest = StaffRequestBuilderTest.getStaffPermissionRequest();

        staffPermissionRequest.getStaff().get(0).setEnrollmentDate(null);
        staffEnrichmentService.enrichStaffPermissionOnCreate(staffPermissionRequest);

        assertNotNull(staffPermissionRequest.getStaff().get(0).getEnrollmentDate());
    }
}
