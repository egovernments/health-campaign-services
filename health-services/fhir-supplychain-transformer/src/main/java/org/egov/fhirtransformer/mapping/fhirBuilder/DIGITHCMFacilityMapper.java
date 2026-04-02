package org.egov.fhirtransformer.mapping.fhirBuilder;

import org.egov.common.models.facility.Facility;
import org.egov.fhirtransformer.common.Constants;
import org.hl7.fhir.r5.model.*;
import java.util.List;
import java.util.Date;


/**
 * Mapper utility for converting DIGIT boundary master data
 * to FHIR {@link Location} resources and back.
 */
public class DIGITHCMFacilityMapper {

    /**
     * Creates a FHIR {@link Location} resource from a DIGIT {@code Facility}.
     * @param facility facility master data
     * @return populated {@link Location} resource representing the facility
     */
    public static Location buildLocationFromFacility(Facility facility) {

        Location location = new Location();
        Long lastModifiedMillis = facility.getAuditDetails().getLastModifiedTime();
        Date lastModified = (lastModifiedMillis != null) ? new Date(lastModifiedMillis) : null;

        location.setId(facility.getId());
        location.setName(facility.getName());
        location.setStatus(Location.LocationStatus.ACTIVE);

        // Setting meta information for the Location resource DIGIT HCM Facility profile
        location.setMeta(new Meta()
                .setLastUpdated(lastModified)
                .addProfile(Constants.PROFILE_DIGIT_HCM_FACILITY));

        // Adding identifier for facility ID
        Identifier identifier = new Identifier()
                .setSystem(Constants.IDENTIFIER_SYSTEM_FACILITY)
                .setValue(facility.getId());
        location.addIdentifier(identifier);

        // Adding facility type and usage
        location.addType(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem(Constants.LOCATION_TYPE_SYSTEM)
                        .setCode(Constants.FACILITY_LOCATION_TYPE)));
        location.addType(new CodeableConcept().addCoding(
                new Coding()
                        .setSystem(Constants.FACILITY_USAGE_SYSTEM)
                        .setCode(facility.getUsage())));

        // Setting address details
        Address address = new Address();
        org.egov.common.models.facility.Address addr = facility.getAddress();
        if (addr != null) {
            if (addr.getBuildingName() != null) {
                address.addLine(addr.getBuildingName());
            }
            if (addr.getAddressLine1() != null) {
                address.addLine(addr.getAddressLine1());
            }
            if (addr.getAddressLine2() != null) {
                address.addLine(addr.getAddressLine2());
            }
            address.setCity(addr.getCity());
            address.setPostalCode(addr.getPincode());

            location.setAddress(address);

            // Setting position details (latitude and longitude)
            Location.LocationPositionComponent position = new Location.LocationPositionComponent()
                    .setLatitude(addr.getLatitude())
                    .setLongitude(addr.getLongitude());

            location.setPosition(position);
        }
        return location;
    }

    /**
     * Converts a FHIR {@link Location} resource into a DIGIT {@code Facility}.
     *
     * @param location FHIR Location to convert; must not be {@code null}
     * @param tenantID
     * @return populated {@code Facility} object
     */
    public Facility convertFhirLocationToFacility(Location location, String tenantID) {
        Facility facility = new Facility();

        facility.setTenantId(tenantID);
        // Map other necessary fields from Location to Facility

        //Map from FHIR
        facility.setId(location.getIdElement().getIdPart());
        facility.setName(location.getName());

        for(CodeableConcept type : location.getType()) {
            for(Coding coding : type.getCoding()) {
                if (Constants.FACILITY_USAGE_SYSTEM.equals(coding.getSystem())) {
                    facility.setUsage(coding.getCode());
                }
            }
        }

        if (location.getAddress() != null) {

            org.egov.common.models.facility.Address facilityAddress = new org.egov.common.models.facility.Address();
            if (!location.getAddress().getLine().isEmpty()) {
                List<StringType> lines = location.getAddress().getLine();
                if (!lines.isEmpty() && lines.get(0).getValue() != null)
                    facilityAddress.setBuildingName(lines.get(0).getValue());
                if (lines.size() >= 2 && lines.get(1).getValue() != null)
                    facilityAddress.setAddressLine1(lines.get(1).getValue());
                if (lines.size() >= 3 && lines.get(2).getValue() != null)
                    facilityAddress.setAddressLine2(lines.get(2).getValue());

            }
            facilityAddress.setCity(location.getAddress().getCity());
            facilityAddress.setPincode(location.getAddress().getPostalCode());
            if (location.getPosition() != null) {
                if (location.getPosition().getLatitude() != null) {
                    facilityAddress.setLatitude(location.getPosition().getLatitude().doubleValue());
                }
                if (location.getPosition().getLongitude() != null) {
                    facilityAddress.setLongitude(location.getPosition().getLongitude().doubleValue());
                }

            }
            facility.setAddress(facilityAddress);
        }
        return facility;
    }
}
