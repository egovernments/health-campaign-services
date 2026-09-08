package org.egov.excelingestion.web.models.mdms;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelIngestionProcessData {
    
    @JsonProperty("sheets")
    private List<ProcessSheetData> sheets;
    
    @JsonProperty("processingResultTopic")
    private String processingResultTopic;
    
    @JsonProperty("excelIngestionProcessName")
    private String excelIngestionProcessName;
}