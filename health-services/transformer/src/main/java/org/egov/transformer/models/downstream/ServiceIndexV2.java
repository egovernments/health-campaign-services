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
public class ServiceIndexV2 {
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
    @JsonProperty("projectId")
    private String projectId;
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("syncedTimeStamp")
    private String syncedTimeStamp;
    @JsonProperty("boundaryHierarchy")
    private ObjectNode boundaryHierarchy;
    @JsonProperty("childrenPresentedUS")
    private Object childrenPresentedUS;
    @JsonProperty("feverPositiveUS")
    private Object feverPositiveUS;
    @JsonProperty("feverNegativeUS")
    private Object feverNegativeUS;
    @JsonProperty("referredChildrenToAPE")
    private Object referredChildrenToAPE;
    @JsonProperty("referredChildrenPresentedToAPE")
    private Object referredChildrenPresentedToAPE;
    @JsonProperty("positiveMalariaAPE")
    private Object positiveMalariaAPE;
    @JsonProperty("negativeMalariaAPE")
    private Object negativeMalariaAPE;

}
