package org.egov.excelingestion.consumer;

import org.egov.excelingestion.service.AttendanceTemplateSyncService;
import org.egov.excelingestion.web.models.AttendanceSyncEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Routing and ack behaviour of the attendance sync listener. */
@ExtendWith(MockitoExtension.class)
class AttendanceSyncConsumerTest {

    @Mock private AttendanceTemplateSyncService syncService;
    @Mock private Acknowledgment acknowledgment;

    private AttendanceSyncConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AttendanceSyncConsumer(syncService);
    }

    @Test
    void registerDeletePayload_routesToRegisterHandler() {
        List<AttendanceSyncEvent.DeletedRegister> registers =
                List.of(AttendanceSyncEvent.DeletedRegister.builder().id("r1").tenantId("mz").build());

        consumer.consume(AttendanceSyncEvent.builder().attendanceRegister(registers).build(), acknowledgment);

        verify(syncService).handleRegisterDeletes(registers);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void attendeePayload_routesToDeEnrolmentHandlerWithAttendeeLabel() {
        List<AttendanceSyncEvent.Enrolment> attendees =
                List.of(AttendanceSyncEvent.Enrolment.builder().registerId("r1").tenantId("mz").build());

        consumer.consume(AttendanceSyncEvent.builder().attendees(attendees).build(), acknowledgment);

        verify(syncService).handleDeEnrolments(attendees, "attendee");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void staffPayload_routesToDeEnrolmentHandlerWithStaffLabel() {
        List<AttendanceSyncEvent.Enrolment> staff =
                List.of(AttendanceSyncEvent.Enrolment.builder().registerId("r1").tenantId("mz").build());

        consumer.consume(AttendanceSyncEvent.builder().staff(staff).build(), acknowledgment);

        verify(syncService).handleDeEnrolments(staff, "staff");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void unreadablePayload_isAckedWithoutWork() {
        consumer.consume(null, acknowledgment);

        verify(syncService, never()).handleRegisterDeletes(any());
        verify(syncService, never()).handleDeEnrolments(any(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void payloadWithNoKnownCollection_isAckedWithoutWork() {
        consumer.consume(AttendanceSyncEvent.builder().build(), acknowledgment);

        verify(syncService, never()).handleRegisterDeletes(any());
        verify(syncService, never()).handleDeEnrolments(any(), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handlerFailure_isNotAckedSoTheContainerCanRetry() {
        List<AttendanceSyncEvent.DeletedRegister> registers =
                List.of(AttendanceSyncEvent.DeletedRegister.builder().id("r1").tenantId("mz").build());
        doThrow(new RuntimeException("boom")).when(syncService).handleRegisterDeletes(any());

        assertThrows(RuntimeException.class, () -> consumer.consume(
                AttendanceSyncEvent.builder().attendanceRegister(registers).build(), acknowledgment));

        verify(syncService).handleRegisterDeletes(registers);
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void registerDeleteTakesPrecedenceWhenAttendeesAlsoPresent() {
        List<AttendanceSyncEvent.DeletedRegister> registers =
                List.of(AttendanceSyncEvent.DeletedRegister.builder().id("r1").tenantId("mz").build());

        consumer.consume(AttendanceSyncEvent.builder()
                .attendanceRegister(registers)
                .attendees(List.of(AttendanceSyncEvent.Enrolment.builder().registerId("r1").build()))
                .build(), acknowledgment);

        verify(syncService).handleRegisterDeletes(eq(registers));
        verify(syncService, never()).handleDeEnrolments(any(), anyString());
    }
}
