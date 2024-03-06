package digit.web.models;

import digit.models.coremodels.AuditDetails;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

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
    private UUID id = null;

    @JsonProperty("input")
    @NotNull
    @Size(min = 1, max = 32)
    private String input = null;

    @JsonProperty("operator")
    @NotNull
    private OperatorEnum operator = null;

    @JsonProperty("assumptionValue")
    private String assumptionValue = null;

    @JsonProperty("output")
    @Size(min = 1, max = 32)
    private String output = null;

    @JsonProperty("auditDetails")
    private @Valid AuditDetails auditDetails;


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
