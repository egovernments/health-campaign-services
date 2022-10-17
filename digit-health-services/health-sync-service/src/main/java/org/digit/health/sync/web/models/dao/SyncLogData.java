package org.digit.health.sync.web.models.dao;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.digit.health.sync.web.models.SyncLogStatus;
import org.digit.health.sync.web.models.AuditDetails;
import org.digit.health.sync.web.models.FileDetails;
import org.digit.health.sync.web.models.ReferenceId;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncLogData {

    @JsonProperty("syncId")
    private String syncId;

    @JsonProperty("referenceId")
    private ReferenceId referenceId;

    @JsonProperty("tenantId")
    private String tenantId;

    @JsonProperty("fileDetails")
    private FileDetails fileDetails;

    @JsonProperty("status")
    private SyncLogStatus status;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("totalCount")
    private Long totalCount;

    @JsonProperty("successCount")
    private Long successCount;

    @JsonProperty("errorCount")
    private Long errorCount;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;

}
