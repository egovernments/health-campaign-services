package org.egov.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.egov.common.contract.response.ResponseInfo;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;

/**
 * AttendanceRegisterResponse
 */
@Validated
@jakarta.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-11-14T14:44:21.051+05:30")

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttendanceRegisterResponse {
    @JsonProperty("ResponseInfo")
    private ResponseInfo responseInfo = null;

    @JsonProperty("attendanceRegister")
    @Valid
    private List<AttendanceRegister> attendanceRegister = null;


    public AttendanceRegisterResponse addAttendanceRegisterItem(AttendanceRegister attendanceRegisterItem) {
        if (this.attendanceRegister == null) {
            this.attendanceRegister = new ArrayList<>();
        }
        this.attendanceRegister.add(attendanceRegisterItem);
        return this;
    }

}
