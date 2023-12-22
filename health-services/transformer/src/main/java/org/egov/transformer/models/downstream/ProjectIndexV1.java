package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    @JsonProperty("boundaryHierarchy")
    private ObjectNode boundaryHierarchy;
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("projectType")
    private String projectType;
    @JsonProperty("subProjectType")
    private String subProjectType;
    @JsonProperty("createdBy")
    private String createdBy = null;
    @JsonProperty("lastModifiedBy")
    private String lastModifiedBy = null;
    @JsonProperty("createdTime")
    private Long createdTime = null;
    @JsonProperty("lastModifiedTime")
    private Long lastModifiedTime = null;
}
