package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.individual.Name;
import org.egov.transformer.models.attendance.AttendanceRegister;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttendanceRegisterIndexV1 {
    @JsonProperty("attendanceRegister")
    private AttendanceRegister attendanceRegister;
    @JsonProperty("attendeesInfo")
    private Map<String, Map<String, String>> attendeesInfo;
    @JsonProperty("staffsInfo")
    private Map<String, Map<String, String>> staffsInfo;
    @JsonProperty("staffsCount")
    private Long staffsCount;
    @JsonProperty("attendeesCount")
    private Long attendeesCount;
    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;
    @JsonProperty("transformerTimeStamp")
    private String transformerTimeStamp;
}