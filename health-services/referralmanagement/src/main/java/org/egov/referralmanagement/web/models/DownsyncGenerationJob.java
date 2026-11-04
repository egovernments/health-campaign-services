package org.egov.referralmanagement.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownsyncGenerationJob {
    private String id;
    private String tenantId;
    private String projectId;
    private Integer totalRequested;
    private Integer totalSucceeded;
    private Integer totalFailed;
    private String status;
    private String createdBy;
    private Long createdTime;
    private String lastModifiedBy;
    private Long lastModifiedTime;
    private Long rowVersion;
    /** Updated periodically by the owning pod's heartbeat scheduler. Null until first claim. */
    private Long lastHeartbeat;
}
