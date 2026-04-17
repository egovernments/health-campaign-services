package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.individual.Name;
import org.egov.transformer.models.attendance.AttendanceLog;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttendanceLogIndexV1 extends ProjectInfo {
    @JsonProperty("attendanceLog")
    private AttendanceLog attendanceLog;
    @JsonProperty("attendanceTakerUserName")
    private String attendanceTakerUserName;
    @JsonProperty("attendanceTakerNameOfUser")
    private String attendanceTakerNameOfUser;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("nameOfUser")
    private String nameOfUser;
    @JsonProperty("role")
    private String role;
    @JsonProperty("attendanceTime")
    private String attendanceTime;
    @JsonProperty("registerServiceCode")
    private String registerServiceCode;
    @JsonProperty("registerName")
    private String registerName;
    @JsonProperty("registerNumber")
    private String registerNumber;
    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;

}