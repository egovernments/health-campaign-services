package org.egov.excelingestion.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ValidationError {
    
    @JsonProperty("rowNumber")
    private Integer rowNumber;
    
    @JsonProperty("sheetName")
    private String sheetName;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("errorDetails")
    private String errorDetails;
    
    @JsonProperty("columnName")
    private String columnName;
}