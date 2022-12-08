package org.egov.project.repository;

public enum RepositoryErrorCode {
    SAVE_ERROR("Error during save operation");

    String message;
    RepositoryErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return this.message;
    }

    public String message(String errorMessage) {
        return String.join(":", message, errorMessage);
    }
}