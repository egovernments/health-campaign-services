package digit.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;


/**
 * CensusDTO
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CensusDTO {

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
    @NotNull
    private String assignee = null;

    @JsonProperty("status")
    @NotNull
    private String status = null;

    @JsonProperty("type")
    @NotNull
    private String type = null;

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

    @JsonProperty("boundaryCodeAncestralMaterializedPath")
    @NotNull
    private String materializedPath = null;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("auditDetails")
    private @Valid AuditDetails auditDetails;
}
