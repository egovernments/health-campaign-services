package org.egov.facility.web.models.staff;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.egov.common.models.core.EgovSearchModel;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FacilityStaffSearch extends EgovSearchModel {

    @JsonProperty("facilityId")
    private List<String> facilityId;

    @JsonProperty("staffId")
    private List<String> staffId;
}
