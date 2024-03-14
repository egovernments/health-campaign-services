package org.egov.fileProcessor.apiResponses;

public class PlanConfigurationResponse {
    private RequestInfo requestInfo;
    private PlanConfiguration planConfiguration;

    // Getters and setters

    public RequestInfo getRequestInfo() {
        return requestInfo;
    }

    public void setRequestInfo(RequestInfo requestInfo) {
        this.requestInfo = requestInfo;
    }

    public PlanConfiguration getPlanConfiguration() {
        return planConfiguration;
    }

    public void setPlanConfiguration(PlanConfiguration planConfiguration) {
        this.planConfiguration = planConfiguration;
    }
}
