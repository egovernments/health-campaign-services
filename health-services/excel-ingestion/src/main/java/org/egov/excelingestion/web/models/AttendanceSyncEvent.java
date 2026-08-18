package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Payload of the attendance-service topics this service listens to. One class covers all three
 * (register delete, attendee update, staff update): the events are produced by the attendance
 * service and only one of the three collections is ever populated on a given message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceSyncEvent {

    @JsonProperty("attendanceRegister")
    private List<DeletedRegister> attendanceRegister;

    @JsonProperty("attendees")
    private List<Enrolment> attendees;

    @JsonProperty("staff")
    private List<Enrolment> staff;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeletedRegister {
        @JsonProperty("id")
        private String id;

        @JsonProperty("tenantId")
        private String tenantId;
    }

    /** An attendee or staff enrolment. denrollmentDate is null on tag edits, which share the topic. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Enrolment {
        @JsonProperty("registerId")
        private String registerId;

        @JsonProperty("tenantId")
        private String tenantId;

        @JsonProperty("denrollmentDate")
        private Long denrollmentDate;
    }
}
