package org.egov.product.web.models;

import java.util.Objects;
import io.swagger.annotations.ApiModel;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Specify the type of operation being performed i.e. CREATE, UPDATE or DELETE
 */
public enum ApiOperation {
  
  CREATE("CREATE"),
  
  UPDATE("UPDATE"),
  
  DELETE("DELETE");

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
    for (ApiOperation b : ApiOperation.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return null;
  }
}

