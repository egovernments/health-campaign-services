package org.egov.referralmanagement.web.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownsyncLocalityDetail {
    private String id;
    private String locality;
    private String tenantId;
    private String projectId;
    private String category;
    private String status;
    private String failureReason;
    private Long startTime;
    private Long endTime;
    private Long createdTime;
    private List<DownsyncLocalityFile> files;
}
