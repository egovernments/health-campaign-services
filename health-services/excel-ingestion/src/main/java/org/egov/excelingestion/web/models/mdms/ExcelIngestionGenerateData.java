package org.egov.excelingestion.web.models.mdms;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import org.egov.excelingestion.web.models.SheetGenerationConfig;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelIngestionGenerateData {
    
    @JsonProperty("sheets")
    private List<SheetGenerationConfig> sheets;
    
    @JsonProperty("applyWorkbookProtection")
    private Boolean applyWorkbookProtection;
    
    @JsonProperty("excelIngestionGenerateName")
    private String excelIngestionGenerateName;
}