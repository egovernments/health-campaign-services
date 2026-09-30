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
    private String rootProjectId;   // campaign root; S3 key + campaign filter for PROJECT files
    private String beneficiaryType; // HOUSEHOLD | INDIVIDUAL (MDMS projectTypes.beneficiaryType), PROJECT rows only
    private boolean forceRefresh;   // when true, bypass staleness check
}
