package org.egov.fileProcessor.apiResponses;

public class Operation {
    private String input;
    private String operator;
    private String assumptionValue;
    private String output;

    // Getters and setters

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getAssumptionValue() {
        return assumptionValue;
    }

    public void setAssumptionValue(String assumptionValue) {
        this.assumptionValue = assumptionValue;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }
}