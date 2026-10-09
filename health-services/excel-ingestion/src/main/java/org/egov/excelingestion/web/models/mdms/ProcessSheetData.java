package org.egov.excelingestion.web.models.mdms;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessSheetData {
    
    @JsonProperty("sheetName")
    private String sheetName;
    
    @JsonProperty("schemaName")
    private String schemaName;
    
    @JsonProperty("parseEnabled")
    private Boolean parseEnabled;
    
    @JsonProperty("processorClass")
    private String processorClass;
}