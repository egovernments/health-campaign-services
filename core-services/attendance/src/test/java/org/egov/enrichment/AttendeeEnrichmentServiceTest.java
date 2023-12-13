package org.egov.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.helper.AttendeeRequestBuilderTest;
import org.egov.util.AttendanceServiceUtil;
import org.egov.web.models.AttendeeCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class AttendeeEnrichmentServiceTest {

    @Mock
    private AttendanceServiceUtil attendanceServiceUtil;


    @InjectMocks
    private AttendeeEnrichmentService attendeeEnrichmentService;



    @DisplayName("update enrollmentDate for attendee if enrollment date is null")
    @Test
    public void shouldEnrichEnrollmentDateWhenEnrollmentDateIsNull() {
        AttendeeCreateRequest attendeeCreateRequest = AttendeeRequestBuilderTest.getAttendeeCreateRequest();

        attendeeCreateRequest.getAttendees().get(0).setEnrollmentDate(null);
        attendeeEnrichmentService.enrichAttendeeOnCreate(attendeeCreateRequest);

        assertNotNull(attendeeCreateRequest.getAttendees().get(0).getEnrollmentDate());
    }
}
