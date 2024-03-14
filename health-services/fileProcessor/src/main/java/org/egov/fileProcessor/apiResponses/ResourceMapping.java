package org.egov.fileProcessor.apiResponses;

public class ResourceMapping {
    private String mappedFrom;
    private String mappedTo;
    private String filestoreId;

    // Getters and setters

    public String getMappedFrom() {
        return mappedFrom;
    }

    public void setMappedFrom(String mappedFrom) {
        this.mappedFrom = mappedFrom;
    }

    public String getMappedTo() {
        return mappedTo;
    }

    public void setMappedTo(String mappedTo) {
        this.mappedTo = mappedTo;
    }

    public String getFilestoreId() {
        return filestoreId;
    }

    public void setFilestoreId(String filestoreId) {
        this.filestoreId = filestoreId;
    }
}