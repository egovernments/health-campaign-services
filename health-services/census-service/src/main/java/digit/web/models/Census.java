package digit.web.models;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.models.Workflow;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;


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

    @JsonProperty("assignee")
    @Size(max = 64)
    private String assignee = null;

    @JsonProperty("status")
    @Size(max = 64)
    private String status = null;

    @JsonProperty("type")
    @NotNull
    private TypeEnum type = null;

    @JsonProperty("totalPopulation")
    @NotNull
    private Long totalPopulation = null;

    @JsonProperty("populationByDemographics")
    @Valid
    private List<PopulationByDemographic> populationByDemographics = null;

    @JsonProperty("additionalFields")
    @Valid
    private List<AdditionalField> additionalFields = null;

    @JsonProperty("effectiveFrom")
    private Long effectiveFrom = null;

    @JsonProperty("effectiveTo")
    private Long effectiveTo = null;

    @JsonProperty("source")
    @NotNull
    private String source = null;

    @JsonIgnore
    private List<String> boundaryAncestralPath = null;

    @JsonIgnore
    @Builder.Default
    private Boolean partnerAssignmentValidationEnabled = Boolean.TRUE;

    @JsonProperty("facilityAssigned")
    private Boolean facilityAssigned = null;

    @JsonProperty("workflow")
    @Valid
    private Workflow workflow;

    @JsonIgnore
    private List<String> assigneeJurisdiction;

    @JsonIgnore
    private Map<String, String> jurisdictionMapping;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("auditDetails")
    private @Valid AuditDetails auditDetails;

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

}
