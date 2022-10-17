package org.digit.health.registration.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Individual {
    @JsonProperty("individualId")
    private String individualId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("givenName")
    private String givenName;

    @JsonProperty("familyName")
    private String familyName;

    @JsonProperty("additionalName")
    private String additionalName;

    @JsonProperty("dateOfBirth")
    private String dateOfBirth;

    @JsonProperty("gender")
    private Gender gender;

    @JsonProperty("isHead")
    private boolean isHead;

    @JsonProperty("identifiers")
    private List<Identifier> identifiers;

    @JsonProperty("addressId")
    private String addressId;

    @JsonProperty("additionalFields")
    private String additionalFields;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails;
}
