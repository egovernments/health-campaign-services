package org.egov.excelingestion.web.models.excel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnDef {
    private String name;
    private String technicalName;
    private String type;
    private String description;
    private String colorHex;
    private int orderNumber;
    private boolean freezeColumnIfFilled;
    private boolean hideColumn;
    private boolean required;
    private List<String> enumValues;
    private MultiSelectDetails multiSelectDetails;
    private String parentColumn;
    private int multiSelectIndex;
    private int multiSelectMaxSelections;
    
    // New display and styling properties
    private Integer width;
    private boolean wrapText;
    private String prefix;
    private boolean adjustHeight;
    private boolean showInProcessed;
    private boolean freezeColumn;
    private boolean freezeTillData;
    private boolean unFreezeColumnTillData;
    
    // Validation properties for MDMS fields
    private Number minimum;
    private Number maximum;
    private String errorMessage; // Custom error message from MDMS schema
    
    // String validation properties
    private Integer minLength;
    private Integer maxLength;
    private String pattern; // Regex pattern for validation
    
    // Additional number validation properties
    private Number multipleOf;
    private Number exclusiveMinimum;
    private Number exclusiveMaximum;
}