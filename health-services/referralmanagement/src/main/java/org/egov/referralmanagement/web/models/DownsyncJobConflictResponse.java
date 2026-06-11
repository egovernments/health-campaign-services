package org.egov.referralmanagement.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownsyncJobConflictResponse {

    private String code;
    private String message;
    private JobInfo currentJob;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobInfo {
        private String jobId;
        private String tenantId;
        private String projectId;
        private String status;
        private Long startedAt;
        private Integer totalLocalities;
        private Integer localitiesCompleted;
        private Integer localitiesPending;
    }
}
