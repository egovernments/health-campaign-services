package org.digit.health.sync.web.models.dao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.digit.health.sync.web.models.AuditDetails;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SyncErrorDetailsLogData {

    @JsonProperty("syncErrorDetailsId")
    private String syncErrorDetailsId;

    @JsonProperty("syncId")
    private String syncId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("recordId")
    private String recordId;

    @JsonProperty("recordIdType")
    private String recordIdType;

    @JsonProperty("errorCodes")
    private String errorCodes;

    @JsonProperty("errorMessages")
    private String errorMessages;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;
}
