package org.egov.transformer.models.musterRoll;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import lombok.*;
import org.egov.tracer.model.AuditDetails;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * IndividualEntry
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-11-14T19:58:09.415+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class IndividualEntry {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("individualId")
    private String individualId = null;

    @JsonProperty("musterRollId")
    private String musterRollId = null;

    @JsonProperty("enrollmentDate")
    private BigDecimal enrollmentDate = null;

    @JsonProperty("denrollmentDate")
    private BigDecimal denrollmentDate = null;

    @JsonProperty("actualTotalAttendance")
    @Min(0)
    private BigDecimal actualTotalAttendance = null;

    @JsonProperty("totalRegistrations")
    @Min(0)
    private Long totalRegistrations = null;

    @JsonProperty("totalInterventions")
    @Min(0)
    private Long totalInterventions = null;

    @JsonProperty("modifiedTotalAttendance")
    @Min(0)
    private BigDecimal modifiedTotalAttendance = null;

    @JsonProperty("attendanceEntries")
    @Valid
    private List<AttendanceEntry> attendanceEntries = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

    @Size(max=64)
    @JsonProperty("tag")
    private String tag = null; // represent teamcode to group attendees

    @Size(max=128)
    @JsonProperty("role")
    private String role = null; // individual's skill type (e.g., REGISTRAR, DISTRIBUTOR)


    public IndividualEntry addAttendanceEntriesItem(AttendanceEntry attendanceEntriesItem) {
        if (this.attendanceEntries == null) {
            this.attendanceEntries = new ArrayList<>();
        }
        this.attendanceEntries.add(attendanceEntriesItem);
        return this;
    }

}

