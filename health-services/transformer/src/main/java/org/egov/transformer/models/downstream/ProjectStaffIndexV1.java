package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectStaffIndexV1 {
    @JsonProperty("id")
    private String id;
    @JsonProperty("userId")
    private String userId;
    @JsonProperty("projectId")
    private String projectId;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("role")
    private String role;
    @JsonProperty("taskDates")
    private List<String> taskDates;
    @JsonProperty("boundaryHierarchy")
    private ObjectNode boundaryHierarchy;
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("projectType")
    private String projectType;
    @JsonProperty("createdBy")
    private String createdBy;
    @JsonProperty("lastModifiedBy")
    private String lastModifiedBy;
    @JsonProperty("createdTime")
    private Long createdTime;
    @JsonProperty("lastModifiedTime")
    private Long lastModifiedTime;

}
