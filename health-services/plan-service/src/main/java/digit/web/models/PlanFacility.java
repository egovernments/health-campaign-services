package digit.web.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.egov.common.contract.models.AuditDetails;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Plan Facility
 */
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanFacility {

    @JsonProperty("id")
    private String id = null;

    @JsonProperty("tenantId")
    @NotNull
    @Size(min = 2, max = 64)
    private String tenantId = null;

    @JsonProperty("planConfigurationId")
    @NotNull
    @Size(max = 64)
    private String planConfigurationId = null;

    @JsonProperty("planConfigurationName")
    private String planConfigurationName = null;

    @JsonProperty("facilityId")
    @NotNull
    @Size(max = 64)
    private String facilityId = null;

    @JsonProperty("facilityName")
    private String facilityName = null;

    @JsonProperty("residingBoundary")
    @NotNull
    @Size(min = 1, max = 64)
    private String residingBoundary = null;

    @JsonProperty("serviceBoundaries")
    @NotNull
    @Valid
    private List<String> serviceBoundaries;

    @JsonIgnore
    private List<String> initiallySetServiceBoundaries;

    @JsonProperty("additionalDetails")
    private Object additionalDetails = null;

    @JsonProperty("active")
    @NotNull
    private Boolean active = null;

    @JsonProperty("auditDetails")
    private AuditDetails auditDetails = null;

    public PlanFacility addServiceBoundariesItem(String serviceBoundariesItem) {
        if (this.serviceBoundaries == null) {
            this.serviceBoundaries = new ArrayList<>();
        }
        this.serviceBoundaries.add(serviceBoundariesItem);
        return this;
    }



}
