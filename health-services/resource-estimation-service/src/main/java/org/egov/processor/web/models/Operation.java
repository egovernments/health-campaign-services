package org.egov.processor.web.models;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * Operation
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Operation {
    @JsonProperty("id")
    @Valid
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("input")
    @NotNull
    @Size(min = 1, max = 256)
    private String input = null;

    @JsonProperty("operator")
    @NotNull
    private OperatorEnum operator = null;

    @JsonProperty("assumptionValue")
    @NotNull
    @Size(min = 2, max = 256)
    private String assumptionValue = null;

    @JsonProperty("output")
    @NotNull
    @Size(min = 1, max = 64)
    private String output = null;

    @JsonProperty("active")
    @NotNull
    private Boolean active = true;

    /**
     * The operator used in the operation
     */
    public enum OperatorEnum {
        PLUS("+"),

        MINUS("-"),

        SLASH("/"),

        STAR("*"),

        PERCENT("%"),

        _U("**");

        private String value;

        OperatorEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static OperatorEnum fromValue(String text) {
            for (OperatorEnum b : OperatorEnum.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }
    }

}
