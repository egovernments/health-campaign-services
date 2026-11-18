package org.egov.facility.web.models.staff;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovModel;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacilityStaff extends EgovModel {

    @JsonProperty("facilityId")
    @NotNull
    @Size(min = 2, max = 64)
    private String facilityId;

    @JsonProperty("staffId")
    @NotNull
    @Size(min = 2, max = 64)
    private String staffId;

    @JsonProperty("isDeleted")
    private Boolean isDeleted = Boolean.FALSE;
}
