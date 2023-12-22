package org.egov.transformer.models.downstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.household.HouseholdMember;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HouseholdMemberIndexV1  {
    @JsonProperty("householdMember")
    private HouseholdMember householdMember;
    @JsonProperty("age")
    private Integer age;
    @JsonProperty("dateOfBirth")
    private Long dateOfBirth;
    @JsonProperty("gender")
    private String gender;
}
