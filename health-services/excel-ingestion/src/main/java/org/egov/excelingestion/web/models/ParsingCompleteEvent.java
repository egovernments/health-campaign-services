package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Event model for parsing completion notifications
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ParsingCompleteEvent {

    @JsonProperty("filestoreId")
    private String filestoreId;

    @JsonProperty("referenceId") 
    private String referenceId;

    @JsonProperty("sheetName")
    private String sheetName;

    @JsonProperty("recordCount")
    private Integer recordCount;
}