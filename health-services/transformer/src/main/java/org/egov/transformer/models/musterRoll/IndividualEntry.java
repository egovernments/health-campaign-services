package org.egov.transformer.models.musterRoll;

import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IndividualEntry {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("individualId")
    private String individualId = null;

    @JsonProperty("actualTotalAttendance")
    private BigDecimal actualTotalAttendance = null;

    @JsonProperty("modifiedTotalAttendance")
    private BigDecimal modifiedTotalAttendance = null;

    @JsonProperty("attendanceEntries")
    @Valid
    private List<AttendanceEntry> attendanceEntries = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;


    public IndividualEntry addAttendanceEntriesItem(AttendanceEntry attendanceEntriesItem) {
        if (this.attendanceEntries == null) {
            this.attendanceEntries = new ArrayList<>();
        }
        this.attendanceEntries.add(attendanceEntriesItem);
        return this;
    }
}
