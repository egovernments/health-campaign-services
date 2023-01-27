package org.egov.household.service;

import org.egov.common.http.client.ServiceRequestClient;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.helper.HouseholdMemberRequestTestBuilder;
import org.egov.household.helper.HouseholdMemberTestBuilder;
import org.egov.household.helper.HouseholdRequestTestBuilder;
import org.egov.household.helper.HouseholdTestBuilder;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.household.web.models.Address;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberRequest;
import org.egov.household.web.models.HouseholdRequest;
import org.egov.household.web.models.Individual;
import org.egov.household.web.models.IndividualResponse;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseholdMemberUpdateTest {

    @InjectMocks
    HouseholdMemberService householdMemberService;

    @Mock
    HouseholdMemberRepository householdMemberRepository;

    @Mock
    HouseholdMemberConfiguration householdMemberConfiguration;

    @Mock
    HouseholdService householdService;

    @Mock
    private ServiceRequestClient serviceRequestClient;

    @BeforeEach
    void setUp() {
        lenient().when(householdMemberConfiguration.getCreateTopic()).thenReturn("create-topic");
        lenient().when(householdMemberConfiguration.getUpdateTopic()).thenReturn("update-topic");
    }

    private void mockHouseholdFindIds() {
        when(householdService.findById(
                any(List.class),
                any(String.class),
                any(Boolean.class)
        )).thenReturn(
                Collections.singletonList(
                        Household.builder().id("some-household-id").clientReferenceId("some-client-ref-id").build())
        );
    }

    private void mockServiceRequestClientWithIndividual() throws Exception {
        when(serviceRequestClient.fetchResult(
                any(StringBuilder.class),
                any(),
                eq(IndividualResponse.class))
        ).thenReturn(
                IndividualResponse.builder().individual(Collections.singletonList(Individual.builder()
                                .clientReferenceId("client")
                                .id("id")
                        .build())).build()
        );
    }

    private void mockIndividualMapping(){
        when(householdMemberRepository.findIndividual(anyString())).thenReturn(Collections.singletonList(
                HouseholdMember.builder()
                        .individualId("some-other-individual")
                        .build()
        ));
    }


    @Test
    @DisplayName("should throw exception if household not found")
    void shouldThrowExceptionIfHouseholdNotFound() throws Exception {
        when(householdService.findById(
                any(List.class),
                any(String.class),
                any(Boolean.class)
        )).thenReturn(
                Collections.emptyList()
        );
        HouseholdMemberRequest request = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationUpdate()
                .build();

        assertThrows(CustomException.class, () -> householdMemberService.update(request));
    }
    @Test
    @DisplayName("should throw exception if row version is not correct")
    void shouldThrowExceptionIfRowVersionNotCorrect() throws Exception {
        mockHouseholdFindIds();
        mockServiceRequestClientWithIndividual();
        mockIndividualMapping();
        HouseholdMemberRequest request = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationUpdate()
                .build();
        request.getHouseholdMember().get(0).setRowVersion(10);
        when(householdMemberRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(Collections.singletonList(HouseholdMemberTestBuilder.builder().withHouseholdIdAndIndividualId().withRowVersion(5).build()));

        assertThrows(CustomException.class, () -> householdMemberService.update(request));
    }

    @Test
    @DisplayName("should increment row version by 1")
    void shouldIncrementRowVersionByOne() throws Exception {
        mockHouseholdFindIds();
        mockServiceRequestClientWithIndividual();
        mockIndividualMapping();
        HouseholdMemberRequest request = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationUpdate()
                .build();
        when(householdMemberRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(request.getHouseholdMember());

        List<HouseholdMember> householdMembers = householdMemberService.update(request);

        assertEquals(2, householdMembers.get(0).getRowVersion());
    }

    @Test
    @DisplayName("should set isDeleted true if API operation is delete")
    void shouldSetIsDeletedIfApiOperationIsTrue() throws Exception {
        mockHouseholdFindIds();
        mockServiceRequestClientWithIndividual();
        mockIndividualMapping();
        HouseholdMemberRequest request = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationDelete().build();

        when(householdMemberRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(request.getHouseholdMember());

        List<HouseholdMember> householdMembers = householdMemberService.update(request);

        assertTrue(householdMembers.get(0).getIsDeleted());
    }

    @Test
    @DisplayName("should send data to kafka topic")
    void shouldSendDataToKafkaTopic() throws Exception {

        mockHouseholdFindIds();
        mockServiceRequestClientWithIndividual();
        mockIndividualMapping();
        HouseholdMemberRequest request = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationDelete().build();

        when(householdMemberRepository.findById(anyList(), eq("id"), eq(false)))
                .thenReturn(request.getHouseholdMember());

        householdMemberService.update(request);

        verify(householdMemberRepository, times(1)).save(anyList(), anyString());
    }

}
