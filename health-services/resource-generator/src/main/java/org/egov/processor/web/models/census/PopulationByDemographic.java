package org.egov.processor.web.models.census;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * PopulationByDemographic
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PopulationByDemographic {

    @JsonProperty("id")
    @Valid
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("demographicVariable")
    private DemographicVariableEnum demographicVariable = null;

    @JsonProperty("populationDistribution")
    private Object populationDistribution = null;

    /**
     * Gets or Sets demographicVariable
     */
    public enum DemographicVariableEnum {
        AGE("age"),

        GENDER("gender"),

        ETHNICITY("ethnicity");

        private String value;

        DemographicVariableEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static DemographicVariableEnum fromValue(String text) {
            for (DemographicVariableEnum b : DemographicVariableEnum.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }
    }

}
