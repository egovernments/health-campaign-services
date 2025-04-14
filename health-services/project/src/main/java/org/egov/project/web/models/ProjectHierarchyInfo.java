package org.egov.project.web.models;

// Simple DTO for hierarchy results
public class ProjectHierarchyInfo {
    private String id;
    private String hierarchy;

    public ProjectHierarchyInfo(String id, String hierarchy) {
        this.id = id;
        this.hierarchy = hierarchy;
    }

    public String getId() {
        return id;
    }

    public String getHierarchy() {
        return hierarchy;
    }
}
