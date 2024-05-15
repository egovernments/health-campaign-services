package org.egov.common.models.stock;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets transactionType
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public enum TransactionType {
  
  RECEIVED("RECEIVED"),
  
  DISPATCHED("DISPATCHED");

  private String value;

  TransactionType(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static TransactionType fromValue(String text) {
    for (TransactionType b : TransactionType.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return null;
  }
}

