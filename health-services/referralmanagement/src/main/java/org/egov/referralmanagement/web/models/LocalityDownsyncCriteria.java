package org.egov.referralmanagement.web.models;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalityDownsyncCriteria {
    @NotBlank
    private String locality;
    private String projectId;
    @NotBlank
    private String tenantId;
    private String localityRowId;   // set at job creation, used for DB audit updates
    private String category;        // REGISTRY | PROJECT
    private String rootProjectId;   // used for S3 key construction in PROJECT files
    private boolean forceRefresh;   // when true, bypass staleness check
}
