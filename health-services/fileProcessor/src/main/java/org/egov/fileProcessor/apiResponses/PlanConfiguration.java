package org.egov.fileProcessor.apiResponses;

public class PlanConfiguration {
    private String tenantId;
    private String name;
    private String executionPlanId;
    private File[] files;
    private Assumption[] assumptions;
    private Operation[] operations;
    private ResourceMapping[] resourceMapping;

    // Getters and setters

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExecutionPlanId() {
        return executionPlanId;
    }

    public void setExecutionPlanId(String executionPlanId) {
        this.executionPlanId = executionPlanId;
    }

    public File[] getFiles() {
        return files;
    }

    public void setFiles(File[] files) {
        this.files = files;
    }

    public Assumption[] getAssumptions() {
        return assumptions;
    }

    public void setAssumptions(Assumption[] assumptions) {
        this.assumptions = assumptions;
    }

    public Operation[] getOperations() {
        return operations;
    }

    public void setOperations(Operation[] operations) {
        this.operations = operations;
    }

    public ResourceMapping[] getResourceMapping() {
        return resourceMapping;
    }

    public void setResourceMapping(ResourceMapping[] resourceMapping) {
        this.resourceMapping = resourceMapping;
    }
}