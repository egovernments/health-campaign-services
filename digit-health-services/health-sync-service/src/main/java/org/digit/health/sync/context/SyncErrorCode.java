package org.digit.health.sync.context;

public enum SyncErrorCode {
    STEP_ALREADY_HANDLED("This step is already handled"),
    INVALID_JSON_FILE("Invalid message"),
    INVALID_FILE("Invalid File"),

    INVALID_CHECKSUM("Checksum did not match the checksum received"),

    INVALID_CHECKSUM_ALGORITHM("Checksum algorithm is invalid"),
    UNABLE_TO_PROCESS("Unable to process");


    String message;
    SyncErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return this.message;
    }

    public String message(Class<? extends SyncStep> clazz) {
        return String.join(":", clazz.getName(), this.message);
    }
}
