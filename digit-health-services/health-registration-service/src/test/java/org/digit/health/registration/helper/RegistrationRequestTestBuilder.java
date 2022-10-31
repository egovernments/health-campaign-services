package org.digit.health.registration.helper;

import org.digit.health.registration.web.models.*;
import org.digit.health.registration.web.models.request.RegistrationRequest;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;

import java.util.ArrayList;
import java.util.List;

public class RegistrationRequestTestBuilder {
    private RegistrationRequest.RegistrationRequestBuilder builder;


    public static RegistrationRequestTestBuilder builder() {
        return new RegistrationRequestTestBuilder();
    }

    public RegistrationRequestTestBuilder() {
        this.builder = RegistrationRequest.builder();
    }

    public List<Identifier> getIdentifiers(){
        ArrayList<Identifier> identifiers = new ArrayList<Identifier>();
        identifiers.add(
                Identifier.builder()
                        .type("NATIONAL_ID")
                        .identifierId("ABCD-1234")
                        .build()
        );
        return identifiers;
    }

    public List<Individual> getIndividuals(){
        ArrayList<Individual> individuals = new ArrayList<Individual>();
        individuals.add(Individual.builder()
                .name("John Doe")
                .dateOfBirth("19960803")
                .gender(Gender.MALE)
                .isHead(true)
                .identifiers(getIdentifiers())
                .additionalFields("\"{\\\"schema\\\":\\\"INDIVIDUAL\\\",\\\"version\\\":2,\\\"fields\\\":[{\\\"key\\\":\\\"height\\\",\\\"value\\\":\\\"180\\\"}]}\"")
                .build()
        );
        return individuals;
    }

    public RegistrationRequestTestBuilder withDetails() {
        builder
                .requestInfo(requestInfo())
                .registration(
                        HouseholdRegistration.builder()
                                .campaignId("ID-1")
                                .numberOfIndividuals(4)
                                .address(Address.builder().addressId("1").addressText("STAR GARAGE").build())
                                .clientReferenceId("GUID")
                                .dateOfRegistration(1663654179)
                                .location(Location.builder().latitude(18.22).longitude(71.00).accuracy(8).build())
                                .administrativeUnit("SOLIMBO")
                                .individuals(getIndividuals())
                                .additionalFields("\"{\\\"schema\\\":\\\"HOUSEHOLD\\\",\\\"version\\\":2,\\\"fields\\\":[{\\\"key\\\":\\\"height\\\",\\\"value\\\":\\\"180\\\"}]}\"")
                                .tenantId("tenantA")
                                .build()
                );
        return this;
    }

    public RequestInfo requestInfo() {
        return RequestInfo.builder()
                .userInfo(User.builder().uuid("uuid").tenantId("tenantId").id(1L).build()).build();
    }

    public RegistrationRequest build() {
        return builder.build();
    }
}
