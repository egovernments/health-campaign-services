package org.egov.household.web.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
* A representation of an Individual.
*/
    @ApiModel(description = "A representation of an Individual.")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-27T11:47:19.561+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Individual {

    @JsonProperty("id")
    @Size(min=2,max=64)
    private String id = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("clientReferenceId")
    private String clientReferenceId = null;

    @JsonProperty("userId")
    private String userId = null;

    @JsonProperty("name")
    @NotNull
    @Valid
    private Name name = null;

    @JsonProperty("dateOfBirth")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy")
    private LocalDate dateOfBirth = null;

    @JsonProperty("gender")
    @Valid
    private Gender gender = null;

    @JsonProperty("bloodGroup")
    @Size(max=3)
    private String bloodGroup = null;

    @JsonProperty("mobileNumber")
    @Size(max=20)
    private String mobileNumber = null;

    @JsonProperty("altContactNumber")
    @Size(max=16)
    private String altContactNumber = null;

    @JsonProperty("email")
    @Size(min=5,max=200)
    private String email = null;

    @JsonProperty("address")
    @Valid
    @Size(min=1)
    private List<Address> address = null;

    @JsonProperty("fatherName")
    @Size(max=100)
    private String fatherName = null;

    @JsonProperty("husbandName")
    @Size(max=100)
    private String husbandName = null;

    @JsonProperty("identifiers")
    @Valid
    private List<Identifier> identifiers = null;

    @JsonProperty("photo")
    private String photo = null;

    @JsonProperty("additionalFields")
    @Valid
    private AdditionalFields additionalFields = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = null;

    @JsonProperty("rowVersion")
    private Integer rowVersion = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;


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

}

