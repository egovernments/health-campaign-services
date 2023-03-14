package org.egov.common.models.stock;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets transactionReason
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public enum TransactionReason {
  
  RECEIVED("RECEIVED"),
  
  RETURNED("RETURNED"),

  LOST_IN_STORAGE("LOST_IN_STORAGE"),

  LOST_IN_TRANSIT("LOST_IN_TRANSIT"),

  DAMAGED_IN_STORAGE("DAMAGED_IN_STORAGE"),

  DAMAGED_IN_TRANSIT("DAMAGED_IN_TRANSIT");

  private String value;

  TransactionReason(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static TransactionReason fromValue(String text) {
    for (TransactionReason b : TransactionReason.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return null;
  }
}

