package org.egov.referralmanagement.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownsyncLocalityFile {
    private String id;
    private String localityRowId;
    private String jobId;
    private String fileType;    // HH_MEMBERS | INDIVIDUALS | BENE_AE_REF | TASKS
    private String status;      // PENDING | IN_PROGRESS | SUCCESS | FAILED | SKIPPED
    private String s3Key;
    private Long recordCount;
    private Long fileSize;
    private String failureReason;
    private Long startTime;
    private Long endTime;
}
