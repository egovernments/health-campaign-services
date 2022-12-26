package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * A Object holds the basic data for a Trade License
 */
@ApiModel(description = "A Object holds the basic data for a Trade License")
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2022-12-02T17:32:25.406+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {
    @JsonProperty("id")
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 128)
    private String tenantId = null;

    @JsonProperty("documentType")
    @NotNull
    @Size(min = 2, max = 64)
    private String documentType = null;

    @JsonProperty("fileStoreId")
    @NotNull
    @Size(min = 2, max = 64)
    private String fileStoreId = null;

    @JsonProperty("documentUid")
    @Size(min = 2, max = 64)
    private String documentUid = null;

    @JsonProperty("auditDetails")
    @Valid
    private AuditDetails auditDetails = null;

}

