package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.referralmanagement.Referral;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferralIndexV1 extends ProjectInfo {
    @JsonProperty("referral")
    private Referral referral;
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("dateOfBirth")
    private Long dateOfBirth;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("nameOfUser")
    private String nameOfUser;
    @JsonProperty("role")
    private String role;
    @JsonProperty("userAddress")
    private String userAddress;
    @JsonProperty("age")
    private Integer age;
    @JsonProperty("boundaryHierarchy")
    private Map<String, String> boundaryHierarchy;
    @JsonProperty("boundaryHierarchyCode")
    private Map<String, String> boundaryHierarchyCode;
    @JsonProperty("facilityName")
    private String facilityName;
    @JsonProperty("individualId")
    private String individualId;
    @JsonProperty("gender")
    private String gender;
    @JsonProperty("taskDates")
    private String taskDates;
    @JsonProperty("syncedDate")
    private String syncedDate;
    @JsonProperty("additionalDetails")
    private ObjectNode additionalDetails;
}