package org.egov.project.helper;


import org.egov.common.models.project.Address;
import org.egov.common.models.project.AddressType;

public class AddressTestBuilder {
    private final Address.AddressBuilder builder;
    public AddressTestBuilder() {
        this.builder = Address.builder();
    }

    public static AddressTestBuilder builder() {
        return new AddressTestBuilder();
    }

    public Address build() {
        return this.builder.build();
    }

    public AddressTestBuilder withAddress(){
        this.builder.tenantId("default").addressLine1("line 1").addressLine2("line 2").id("some-id").city("city")
                .landmark("landmark").buildingName("buildingName").latitude(12.31).longitude(12.31)
                .type(AddressType.OTHER).locationAccuracy(12.21).pincode("98909");
        return this;
    }
}
