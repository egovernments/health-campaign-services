package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SheetDataSearchRequest {

    @JsonProperty("RequestInfo")
    @NotNull(message = "REQUEST_INFO_MANDATORY")
    @Valid
    private RequestInfo requestInfo;

    @JsonProperty("SheetDataSearchCriteria")
    @NotNull(message = "SEARCH_CRITERIA_MANDATORY")
    @Valid
    private SheetDataSearchCriteria sheetDataSearchCriteria;
}