package org.egov.individual.web.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets gender
 */
public enum BloodGroup {

  B_POSITIVE("B+"),
  B_NEGATIVE("B-"),
  A_POSITIVE("A+"),
  A_NEGATIVE("A-"),
  AB_POSITIVE("AB+"),
  AB_NEGATIVE("AB-"),
  O_NEGATIVE("O-"),
  O_POSITIVE("O+");


  private String value;

  BloodGroup(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static BloodGroup fromValue(String text) {
    for (BloodGroup b : BloodGroup.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return null;
  }
}

