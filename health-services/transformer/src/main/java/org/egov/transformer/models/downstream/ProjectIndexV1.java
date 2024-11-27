package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectIndexV1 extends ProjectInfo {
    @JsonProperty("id")
    private String id;
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
    @JsonProperty("productName")
    private String productName;
    @JsonProperty("targetType")
    private String targetType;
    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("taskDates")
    private List<String> taskDates;
    @JsonProperty("subProjectType")
    private String subProjectType;
    @JsonProperty("localityCode")
    private String localityCode;
    @JsonProperty("createdBy")
    private String createdBy = null;
    @JsonProperty("createdTime")
    private Long createdTime = null;
    @JsonProperty("additionalDetails")
    private JsonNode additionalDetails;
}
