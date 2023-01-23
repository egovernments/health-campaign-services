package org.egov.household.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.helper.HouseholdMemberRequestTestBuilder;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberRequest;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HouseholdMemberCreateTest {

    @InjectMocks
    HouseholdMemberService householdMemberService;

    @Mock
    HouseholdMemberRepository householdMemberRepository;

    @Mock
    HouseholdService householdService;

    @Mock
    IdGenService idGenService;

    @Mock
    HouseholdMemberConfiguration householdMemberConfiguration;

    @Mock
    Producer producer;

    @Mock
    private ServiceRequestClient serviceRequestClient;

    @BeforeEach
    void setUp() throws Exception {
        List<String> idList = new ArrayList<>();
        idList.add("some-id");
        lenient().when(idGenService.getIdList(any(RequestInfo.class),
                        any(String.class),
                        eq("household.member.id"), eq(""), anyInt()))
                .thenReturn(idList);
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
                IndividualResponse.builder().individual(Collections.singletonList(Individual.builder().build())).build()
        );
    }

    @Test
    @DisplayName("should send data to kafka")
    void shouldSendDataToKafkaTopic() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationCreate().build();

        mockHouseholdFindIds();
        mockServiceRequestClientWithIndividual();

        List<HouseholdMember> createdHouseholdMembers =  householdMemberService.create(householdMemberRequest);

        verify(serviceRequestClient, times(1)).fetchResult(any(), any(), any());
        verify(idGenService, times(1)).getIdList(any(RequestInfo.class),
                any(String.class),
                eq("household.member.id"),
                eq(""),
                anyInt());
        verify(householdMemberRepository, times(1)).save(createdHouseholdMembers, "create-topic");
    }

    @Test
    @DisplayName("should send data to kafka if the request have the individual who is head of household")
    void shouldSendDataToKafkaTopicWhenIndividualIsHeadOfHousehold() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder()
                .withHouseholdMember()
                .withHouseholdMemberAsHead()
                .withRequestInfo()
                .withApiOperationCreate()
                .build();

        mockHouseholdFindIds();
        mockServiceRequestClientWithIndividual();

        householdMemberService.create(householdMemberRequest);

        verify(householdMemberRepository, times(1)).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should throw error if household doesnt exists")
    void shouldThrowErrorIfHouseholdDoesNotExists() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationCreate().build();

        when(householdService.findById(
                any(List.class),
                any(String.class),
                any(Boolean.class)
        )).thenReturn(
                Collections.emptyList()
        );

        assertThrows(CustomException.class, () ->  householdMemberService.create(householdMemberRequest));

    }

    @Test
    @DisplayName("should throw error if individual doesnt exists")
    void shouldThrowErrorIfIndividualDoesNotExists() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder().withHouseholdMember().withRequestInfo()
                .withApiOperationCreate().build();

        mockHouseholdFindIds();
        when(serviceRequestClient.fetchResult(
                any(StringBuilder.class),
                any(),
                eq(IndividualResponse.class))
        ).thenReturn(
                IndividualResponse.builder().individual(Collections.emptyList()).build()
        );

        assertThrows(CustomException.class, () ->  householdMemberService.create(householdMemberRequest));
        verify(householdMemberRepository, times(0)).save(anyList(), anyString());

    }

    @Test
    @DisplayName("should throw error if individual in a household already is head")
    void shouldThrowErrorIfIndividualInAHouseholdIsAlreadyHead() throws Exception {
        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder()
                .withHouseholdMemberAsHead()
                .withRequestInfo()
                .withApiOperationCreate().build();

        mockHouseholdFindIds();
        mockServiceRequestClientWithIndividual();
        when(householdMemberRepository.findIndividualByHousehold(anyString())).thenReturn(Collections.singletonList(
                HouseholdMember.builder()
                        .individualId("some-other-individual")
                        .isHeadOfHousehold(true)
                        .build()
        ));

        assertThrows(CustomException.class, () ->  householdMemberService.create(householdMemberRequest));
        verify(serviceRequestClient, times(1)).fetchResult(any(),any(), any());
        verify(householdMemberRepository, times(1)).findIndividualByHousehold(any());
        verify(householdMemberRepository, times(0)).save(anyList(), anyString());

    }

    @Test
    @DisplayName("should update audit details before pushing the project staff to kafka")
    void shouldUpdateAuditDetailsBeforePushingTheProjectStaffsToKafka() throws Exception {
        mockHouseholdFindIds();
        mockServiceRequestClientWithIndividual();

        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder()
                .withHouseholdMemberAsHead()
                .withRequestInfo()
                .withApiOperationCreate().build();

        List<HouseholdMember> householdMembers = householdMemberService.create(householdMemberRequest);

        assertNotNull(householdMembers.stream().findAny().get().getAuditDetails().getCreatedBy());
        assertNotNull(householdMembers.stream().findAny().get().getAuditDetails().getCreatedTime());
        assertNotNull(householdMembers.stream().findAny().get().getAuditDetails().getLastModifiedBy());
        assertNotNull(householdMembers.stream().findAny().get().getAuditDetails().getLastModifiedTime());
    }

    @Test
    @DisplayName("should set row version as 1 and deleted as false")
    void shouldSetRowVersionAs1AndDeletedAsFalse() throws Exception {
        mockHouseholdFindIds();
        mockServiceRequestClientWithIndividual();

        HouseholdMemberRequest householdMemberRequest = HouseholdMemberRequestTestBuilder.builder()
                .withHouseholdMemberAsHead()
                .withRequestInfo()
                .withApiOperationCreate().build();

        List<HouseholdMember> householdMembers = householdMemberService.create(householdMemberRequest);

        assertEquals(1, householdMembers.stream().findAny().get().getRowVersion());
        assertFalse(householdMembers.stream().findAny().get().getIsDeleted());
    }

}
