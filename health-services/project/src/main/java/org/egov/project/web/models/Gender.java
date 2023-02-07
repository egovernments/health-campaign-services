package org.egov.project.web.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets gender
 */
public enum Gender {
  
  MALE("MALE"),
  
  FEMALE("FEMALE"),
  
  OTHER("OTHER");

  private String value;

  Gender(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static Gender fromValue(String text) {
    for (Gender b : Gender.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return null;
  }
}

