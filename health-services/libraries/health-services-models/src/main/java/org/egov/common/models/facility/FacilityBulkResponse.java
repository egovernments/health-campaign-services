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
 * FacilityResponse
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacilityBulkResponse {
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.response.ResponseInfo responseInfo = null;

    @JsonProperty("Facilities")
    @Valid
    private List<Facility> facilities = null;

    /**
     * The total count of user actions in the response.
     * It is initialized to 0 by default.
     */
    @JsonProperty("TotalCount")
    @Valid
    @Builder.Default
    private Long totalCount = 0L;

    public FacilityBulkResponse addFacilityItem(Facility facilityItem) {
        if (this.facilities == null) {
            this.facilities = new ArrayList<>();
        }
        this.facilities.add(facilityItem);
        return this;
    }

}

