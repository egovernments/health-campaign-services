package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.egov.common.models.household.Household;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HouseholdIndexV1 extends ProjectInfo {
    @JsonProperty("household")
    private Household household;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("nameOfUser")
    private String nameOfUser;
    @JsonProperty("role")
    private String role;
    @JsonProperty("userAddress")
    private String userAddress;
    @JsonProperty("taskDates")
    private String taskDates;
    @JsonProperty("syncedDate")
    private String syncedDate;
    @JsonProperty("syncedTimeStamp")
    private String syncedTimeStamp;
    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;
    @JsonProperty("geoPoint")
    private List<Double> geoPoint;
    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;
}
