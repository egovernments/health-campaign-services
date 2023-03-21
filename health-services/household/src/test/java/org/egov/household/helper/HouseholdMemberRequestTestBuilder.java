package org.egov.household.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberRequest;

public class HouseholdMemberRequestTestBuilder {
    private HouseholdMemberRequest.HouseholdMemberRequestBuilder builder;

    public HouseholdMemberRequestTestBuilder() {
        this.builder = HouseholdMemberRequest.builder();
    }

    public static HouseholdMemberRequestTestBuilder builder() {
        return new HouseholdMemberRequestTestBuilder();
    }

    public HouseholdMemberRequest build() {
        return this.builder.build();
    }

    public HouseholdMemberRequestTestBuilder withHouseholdMember(){
       this.builder.householdMember(HouseholdMemberTestBuilder.builder().withHouseholdIdAndIndividualId().build());
        return this;
    }

    public HouseholdMemberRequestTestBuilder withDeletedHouseholdMember(){
        this.builder.householdMember(HouseholdMemberTestBuilder.builder().withHouseholdIdAndIndividualId().withDeleted().build());
        return this;
    }

    public HouseholdMemberRequestTestBuilder withHouseholdMemberAsHead(){
        this.builder.householdMember(HouseholdMemberTestBuilder.builder()
                .withHouseholdIdAndIndividualId()
                .withHeadOfHousehold()
                .build());
        return this;
    }

    public HouseholdMemberRequestTestBuilder withRowVersion(Integer rowVersion){
        this.builder.householdMember(HouseholdMemberTestBuilder.builder()
                .withHouseholdIdAndIndividualId()
                .withHeadOfHousehold()
                .withRowVersion(rowVersion)
                .build());
        return this;
    }

    public HouseholdMemberRequestTestBuilder withHouseholdMember(HouseholdMember householdMember) {
        this.builder.householdMember(householdMember);
        return this;
    }

    public HouseholdMemberRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }




}
