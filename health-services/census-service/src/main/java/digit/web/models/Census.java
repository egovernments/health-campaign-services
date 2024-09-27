package digit.web.models;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import digit.web.models.PopulationByDemographic;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.Builder;

/**
 * Census
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Census {

    @JsonProperty("id")
    @Valid
    @Size(min = 2, max = 64)
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    private String tenantId = null;

    @JsonProperty("hierarchyType")
    @NotNull
    private String hierarchyType = null;

    @JsonProperty("boundaryCode")
    @NotNull
    private String boundaryCode = null;

    /**
     * Gets or Sets type
     */
    public enum TypeEnum {
        PEOPLE("people"),

        ANIMALS("animals"),

        PLANTS("plants"),

        OTHER("other");

        private String value;

        TypeEnum(String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }

        @JsonCreator
        public static TypeEnum fromValue(String text) {
            for (TypeEnum b : TypeEnum.values()) {
                if (String.valueOf(b.value).equals(text)) {
                    return b;
                }
            }
            return null;
        }
    }

    @JsonProperty("type")
    @NotNull
    private TypeEnum type = null;

    @JsonProperty("totalPopulation")
    @NotNull
    private Long totalPopulation = null;

    @JsonProperty("populationByDemographics")
    @Valid
    private List<PopulationByDemographic> populationByDemographics = null;

    @JsonProperty("effectiveFrom")
    private Long effectiveFrom = null;

    @JsonProperty("effectiveTo")
    private Long effectiveTo = null;

    @JsonProperty("source")
    @NotNull
    private String source = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;


    public Census addPopulationByDemographicsItem(PopulationByDemographic populationByDemographicsItem) {
        if (this.populationByDemographics == null) {
            this.populationByDemographics = new ArrayList<>();
        }
        this.populationByDemographics.add(populationByDemographicsItem);
        return this;
    }

}
