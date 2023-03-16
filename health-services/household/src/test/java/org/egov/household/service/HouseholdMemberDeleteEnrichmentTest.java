package org.egov.household.service;

import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.household.helper.HouseholdMemberBulkRequestTestBuilder;
import org.egov.household.repository.HouseholdMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class HouseholdMemberDeleteEnrichmentTest {

    @InjectMocks
    HouseholdMemberEnrichmentService householdMemberEnrichmentService;

    @Mock
    HouseholdMemberRepository householdMemberRepository;

    @Mock
    HouseholdService householdService;


    @BeforeEach
    void setUp() {

    }

    @Test
    @DisplayName("should set isDeleted to true for the household member")
    void shouldSetIsDeletedToTrueForTheHouseholdMember() {
        HouseholdMemberBulkRequest householdMemberBulkRequest = HouseholdMemberBulkRequestTestBuilder.builder()
                .withHouseholdMemberAsHead()
                .withRequestInfo()
                .build();

        householdMemberEnrichmentService.delete(householdMemberBulkRequest.getHouseholdMembers(),
                householdMemberBulkRequest);
        assertTrue(householdMemberBulkRequest.getHouseholdMembers().get(0).getIsDeleted());
    }


}
