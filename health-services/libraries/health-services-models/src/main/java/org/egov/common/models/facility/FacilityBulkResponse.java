package org.egov.common.models.facility;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

/**
 * Represents a bulk response for facilities, including response metadata and a list of facilities.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacilityBulkResponse {

    /**
     * Metadata about the API response, including request details and status.
     */
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.response.ResponseInfo responseInfo = null;

    /**
     * List of facilities returned in the response.
     */
    @JsonProperty("Facilities")
    @Valid
    private List<Facility> facilities = null;

    /**
     * Total number of facilities in the response, defaults to 0.
     */
    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    /**
     * Adds a single facility to the list and returns the updated response.
     */
    public FacilityBulkResponse addFacilityItem(Facility facilityItem) {
        if (this.facilities == null) {
            this.facilities = new ArrayList<>();
        }
        this.facilities.add(facilityItem);
        return this;
    }
}
