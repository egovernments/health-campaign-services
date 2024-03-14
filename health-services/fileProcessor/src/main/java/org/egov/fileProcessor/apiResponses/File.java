package org.egov.fileProcessor.apiResponses;

public class File {
    private String filestoreId;
    private String inputFileType;
    private String templateIdentifier;

    // Getters and setters

    public String getFilestoreId() {
        return filestoreId;
    }

    public void setFilestoreId(String filestoreId) {
        this.filestoreId = filestoreId;
    }

    public String getInputFileType() {
        return inputFileType;
    }

    public void setInputFileType(String inputFileType) {
        this.inputFileType = inputFileType;
    }

    public String getTemplateIdentifier() {
        return templateIdentifier;
    }

    public void setTemplateIdentifier(String templateIdentifier) {
        this.templateIdentifier = templateIdentifier;
    }
}