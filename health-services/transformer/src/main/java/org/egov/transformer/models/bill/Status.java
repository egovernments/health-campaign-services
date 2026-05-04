package org.egov.transformer.models.bill;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * status of the Property
 */
public enum Status {

	ACTIVE ("ACTIVE"),

	INACTIVE ("INACTIVE"),

	INWORKFLOW ("INWORKFLOW"),
	
	CANCELLED ("CANCELLED"),
	
	REJECTED ("REJECTED"),

    PENDING_VERIFICATION ("PENDING_VERIFICATION"),

    VERIFICATION_FAILED ("VERIFICATION_FAILED"),

    PARTIALLY_VERIFIED ("PARTIALLY_VERIFIED"),

    FULLY_VERIFIED ("FULLY_VERIFIED"),

    VERIFICATION_IN_PROGRESS("VERIFICATION_IN_PROGRESS"),

    UNDER_REVIEW("UNDER_REVIEW"),

    REVIEWED("REVIEWED"),

    PAYMENT_IN_PROGRESS("PAYMENT_IN_PROGRESS"),

    VERIFIED ("VERIFIED"),

    PARTIALLY_PAID("PARTIALLY_PAID"),

    PAYMENT_FAILED ("PAYMENT_FAILED"),

    FULLY_PAID ("FULLY_PAID"),

    PAID ("PAID"),

    IGNORING_ERRORS_IN_PROGRESS("IGNORING_ERRORS_IN_PROGRESS"),

    SENDING_FOR_REVIEW("SENDING_FOR_REVIEW"),

    REVIEW_IN_PROGRESS("REVIEW_IN_PROGRESS"),

    IN_PROGRESS ("IN_PROGRESS"),

    DONE ("DONE");

	private String value;

  Status(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static Status fromValue(String text) {
    for (Status b : Status.values()) {
      if (String.valueOf(b.value).equalsIgnoreCase(text)) {
        return b;
      }
    }
    return null;
  }
}
