package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import org.egov.common.models.referralmanagement.Referral;

import java.util.List;

@Builder
public class ReferralIndexV1 {
    @JsonProperty("referral")
    private Referral referral;
    @JsonProperty("tenantId")
    private String tenantId;
    @JsonProperty("dateOfBirth")
    private Long dateOfBirth;
    @JsonProperty("userName")
    private String userName;
    @JsonProperty("role")
    private String role;
    @JsonProperty("age")
    private Integer age;
    @JsonProperty("facilityName")
    private String facilityName;
    @JsonProperty("individualId")
    private String individualId;
    @JsonProperty("gender")
    private String gender;
    @JsonProperty("reasons")
    private List<String> reasons;
    @JsonProperty("clientLastModifiedTime")
    private Long clientLastModifiedTime;
}