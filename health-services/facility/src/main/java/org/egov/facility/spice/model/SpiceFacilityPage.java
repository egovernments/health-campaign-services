package org.egov.facility.spice.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** One page of Spice facilities plus the server-reported total. */
@Data
@AllArgsConstructor
public class SpiceFacilityPage {
    private List<SpiceFacility> facilities;
    private long totalCount;
}
