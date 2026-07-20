package org.egov.facility.spice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Raw facility object from Spice {@code admin-service/healthfacility/list} (entityList[]).
 * Only fields consumed by the mapper are declared. clinicalWorkflows/customizedWorkflows dropped.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpiceFacility {

    private Integer id;                 // Spice server id -> HCM id + clientReferenceId
    private String name;
    private String type;                // clinical facility type -> additionalFields
    private String address;
    private String cityName;
    private String latitude;
    private String longitude;
    private String postalCode;
    private String fhirId;
    private Integer tenantId;           // Spice's own tenant (numeric) -> additionalFields
    private Boolean active;
    private String phuFocalPersonName;
    private String phuFocalPersonNumber;
    private Chiefdom chiefdom;
    private District district;
    private List<LinkedVillage> linkedVillages;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chiefdom {
        private Integer id;
        private Integer districtId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class District {
        private Integer id;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinkedVillage {
        private Integer id;
    }
}
