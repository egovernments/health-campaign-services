package org.egov.common.models.individual;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;
import org.springframework.validation.annotation.Validated;

/**
 * A representation of an Individual.
 */
@ApiModel(description = "A representation of an Individual.")
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Individual extends EgovOfflineModel {

    @JsonProperty("individualId")
    @Size(min = 2, max = 64)
    private String individualId = null;

    @JsonProperty("userId")
    private String userId = null;

    @JsonProperty("userUuid")
    private String userUuid = null;

    @JsonProperty("name")
    @NotNull @Valid
    private Name name;

    @JsonProperty("dateOfBirth")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy") // keep; convert from ABHA "dd-MM-yyyy" before set
    private Date dateOfBirth;

    @JsonProperty("gender")
    @Valid
    private Gender gender;

    @JsonProperty("bloodGroup")
    @Valid
    private BloodGroup bloodGroup;

    @JsonProperty("mobileNumber")
    @Size(max = 20)                    // keep len; do NOT force 10 digits here (ABHA is India, but HCM may be multi-tenant)
    private String mobileNumber;

    @JsonProperty("altContactNumber")
    @Size(max = 20)                    // was 16 → align with mobileNumber cap
    private String altContactNumber;

    @JsonProperty("email")
    @Size(min = 3, max = 200)         // was 5 → allow short aliases if needed; still optional
    private String email;

    @JsonProperty("address")
    @Valid
    @Size(max = 3)
    private List<Address> address;

    @JsonProperty("fatherName")
    @Size(max = 100)
    private String fatherName;

    @JsonProperty("husbandName")
    @Size(max = 100)
    private String husbandName;

    @JsonProperty("relationship")
    @Size(max = 100, min = 1)
    private String relationship;

    @JsonProperty("identifiers")
    @Valid
    private List<Identifier> identifiers;

    @JsonProperty("skills")
    @Valid
    private List<Skill> skills;

    @JsonProperty("photo")
    private String photo;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("isSystemUser")
    private Boolean isSystemUser = Boolean.FALSE;

    @JsonProperty("isSystemUserActive")
    private Boolean isSystemUserActive = Boolean.TRUE;

    @JsonProperty("userDetails")
    private UserDetails userDetails;

    public Individual addAddressItem(Address addressItem) {
        if (this.address == null) {
            this.address = new ArrayList<>();
        }
        this.address.add(addressItem);
        return this;
    }

    public Individual addIdentifiersItem(Identifier identifiersItem) {
        if (this.identifiers == null) {
            this.identifiers = new ArrayList<>();
        }
        this.identifiers.add(identifiersItem);
        return this;
    }

    public Individual addSkillsItem(Skill skillItem) {
        if (this.skills == null) {
            this.skills = new ArrayList<>();
        }
        this.skills.add(skillItem);
        return this;
    }
}

