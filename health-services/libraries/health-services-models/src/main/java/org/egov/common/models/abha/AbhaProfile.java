package org.egov.common.models.abha;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class AbhaProfile {

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("middleName")
    private String middleName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("dob")
    private String dob;

    @JsonProperty("gender")
    private String gender;

    @JsonProperty("photo")
    private String photo;

    @JsonProperty("mobile")
    private String mobile;

    @JsonProperty("email")
    private String email;

    @JsonProperty("phrAddress")
    private String[] phrAddress;

    @JsonProperty("address")
    private String address;

    @JsonProperty("districtCode")
    private String districtCode;

    @JsonProperty("stateCode")
    private String stateCode;

    @JsonProperty("pinCode")
    private String pinCode;

    @JsonProperty("abhatype")
    private String abhatype;

    @JsonProperty("ABHANumber")
    private String abhaNumber;

    @JsonProperty("abhaStatus")
    private String abhaStatus;
}
