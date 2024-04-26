package org.egov.facility.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.facility.Address;
import org.egov.common.models.facility.Facility;


public class FacilityTestBuilder {

    private final Facility.FacilityBuilder<Facility, ?> builder;

    public FacilityTestBuilder() {
        this.builder = (Facility.FacilityBuilder<Facility, ?>) Facility.builder();
    }

    public static FacilityTestBuilder builder() {
        return new FacilityTestBuilder();
    }

    public Facility build() {
        return this.builder.build();
    }

    public FacilityTestBuilder withFacility() {
        this.builder.storageCapacity(0).name("name")
                .usage("usage").rowVersion(1).tenantId("default").isPermanent(Boolean.TRUE)
                .address(AddressTestBuilder.builder().withAddress().build())
                .hasErrors(false).isDeleted(Boolean.FALSE)
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public FacilityTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }
    public FacilityTestBuilder withAddress(Address address){
        this.builder.address(address);
        return this;
    }
}
