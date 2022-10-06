package org.digit.health.sync.context;

public enum SyncErrorCode {
    STEP_ALREADY_HANDLED("This step is already handled"),
    INVALID_JSON_FILE("Invalid message"),
    INVALID_FILE("Invalid File"),
    UNABLE_TO_PROCESS("Unable to process");


    String message;
    SyncErrorCode(String message) {
        this.message = message;
    }

    public String message(String message) {
        return String.join(" : ",this.message, message );
    }

    public String message(Class<? extends SyncStep> clazz) {
        return String.join(":", clazz.getName(), this.message);
    }
}
