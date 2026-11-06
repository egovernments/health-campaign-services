package org.egov.excelingestion.web.models.mdms;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.egov.excelingestion.web.models.SheetGenerationConfig;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelIngestionGenerateData {

    @JsonProperty("sheets")
    private List<SheetGenerationConfig> sheets;

    @JsonProperty("applyWorkbookProtection")
    private Boolean applyWorkbookProtection;

    @JsonProperty("excelIngestionGenerateName")
    private String excelIngestionGenerateName;

    /**
     * Maps each sheet name key (e.g. "HCM_REGISTER_WORKER_SHEET") to the list of
     * role codes that belong to that sheet.  Replaces the previous hardcoded
     * WORKER_ROLES / MARKER_ROLES / APPROVER_ROLES constants.
     */
    @JsonProperty("attendanceRoleConfig")
    private Map<String, List<String>> attendanceRoleConfig;

    /**
     * Role codes whose users are allowed to be actively enrolled in more than one
     * attendance register simultaneously.  Empty list (default) means no role is exempt
     * from the cross-register enrollment block.
     */
    @JsonProperty("multiRegisterAllowedRoles")
    private List<String> multiRegisterAllowedRoles;

    public Map<String, List<String>> getAttendanceRoleConfig() {
        return attendanceRoleConfig != null ? attendanceRoleConfig : Collections.emptyMap();
    }

    public List<String> getMultiRegisterAllowedRoles() {
        return multiRegisterAllowedRoles != null ? multiRegisterAllowedRoles : Collections.emptyList();
    }
}