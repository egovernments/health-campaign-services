package org.egov.facility.web.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * FacilityResponse
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-21T14:37:54.683+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacilityBulkResponse {
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.response.ResponseInfo responseInfo = null;

    @JsonProperty("Facility")
    @Valid
    private List<Facility> facility = null;


    public FacilityBulkResponse addFacilityItem(Facility facilityItem) {
        if (this.facility == null) {
            this.facility = new ArrayList<>();
        }
        this.facility.add(facilityItem);
        return this;
    }

}

