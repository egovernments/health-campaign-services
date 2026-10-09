package org.egov.excelingestion.web.models;

/**
 * Config class for processing - includes persistence options
 */
public class ProcessorSheetConfig {
    private final String sheetNameKey;
    private final String schemaName;
    private final String processorClass;
    private final boolean persistParsings;
    
    public ProcessorSheetConfig(String sheetNameKey, String schemaName) {
        this(sheetNameKey, schemaName, null, true);
    }
    
    public ProcessorSheetConfig(String sheetNameKey, String schemaName, String processorClass) {
        this(sheetNameKey, schemaName, processorClass, true);
    }
    
    public ProcessorSheetConfig(String sheetNameKey, String schemaName, String processorClass, 
                              boolean persistParsings) {
        this.sheetNameKey = sheetNameKey;
        this.schemaName = schemaName;
        this.processorClass = processorClass;
        this.persistParsings = persistParsings;
    }
    
    public String getSheetNameKey() {
        return sheetNameKey;
    }
    
    public String getSchemaName() {
        return schemaName;
    }
    
    public String getProcessorClass() {
        return processorClass;
    }
    
    public boolean isPersistParsings() {
        return persistParsings;
    }
}