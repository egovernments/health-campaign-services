package org.egov.common.models.household;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovOfflineModel;
import org.hibernate.validator.constraints.Range;
import org.springframework.validation.annotation.Validated;

/**
* A representation of Household.
*/
@Validated


@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Household extends EgovOfflineModel {

    @JsonProperty("memberCount")
    @NotNull
//    @Range(min = 0, max = 1000)
    private Integer memberCount = null;

    @JsonProperty("address")
    @Valid
    private Address address = null;

    @JsonProperty("householdType")
    @NotNull
    @lombok.Builder.Default
    private HouseHoldType householdType = HouseHoldType.FAMILY;

    //TODO remove
    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

}

