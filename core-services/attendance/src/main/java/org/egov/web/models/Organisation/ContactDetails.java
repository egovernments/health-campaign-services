package org.egov.web.models.Organisation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.models.core.Role;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * Captures details of a contact person
 */
@ApiModel(description = "Captures details of a contact person")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-15T14:49:42.141+05:30")

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContactDetails {

    @JsonProperty("id")
    @Valid
    private String id = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("individualId")
    private String individualId = null;

    @JsonProperty("orgId")
    private String orgId = null;

    @JsonProperty("contactName")
    @Size(min = 2, max = 64)
    private String contactName = null;

    @JsonProperty("contactMobileNumber")
    @Size(max = 20)
    private String contactMobileNumber = null;

    @JsonProperty("contactEmail")
    @Size(min = 5, max = 200)
    private String contactEmail = null;

    @JsonProperty("active")
    private Boolean active;

    @JsonProperty("roles")
    @Valid
    private List<Role> roles;

    @Size(max=50)
    @JsonProperty("type")
    private String type;

    @Size(max=64)
    //@DiffIgnore
    @JsonProperty("createdBy")
    private String createdBy;

    //@DiffIgnore
    @JsonProperty("createdDate")
    private Long createdDate;

    @Size(max=64)
    //@DiffIgnore
    @JsonProperty("lastModifiedBy")
    private String lastModifiedBy;

    //@DiffIgnore
    @JsonProperty("lastModifiedDate")
    private Long lastModifiedDate;

}
