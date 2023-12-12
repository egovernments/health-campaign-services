package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectIndexV1 {
    @JsonProperty("id")
    private String id;
    @JsonProperty("projectId")
    private String projectId;
    @JsonProperty("projectBeneficiaryType")
    private String projectBeneficiaryType;
    @JsonProperty("overallTarget")
    private Integer overallTarget;
    @JsonProperty("targetPerDay")
    private Integer targetPerDay;
    @JsonProperty("campaignDurationInDays")
    private Integer campaignDurationInDays;
    @JsonProperty("startDate")
    private Long startDate;
    @JsonProperty("endDate")
    private Long endDate;
    @JsonProperty("productVariant")
    private String productVariant;
    @JsonProperty("targetType")
    private String targetType;
    @JsonProperty("province")
    private String province;
    @JsonProperty("district")
    private String district;
    @JsonProperty("administrativeProvince")
    private String administrativeProvince;
    @JsonProperty("locality")
    private String locality;
    @JsonProperty("village")
    private String village;
    @JsonProperty("createdBy")
    private String createdBy = null;
    @JsonProperty("lastModifiedBy")
    private String lastModifiedBy = null;
    @JsonProperty("createdTime")
    private Long createdTime = null;
    @JsonProperty("lastModifiedTime")
    private Long lastModifiedTime = null;
}
