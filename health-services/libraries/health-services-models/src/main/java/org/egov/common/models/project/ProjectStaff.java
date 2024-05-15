package org.egov.common.models.project;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import digit.models.coremodels.AuditDetails;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
* This object defines the mapping of a system staff user to a project for a certain period.
*/
@ApiModel(description = "This object defines the mapping of a system staff user to a project for a certain period.")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectStaff {

    @JsonProperty("id")
    @Size(min=2,max=64)
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min=2,max=1000)
    private String tenantId = null;

    @JsonProperty("userId")
    @NotNull
    @Size(min=2,max=64)
    private String userId = null;

    @JsonProperty("projectId")
    @NotNull
    @Size(min=2,max=64)
    private String projectId = null;

    @JsonProperty("startDate")
    private Long startDate = null;

    @JsonProperty("endDate")
    private Long endDate = null;

    @JsonProperty("channel")
    @Size(min=2,max=64)
    private String channel = null;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @JsonProperty("rowVersion")
    private Integer rowVersion = null;

    @JsonProperty("additionalFields")
    @Valid
    private AdditionalFields additionalFields = null;

    @JsonIgnore
    private Boolean hasErrors = Boolean.FALSE;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

}

