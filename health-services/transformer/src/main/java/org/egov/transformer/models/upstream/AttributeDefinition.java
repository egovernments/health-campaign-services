package org.egov.transformer.models.upstream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import digit.models.coremodels.AuditDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Hold the attribute definition fields details as json object.
 */

@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AttributeDefinition {
    @JsonProperty("id")
    private String id = null;

    @JsonProperty("referenceId")
    private String referenceId = null;

    @JsonProperty("tenantId")
    private String tenantId = null;

    @JsonProperty("code")
    private String code = null;

    /**
     * defines the attribute type
     */
    public enum DataTypeEnum {
        STRING("String"),

        NUMBER("Number"),

        TEXT("Text"),

        DATETIME("Datetime"),

        SINGLEVALUELIST("SingleValueList"),

        MULTIVALUELIST("MultiValueList"),

        FILE("File");

        private String value;

        DataTypeEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static DataTypeEnum fromValue(String text) {
            for (DataTypeEnum b : DataTypeEnum.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }
    }

    @JsonProperty("dataType")
    private DataTypeEnum dataType = null;

    @JsonProperty("values")
    private List<String> values = null;

    @JsonProperty("isActive")
    private Boolean isActive = true;

    @JsonProperty("required")
    private Boolean required = null;

    @JsonProperty("regex")
    private String regex = null;

    @JsonProperty("order")
    private String order = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;


    public AttributeDefinition addValuesItem(String valuesItem) {
        if (this.values == null) {
            this.values = new ArrayList<>();
        }
        this.values.add(valuesItem);
        return this;
    }

}
