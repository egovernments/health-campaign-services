package org.egov.product.web.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Specify the type of operation being performed i.e. CREATE, UPDATE or DELETE
 */
public enum ApiOperation {
  
  CREATE("CREATE"),

  UPDATE("UPDATE"),

  DELETE("DELETE"),

  UNKNOWN("UNKNOWN");

  private String value;

  ApiOperation(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static ApiOperation fromValue(String text) {
    if (text == null) {
      return null;
    }

    for (ApiOperation b : ApiOperation.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return ApiOperation.UNKNOWN;
  }
}

