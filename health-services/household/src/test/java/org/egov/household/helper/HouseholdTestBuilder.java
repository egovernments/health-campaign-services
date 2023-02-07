package org.egov.household.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.household.web.models.AdditionalFields;
import org.egov.household.web.models.Address;
import org.egov.household.web.models.Household;

public class HouseholdTestBuilder {

    private Household.HouseholdBuilder builder;

    public HouseholdTestBuilder() {
        this.builder = Household.builder();
    }

    public static HouseholdTestBuilder builder() {
        return new HouseholdTestBuilder();
    }

    public Household build() {
        return this.builder.build();
    }

    public HouseholdTestBuilder withHousehold(){
        this.builder.id("some-id").additionalFields(AdditionalFields.builder().build())
                .auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build())
                .address(AddressTestBuilder.builder().withAddress().build())
                .memberCount(5)
                .rowVersion(1)
                .clientReferenceId("some-id")
                .tenantId("default");
        return this;
    }

    public HouseholdTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }

    public HouseholdTestBuilder withClientReferenceId(String clientReferenceId) {
        this.builder.clientReferenceId(clientReferenceId);
        return this;
    }

    public HouseholdTestBuilder withRowVersion(int rowVersion) {
        this.builder.rowVersion(rowVersion);
        return this;
    }

    public HouseholdTestBuilder withAddress(Address address){
        this.builder.address(address);
        return this;
    }
}
