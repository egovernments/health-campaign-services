package org.egov.facility.spice;

import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Boundary;
import org.egov.common.models.core.Field;
import org.egov.common.models.facility.Address;
import org.egov.common.models.facility.AddressType;
import org.egov.common.models.facility.Facility;
import org.egov.facility.spice.model.SpiceFacility;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps a Spice facility to an HCM {@link Facility}. The Spice server id populates both {@code id}
 * and {@code clientReferenceId}. Clinical attributes with no native HCM Facility field go to
 * additionalFields; usage/storageCapacity stay null (HCM Facility is storage-oriented).
 */
@Component
public class SpiceFacilityMapper {

    private static final String ACTOR = "spice-facility";

    public Facility toFacility(SpiceFacility s, String tenantId) {
        Facility f = new Facility();
        f.setId(String.valueOf(s.getId()));                 // Spice server id -> id
        f.setClientReferenceId(String.valueOf(s.getId()));  // Spice server id -> clientReferenceId
        f.setTenantId(tenantId);
        f.setName(s.getName());
        f.setIsPermanent(Boolean.TRUE);
        f.setIsDeleted(s.getActive() != null && !s.getActive());
        f.setRowVersion(1);
        long now = System.currentTimeMillis();
        f.setAuditDetails(AuditDetails.builder()
                .createdBy(ACTOR).createdTime(now).lastModifiedBy(ACTOR).lastModifiedTime(now).build());

        Address addr = new Address();
        addr.setTenantId(tenantId);
        addr.setType(AddressType.PERMANENT);
        addr.setAddressLine1(s.getAddress());
        addr.setCity(s.getCityName());
        addr.setPincode(s.getPostalCode());
        addr.setLatitude(toDouble(s.getLatitude()));
        addr.setLongitude(toDouble(s.getLongitude()));
        addr.setLocality(chiefdomBoundary(s, tenantId));
        f.setAddress(addr);

        f.setAdditionalFields(additional(s));
        return f;
    }

    /** Facility sits at the CHIEFDOM level in SPICE_SL: SL_C1_D<districtId>_CH<chiefdomId>. */
    private Boundary chiefdomBoundary(SpiceFacility s, String tenantId) {
        SpiceFacility.Chiefdom ch = s.getChiefdom();
        if (ch == null || ch.getId() == null) return null;
        Integer districtId = ch.getDistrictId() != null ? ch.getDistrictId()
                : (s.getDistrict() != null ? s.getDistrict().getId() : null);
        String code = "SL_C1_D" + districtId + "_CH" + ch.getId();
        return Boundary.builder().code(code).tenantId(tenantId).build();
    }

    private AdditionalFields additional(SpiceFacility s) {
        List<Field> fields = new ArrayList<>();
        add(fields, "facilityType", s.getType());
        add(fields, "fhirId", s.getFhirId());
        add(fields, "spiceTenantId", str(s.getTenantId()));
        add(fields, "phuFocalPersonName", s.getPhuFocalPersonName());
        add(fields, "phuFocalPersonNumber", s.getPhuFocalPersonNumber());
        if (s.getLinkedVillages() != null && !s.getLinkedVillages().isEmpty()) {
            String csv = s.getLinkedVillages().stream()
                    .map(v -> String.valueOf(v.getId()))
                    .collect(Collectors.joining(","));
            add(fields, "linkedVillageIds", csv);
        }
        return AdditionalFields.builder().schema("FACILITY").version(1).fields(fields).build();
    }

    private void add(List<Field> fields, String key, String value) {
        if (value != null) fields.add(Field.builder().key(key).value(value).build());
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Double toDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
