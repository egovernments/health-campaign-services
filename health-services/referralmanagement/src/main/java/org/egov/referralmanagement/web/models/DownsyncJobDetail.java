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
public class DownsyncJobDetail {
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
    private List<DownsyncLocalityDetail> localities;
}
