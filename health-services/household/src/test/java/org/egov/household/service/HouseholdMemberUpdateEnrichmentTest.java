package org.egov.household.service;

import org.egov.common.ds.Tuple;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseholdMemberUpdateEnrichmentTest {

    @InjectMocks
    HouseholdMemberEnrichmentService householdMemberEnrichmentService;

    @Mock
    HouseholdMemberRepository householdMemberRepository;

    @Mock
    HouseholdService householdService;


    @BeforeEach
    void setUp() {

    }

    private void mockHouseholdFindIds() {
        when(householdService.findById(
                any(List.class),
                any(String.class),
                any(Boolean.class)
        )).thenReturn(new Tuple(1L,
                Collections.singletonList(
                        Household.builder().id("some-household-id").clientReferenceId("some-client-ref-id").build())
            )
        );
    }

    private void mockFindById() {
        when(householdMemberRepository.findById(
                any(List.class),
                any(String.class),
                any(Boolean.class)
        )).thenReturn(
                Collections.singletonList(
                        HouseholdMember.builder().id("some-id").build())
        );
    }

    @Test
    @DisplayName("should update audit details before pushing the household member to kafka")
    void shouldUpdateAuditDetailsBeforePushingTheHouseholdMemberToKafka() throws Exception {
        mockHouseholdFindIds();
        mockFindById();
        HouseholdMemberBulkRequest householdMemberBulkRequest = HouseholdMemberBulkRequestTestBuilder.builder()
                .withHouseholdMemberAsHead()
                .withRequestInfo()
                .build();

        householdMemberEnrichmentService
                .update(householdMemberBulkRequest.getHouseholdMembers(), householdMemberBulkRequest);

        assertNotNull(householdMemberBulkRequest.getHouseholdMembers().stream().findAny()
                .get().getAuditDetails());
    }

}
