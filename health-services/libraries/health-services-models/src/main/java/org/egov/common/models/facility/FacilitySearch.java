package org.egov.common.models.facility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.List;

/**
 * FacilitySearch
 */
@Validated
@javax.annotation.Generated(value = "org.egov.codegen.SpringBootCodegen", date = "2023-02-21T14:37:54.683+05:30")

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacilitySearch {
    @JsonProperty("id")
    @Valid
    private List<String> id = null;

    @JsonProperty("isPermanent")
    private Boolean isPermanent = null;

    @JsonProperty("usage")
    private String usage = null;

    @JsonProperty("storageCapacity")
    private Integer storageCapacity = null;

    @JsonProperty("boundaryCode")
    private String localityCode = null;


}

