package org.digit.health.sync.context.enums;

import org.digit.health.sync.context.step.SyncStep;

public enum SyncErrorCode {
    STEP_ALREADY_HANDLED("This step is already handled");


    String message;
    SyncErrorCode(String message) {
        this.message = message;
    }

    public String message(Class<? extends SyncStep> clazz) {
        return String.join(":", clazz.getName(), this.message);
    }
}
