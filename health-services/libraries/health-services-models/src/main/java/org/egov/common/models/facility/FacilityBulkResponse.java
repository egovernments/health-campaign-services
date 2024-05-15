package org.egov.common.models.facility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacilityBulkResponse {
    @JsonProperty("ResponseInfo")
    @NotNull
    @Valid
    private org.egov.common.contract.response.ResponseInfo responseInfo = null;

    @JsonProperty("Facilities")
    @Valid
    private List<Facility> facilities = null;


    public FacilityBulkResponse addFacilityItem(Facility facilityItem) {
        if (this.facilities == null) {
            this.facilities = new ArrayList<>();
        }
        this.facilities.add(facilityItem);
        return this;
    }

}

