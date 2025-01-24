package org.egov.common.models.individual;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;

/**
 * Identifier
 */
@Validated


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Identifier {

    @JsonProperty("id")

    @Size(min = 2, max = 64)

    private String id = null;

    @JsonProperty("clientReferenceId")
    @Size(min = 2, max = 64)
    private String clientReferenceId = null;

    @JsonProperty("individualId")

    @Size(min = 2, max = 64)

    private String individualId = null;


    @JsonProperty("identifierType")
    @NotNull


    @Size(min = 2, max = 64)

    private String identifierType = null;

    @JsonProperty("identifierId")
    @NotNull


    @Size(min = 2, max = 64)

    private String identifierId = null;

    @JsonProperty("isDeleted")

    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("auditDetails")

    @Valid


    private AuditDetails auditDetails = null;

}

