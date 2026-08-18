package org.egov.excelingestion.consumer;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.service.AttendanceTemplateSyncService;
import org.egov.excelingestion.web.models.AttendanceSyncEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Listens on the attendance service's own topics so a deletion or de-enrolment there invalidates
 * the templates generated here. Reading the events directly keeps each service responsible for its
 * own store — project-factory updates its campaign data from the same topics, and neither has to
 * call the other.
 *
 * A failure is NOT acked: nothing else re-invalidates the template, so a swallowed error would keep
 * serving a sheet with a deleted register in it. The container retries with backoff and gives up
 * after the configured attempts, which commits the offset — a bad event cannot block the partition.
 */
@Component
@Slf4j
public class AttendanceSyncConsumer {

    private static final String ATTENDEE_LABEL = "attendee";
    private static final String STAFF_LABEL = "staff";

    private final AttendanceTemplateSyncService attendanceTemplateSyncService;

    public AttendanceSyncConsumer(AttendanceTemplateSyncService attendanceTemplateSyncService) {
        this.attendanceTemplateSyncService = attendanceTemplateSyncService;
    }

    @KafkaListener(
            topicPattern = "(${kafka.tenant.id.pattern}){0,1}("
                    + "${excel.ingestion.attendance.register.delete.topic}|"
                    + "${excel.ingestion.attendance.attendee.update.topic}|"
                    + "${excel.ingestion.attendance.staff.update.topic})",
            containerFactory = "attendanceSyncListenerContainerFactory")
    public void consume(AttendanceSyncEvent event, Acknowledgment acknowledgment) {
        if (event == null) {
            // Null means the payload failed to deserialize (ErrorHandlingDeserializer) - retrying it
            // would fail identically, so ack and move on.
            log.warn("Received unreadable attendance sync event; acking and skipping");
            acknowledgment.acknowledge();
            return;
        }

        // The three topics are disjoint in shape, so the populated collection identifies the event.
        if (event.getAttendanceRegister() != null) {
            attendanceTemplateSyncService.handleRegisterDeletes(event.getAttendanceRegister());
        } else if (event.getAttendees() != null) {
            attendanceTemplateSyncService.handleDeEnrolments(event.getAttendees(), ATTENDEE_LABEL);
        } else if (event.getStaff() != null) {
            attendanceTemplateSyncService.handleDeEnrolments(event.getStaff(), STAFF_LABEL);
        } else {
            log.warn("Attendance sync event carried no register/attendee/staff entries; skipping");
        }

        acknowledgment.acknowledge();
    }
}
