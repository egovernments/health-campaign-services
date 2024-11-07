package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.transformer.models.upstream.AttributeValue;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceIndexV1 {
    @JsonProperty("id")
    private String id;
    @JsonProperty("createdTime")
    private Long createdTime;
    @JsonProperty("createdBy")
    private String createdBy;
    @JsonProperty("supervisorLevel")
    private String supervisorLevel;
    @JsonProperty("checklistName")
    private String checklistName;
    @JsonProperty("projectId")
    private String projectId;
    @JsonProperty("serviceDefinitionId")
    private String serviceDefinitionId;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("nameOfUser")
    private String nameOfUser;
    @JsonProperty("role")
    private String role;
    @JsonProperty("userAddress")
    private String userAddress;
    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("userId")
    private String userId;
    @JsonProperty("attributes")
    private List<AttributeValue> attributes = new ArrayList<>();
    @JsonProperty("clientReferenceId")
    private String clientReferenceId;
    @JsonProperty("syncedTimeStamp")
    private String syncedTimeStamp;
    @JsonProperty("syncedTime")
    private Long syncedTime;
    @JsonProperty("taskDates")
    private String taskDates;
    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;
    @JsonProperty("geoPoint")
    private List<Double> geoPoint;
    @JsonProperty("projectType")
    private String projectType;
}
