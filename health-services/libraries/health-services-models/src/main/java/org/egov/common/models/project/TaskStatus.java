package org.egov.common.models.project;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing the various possible statuses for a task.
 * <p>
 * Each status corresponds to a specific state of the task in the system.
 * The status is stored as a string value and can be serialized/deserialized
 * from JSON using Jackson annotations.
 * </p>
 */
public enum TaskStatus {

    /**
     * Indicates that the task administration has failed.
     * This status represents an error or issue encountered during
     * the administrative process of the task.
     */
    ADMINISTRATION_FAILED("ADMINISTRATION_FAILED"),

    /**
     * Indicates that the task administration was successful.
     * This status signifies that the task has been processed correctly
     * without any issues.
     */
    ADMINISTRATION_SUCCESS("ADMINISTRATION_SUCCESS"),

    /**
     * Indicates that the beneficiary has refused the task.
     * This status means that the individual or entity for whom the task
     * was intended has declined to participate or accept it.
     */
    BENEFICIARY_REFUSED("BENEFICIARY_REFUSED"),

    /**
     * Indicates that the beneficiary has refused the task.
     * This status means that the individual or entity for whom the task
     * was intended has declined to participate or accept it.
     */
    BENEFICIARY_ABSENT("BENEFICIARY_ABSENT"),

    /**
     * Indicates that the household associated with the task has been closed.
     * This status implies that the household is no longer active or
     * relevant to the task, possibly due to its closure or other reasons.
     */
    CLOSED_HOUSEHOLD("CLOSED_HOUSEHOLD"),

    /**
     * Indicates that the task has been delivered.
     * This status shows that the task has been successfully completed
     * and the deliverables have been provided.
     */
    DELIVERED("DELIVERED"),

    /**
     * Indicates that the task has not been administered.
     * This status signifies that the task has not been processed or
     * handled yet.
     */
    NOT_ADMINISTERED("NOT_ADMINISTERED"),

    /**
     * Indicates that the beneficiary is ineligible.
     * This status means that the individual or entity for whom the task
     * was intended is ineligible
     */
    BENEFICIARY_INELIGIBLE("BENEFICIARY_INELIGIBLE"),

    /**
     * Indicates that the beneficiary is ineligible.
     * This status means that the individual or entity for whom the task
     * was intended was referred to some institution
     */
    BENEFICIARY_REFERRED("BENEFICIARY_REFERRED"),

    /**
     * Indicates that the redose is administered.
     * This status means that the individual or entity for whom the task
     * was intended was given a re dose
     */
    VISITED("VISITED");

    // The string value associated with the task status.
    private String value;

    /**
     * Constructor to initialize the TaskStatus with a specific string value.
     *
     * @param value The string value representing the task status.
     */
    TaskStatus(String value) {
        this.value = value;
    }

    /**
     * Returns the string representation of the TaskStatus.
     * This method is used for serialization of the enum value to JSON.
     *
     * @return The string value of the task status.
     */
    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    /**
     * Creates a TaskStatus enum from a string value.
     * This method is used for deserialization of the enum value from JSON.
     *
     * @param text The string value representing the task status.
     * @return The TaskStatus enum corresponding to the provided value,
     *         or null if no match is found.
     */
    @JsonCreator
    public static TaskStatus fromValue(String text) {
        for (TaskStatus status : TaskStatus.values()) {
            if (String.valueOf(status.value).equals(text)) {
                return status;
            }
        }
        return null; // Return null if no matching status is found
    }
}
