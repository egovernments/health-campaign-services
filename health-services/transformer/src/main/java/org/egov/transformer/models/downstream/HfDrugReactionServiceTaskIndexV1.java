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
public class HfDrugReactionServiceTaskIndexV1 {
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
    @JsonProperty("evaluatedForSE")
    private Object evaluatedForSE;
    @JsonProperty("filledPharma")
    private Object filledPharma;
    @JsonProperty("adverseReactions")
    private Object adverseReactions;
    @JsonProperty("seriousAdverseEffects")
    private Object seriousAdverseEffects;
    @JsonProperty("seriousAdverseEffectsOutcome")
    private Object seriousAdverseEffectsOutcome;
    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;

}
