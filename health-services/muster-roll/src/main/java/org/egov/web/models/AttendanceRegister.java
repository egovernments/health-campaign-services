package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.egov.common.contract.models.AuditDetails;
import lombok.*;
import org.egov.works.services.common.models.attendance.StaffPermission;
import org.egov.works.services.common.models.musterroll.Status;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * AttendanceRegister
 */
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-11-14T14:44:21.051+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttendanceRegister {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("registerNumber")
    private String registerNumber = null;

    @JsonProperty("name")
    private String name = null;

    @JsonProperty("startDate")
    private BigDecimal startDate = null;

    @JsonProperty("endDate")
    private BigDecimal endDate = null;

    @JsonProperty("status")
    private Status status = Status.ACTIVE;

    @JsonProperty("staff")
    @Valid
    private List<StaffPermission> staff = null;

    @JsonProperty("attendees")
    @Valid
    private List<IndividualEntry> attendees = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;


    public AttendanceRegister addStaffItem(StaffPermission staffItem) {
        if (this.staff == null) {
            this.staff = new ArrayList<>();
        }
        this.staff.add(staffItem);
        return this;
    }

    public AttendanceRegister addAttendeesItem(IndividualEntry attendeesItem) {
        if (this.attendees == null) {
            this.attendees = new ArrayList<>();
        }
        this.attendees.add(attendeesItem);
        return this;
    }

}
