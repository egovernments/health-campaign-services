package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SheetDataSearchCriteria {

    @JsonProperty("tenantId")
    @NotBlank(message = "SHEET_DATA_INVALID_TENANT")
    private String tenantId;

    @JsonProperty("referenceId")
    private String referenceId;

    @JsonProperty("fileStoreId")
    private String fileStoreId;

    @JsonProperty("sheetName")
    private String sheetName;

    @JsonProperty("limit")
    @Min(value = 1, message = "SHEET_DATA_INVALID_LIMIT")
    @Max(value = 1000, message = "SHEET_DATA_INVALID_LIMIT")
    private Integer limit;

    @JsonProperty("offset")
    @Min(value = 0, message = "SHEET_DATA_INVALID_OFFSET")
    private Integer offset;
}