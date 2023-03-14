package org.egov.common.models.stock;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.Size;

/**
 * Facility
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-21T14:37:54.683+05:30")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Facility {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("isPermanent")
    private Boolean isPermanent = true;

    @JsonProperty("name")
    @Size(min = 2, max = 2000)
    private String name = null;

    @JsonProperty("usage")
    private String usage = null;

    @JsonProperty("storageCapacity")
    private Integer storageCapacity = null;

    @JsonProperty("address")
    @Valid
    private Address address = null;

    @JsonProperty("additionalFields")
    @Valid
    private AdditionalFields additionalFields = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("rowVersion")
    private Integer rowVersion = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

    @JsonIgnore
    private Boolean hasErrors = Boolean.FALSE;


}

