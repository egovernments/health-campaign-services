package org.egov.household.web.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.data.query.annotations.Exclude;
import org.egov.common.data.query.annotations.Table;
import org.springframework.validation.annotation.Validated;

/**
* A representation of Household.
*/
    @ApiModel(description = "A representation of Household.")
@Validated

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@Table(name = "household h")
    @Deprecated
public class HouseholdSearch {

    @JsonProperty("id")
    private List<String> id = null;

    @JsonProperty("clientReferenceId")
    private List<String> clientReferenceId = null;

//    @JsonProperty("memberCount")
//    private Integer memberCount = null;

    @JsonProperty("boundaryCode")
    private String localityCode = null;

    @Exclude
    @JsonProperty("latitude")
    @DecimalMin("-90")
    @DecimalMax("90")
    private Double latitude = null;

    @Exclude
    @JsonProperty("longitude")
    @DecimalMin("-180")
    @DecimalMax("180")
    private Double longitude = null;

    /*
     * @value unit of measurement in Kilometer
     * */
    @Exclude
    @JsonProperty("searchRadius")
    @DecimalMin("0")
    private Double searchRadius = null;
}

