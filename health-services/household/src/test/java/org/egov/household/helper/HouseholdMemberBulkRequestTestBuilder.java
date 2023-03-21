package org.egov.household.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;

import java.util.Collections;

public class HouseholdMemberBulkRequestTestBuilder {
    private HouseholdMemberBulkRequest.HouseholdMemberBulkRequestBuilder builder;

    public HouseholdMemberBulkRequestTestBuilder() {
        this.builder = HouseholdMemberBulkRequest.builder();
    }

    public static HouseholdMemberBulkRequestTestBuilder builder() {
        return new HouseholdMemberBulkRequestTestBuilder();
    }

    public HouseholdMemberBulkRequest build() {
        return this.builder.build();
    }

    public HouseholdMemberBulkRequestTestBuilder withHouseholdMember(){
       this.builder.householdMembers(Collections.singletonList(HouseholdMemberTestBuilder.builder()
               .withHouseholdIdAndIndividualId().build()));
        return this;
    }

    public HouseholdMemberBulkRequestTestBuilder withDeletedHouseholdMember(){
        this.builder.householdMembers(Collections.singletonList(HouseholdMemberTestBuilder.builder()
                .withHouseholdIdAndIndividualId().withDeleted().build()));
        return this;
    }

    public HouseholdMemberBulkRequestTestBuilder withHouseholdMemberAsHead(){
        this.builder.householdMembers(Collections.singletonList(HouseholdMemberTestBuilder.builder()
                .withHouseholdIdAndIndividualId()
                .withHeadOfHousehold()
                .build()));
        return this;
    }

    public HouseholdMemberBulkRequestTestBuilder withRowVersion(Integer rowVersion){
        this.builder.householdMembers(Collections.singletonList(HouseholdMemberTestBuilder.builder()
                .withHouseholdIdAndIndividualId()
                .withHeadOfHousehold()
                .withRowVersion(rowVersion)
                .build()));
        return this;
    }

    public HouseholdMemberBulkRequestTestBuilder withHouseholdMember(HouseholdMember householdMember) {
        this.builder.householdMembers(Collections.singletonList(householdMember));
        return this;
    }

    public HouseholdMemberBulkRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }




}
