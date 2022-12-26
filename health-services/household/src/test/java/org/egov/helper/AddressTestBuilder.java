package org.egov.helper;

import org.egov.web.models.Address;
import org.egov.web.models.Boundary;

public class AddressTestBuilder {
    private Address.AddressBuilder builder;
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
        this.builder.addressLine1("line 1").addressLine2("line 2").id("some-id").city("city")
                .landmark("landmark").buildingName("buildingName").latitude(12.31).longitude(12.31)
                .locationAccuracy(12.21).locality(Boundary.builder().code("code")
                        .label("label1").name("name1").build()).pincode("98909").type("HOME");
        return this;
    }
}
