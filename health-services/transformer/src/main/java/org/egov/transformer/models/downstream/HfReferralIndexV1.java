package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HfReferralIndexV1 {
    @JsonProperty("hfReferral")
    private HFReferral hfReferral;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("role")
    private String role;
    @JsonProperty("userAddress")
    private String userAddress;
    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;
    @JsonProperty("taskDates")
    private String taskDates;
    @JsonProperty("syncedDate")
    private String syncedDate;
    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;
}