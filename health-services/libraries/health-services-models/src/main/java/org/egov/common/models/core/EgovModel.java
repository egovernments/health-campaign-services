package org.egov.common.models.core;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.contract.models.AuditDetails;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EgovModel {

    @JsonProperty("id")
    @Size(min = 2, max = 64)
    // Same hole clientReferenceId had: @Size alone accepts a whitespace-only value. @Pattern skips
    // nulls, so a create that carries no id yet is unaffected.
    @Pattern(regexp = ".*\\S.*", message = "id must contain a non-whitespace character")
    protected String id;

    @JsonProperty("tenantId")
    @NotBlank
    @Size(min = 2, max = 1000)
    protected String tenantId;

    @JsonProperty("source")
    protected String source;  //TODO what are the various sources and needs comments

    @JsonProperty("rowVersion")
    protected Integer rowVersion;

    @JsonProperty("applicationId") //needs comments
    protected String applicationId;

    @JsonProperty("hasErrors")
    @Builder.Default
    protected Boolean hasErrors = Boolean.FALSE; //TODO is this health specific or will this become general.

    @JsonProperty("additionalFields")
    @Valid
    protected AdditionalFields additionalFields;

    @JsonProperty("auditDetails")
    @Valid
    protected AuditDetails auditDetails;

}
