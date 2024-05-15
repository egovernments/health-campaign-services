package org.egov.household.helper;

import org.egov.common.helper.AuditDetailsTestBuilder;
import org.egov.common.models.household.AdditionalFields;
import org.egov.common.models.household.HouseholdMember;


public class HouseholdMemberTestBuilder {

    private HouseholdMember.HouseholdMemberBuilder builder;

    public HouseholdMemberTestBuilder() {
        this.builder = HouseholdMember.builder();
    }

    public static HouseholdMemberTestBuilder builder() {
        return new HouseholdMemberTestBuilder();
    }

    public HouseholdMember build() {
        return this.builder.build();
    }

    public HouseholdMemberTestBuilder withHouseholdIdAndIndividualId(){
        this.builder.id("some-id")
                .clientReferenceId("some-client-reference-id")
                .additionalFields(AdditionalFields.builder().build())
                .rowVersion(1)
                .isHeadOfHousehold(false)
                .individualId("some-individual-id")
                .householdId("some-household-id")
                .tenantId("default")
                .hasErrors(false);
        return this;
    }

    public HouseholdMemberTestBuilder withHeadOfHousehold(){
        this.builder.isHeadOfHousehold(true);
        return this;
    }

    public HouseholdMemberTestBuilder withErrors(){
        this.builder.hasErrors(true);
        return this;
    }

    public HouseholdMemberTestBuilder withId(String id) {
        this.builder.id(id);
        return this;
    }


    public HouseholdMemberTestBuilder withRowVersion(int rowVersion) {
        this.builder.rowVersion(rowVersion);
        return this;
    }

    public HouseholdMemberTestBuilder withAuditDetails(int rowVersion) {
        this.builder.auditDetails(AuditDetailsTestBuilder.builder().withAuditDetails().build());
        return this;
    }

    public HouseholdMemberTestBuilder withDeleted(){
        this.builder.isDeleted(true);
        return this;
    }

}
