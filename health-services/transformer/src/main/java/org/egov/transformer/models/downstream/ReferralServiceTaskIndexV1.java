package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferralServiceTaskIndexV1 {
    @JsonProperty("id")
    private String id;
    @JsonProperty("supervisorLevel")
    private String supervisorLevel;
    @JsonProperty("checklistName")
    private String checklistName;
    @JsonProperty("ageGroup")
    private String ageGroup;
    @JsonProperty("createdTime")
    private Long createdTime;
    @JsonProperty("createdBy")
    private String createdBy;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("role")
    private String role;
    @JsonProperty("userAddress")
    private String userAddress;
    @JsonProperty("projectId")
    private String projectId;
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("syncedTime")
    private Long syncedTime;
    @JsonProperty("taskDates")
    private String taskDates;
    @JsonProperty("syncedTimeStamp")
    private String syncedTimeStamp;
    @JsonProperty("boundaryHierarchy")
    private ObjectNode boundaryHierarchy;
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;
    @JsonProperty("childrenPresentedUS")
    private Object childrenPresentedUS;
    @JsonProperty("malariaPositiveUS")
    private Object malariaPositiveUS;
    @JsonProperty("malariaNegativeUS")
    private Object malariaNegativeUS;
    @JsonProperty("childrenPresentedAPE")
    private Object childrenPresentedAPE;
    @JsonProperty("malariaPositiveAPE")
    private Object malariaPositiveAPE;
    @JsonProperty("malariaNegativeAPE")
    private Object malariaNegativeAPE;
    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;

}
