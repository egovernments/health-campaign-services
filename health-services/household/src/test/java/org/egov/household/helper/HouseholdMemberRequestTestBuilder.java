package org.egov.household.helper;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.household.web.models.ApiOperation;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberRequest;

import java.util.Arrays;
import java.util.List;

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
        this.builder.householdMember(Arrays.asList(HouseholdMemberTestBuilder.builder().withHouseholdIdAndIndividualId().build()));
        return this;
    }

    public HouseholdMemberRequestTestBuilder withHouseholdMemberAsHead(){
        this.builder.householdMember(Arrays.asList(HouseholdMemberTestBuilder.builder()
                .withHouseholdIdAndIndividualId()
                .withHeadOfHousehold()
                .build()));
        return this;
    }
    public HouseholdMemberRequestTestBuilder withHouseholdMember(List<HouseholdMember> householdMember) {
        this.builder.householdMember(householdMember);
        return this;
    }

    public HouseholdMemberRequestTestBuilder withRequestInfo(){
        this.builder.requestInfo(RequestInfoTestBuilder.builder().withCompleteRequestInfo().build());
        return this;
    }

    public HouseholdMemberRequestTestBuilder withApiOperationCreate(){
     this.builder.apiOperation(ApiOperation.CREATE);
     return this;
    }

    public HouseholdMemberRequestTestBuilder withApiOperationDelete(){
        this.builder.apiOperation(ApiOperation.DELETE);
        return this;
    }

    public HouseholdMemberRequestTestBuilder withApiOperationUpdate(){
        this.builder.apiOperation(ApiOperation.UPDATE);
        return this;
    }
}
