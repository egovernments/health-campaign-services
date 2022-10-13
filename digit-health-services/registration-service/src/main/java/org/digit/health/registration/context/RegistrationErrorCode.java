package org.digit.health.registration.context;

public enum RegistrationErrorCode {
    UNABLE_TO_PROCESS("Unable to process");

    String message;
    RegistrationErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return this.message;
    }

}
