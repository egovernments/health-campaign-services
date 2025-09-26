package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.project.useraction.UserAction;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserActionIndexV1 {

    @JsonProperty("userAction")
    private UserAction userAction;

    @JsonProperty("id")
    private String id;

    @JsonProperty("projectId")
    private String projectId;

    @JsonProperty("projectType")
    private String projectType;

    @JsonProperty("projectTypeId")
    private String projectTypeId;

    @JsonProperty("additionalDetails")
    private JsonNode additionalDetails;

    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;

    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;

    @JsonProperty("geoPoint")
    private Double[] geoPoint;

    @JsonProperty("userName")
    private String userName;

    @JsonProperty("nameOfUser")
    private String nameOfUser;

    @JsonProperty("role")
    private String role;

    @JsonProperty("userAddress")
    private String userAddress;

    @JsonProperty("syncedTimeStamp")
    private String syncedTimeStamp;

    @JsonProperty("syncedTime")
    private Long syncedTime;

    @JsonProperty("taskDates")
    private String taskDates;

    @JsonProperty("syncedDate")
    private String syncedDate;

    @JsonProperty("userAssignedLowestBoundaryCount")
    private Long userAssignedLowestBoundaryCount;

}