package org.egov.common.models.facility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;
import org.springframework.validation.annotation.Validated;

/**
 * Facility
 */
@Validated

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Facility extends EgovOfflineModel {

    @JsonProperty("isPermanent")
    private Boolean isPermanent = true;

    @JsonProperty("name")
    @Size(min = 2, max = 2000)
    private String name = null;

    @JsonProperty("usage")
    private String usage = null;

    @JsonProperty("storageCapacity")
    private Integer storageCapacity = null;

    @JsonProperty("address")
    @Valid
    private Address address = null;

    //TODO remove this
    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;



}

