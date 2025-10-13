package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SheetDataDeleteRequest {

    @JsonProperty("RequestInfo")
    @NotNull(message = "REQUEST_INFO_MANDATORY")
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("tenantId")
    @NotBlank(message = "SHEET_DATA_INVALID_TENANT")
    private String tenantId;

    @JsonProperty("referenceId")
    @NotBlank(message = "SHEET_DATA_DELETE_MISSING_PARAMS")
    private String referenceId;

    @JsonProperty("fileStoreId")
    @NotBlank(message = "SHEET_DATA_DELETE_MISSING_PARAMS")
    private String fileStoreId;
}