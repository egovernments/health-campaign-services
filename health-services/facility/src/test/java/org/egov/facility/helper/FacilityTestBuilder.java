package org.egov.facility.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.facility.web.models.Facility;

public class FacilityTestBuilder {

    private final Facility.FacilityBuilder builder;

    public FacilityTestBuilder() {
        this.builder = Facility.builder();
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
}
