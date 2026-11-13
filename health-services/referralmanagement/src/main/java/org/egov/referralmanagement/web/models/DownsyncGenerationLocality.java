package org.egov.referralmanagement.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownsyncGenerationLocality {
    private String id;
    private String jobId;
    private String tenantId;
    private String projectId;
    private String locality;
    private String category;    // REGISTRY | PROJECT
    private String status;      // PENDING | IN_PROGRESS | SUCCESS | PARTIAL_SUCCESS | FAILED | SKIPPED
    private String failureReason;
    private Long startTime;
    private Long endTime;
    private Long createdTime;
}
