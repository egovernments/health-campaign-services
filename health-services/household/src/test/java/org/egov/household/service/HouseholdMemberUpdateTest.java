package org.egov.household.service;

import org.egov.common.ds.Tuple;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.household.HouseholdMemberRequest;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.validator.Validator;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.helper.HouseholdMemberRequestTestBuilder;
import org.egov.household.helper.HouseholdMemberTestBuilder;
import org.egov.household.household.member.validators.HmHouseholdHeadValidator;
import org.egov.household.household.member.validators.HmIndividualValidator;
import org.egov.household.household.member.validators.HmIsDeletedValidator;
import org.egov.household.household.member.validators.HmNonExistentEntityValidator;
import org.egov.household.household.member.validators.HmNullIdValidator;
import org.egov.household.household.member.validators.HmRowVersionValidator;
import org.egov.household.household.member.validators.HmUniqueEntityValidator;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Mock
    private HmNullIdValidator hmNullIdValidator;

    @Mock
    private HmNonExistentEntityValidator hmNonExistentEntityValidator;


    @Mock
    private HmUniqueEntityValidator hmUniqueEntityValidator;

    @Mock
    private HmIsDeletedValidator hmIsDeletedValidator;

    @Mock
    private HmRowVersionValidator hmRowVersionValidator;

    @Mock
    private HmIndividualValidator hmIndividualValidator;

    @Mock
    private HmHouseholdHeadValidator hmHouseholdHeadValidator;

    @Mock
    private HouseholdMemberEnrichmentService householdMemberEnrichmentService;

    private List<Validator<HouseholdMemberBulkRequest, HouseholdMember>> validators;


    @BeforeEach
    void setUp() {
        validators = Arrays.asList(
                hmNullIdValidator,
                hmNonExistentEntityValidator,
                hmUniqueEntityValidator,
                hmRowVersionValidator,
                hmIsDeletedValidator,
                hmIndividualValidator,
                hmHouseholdHeadValidator);
        ReflectionTestUtils.setField(householdMemberService, "validators", validators);
        lenient().when(householdMemberConfiguration.getCreateTopic()).thenReturn("create-topic");
        lenient().when(householdMemberConfiguration.getUpdateTopic()).thenReturn("update-topic");
    }

    private void mockHouseholdFindIds() {
        when(householdService.findById(
                any(List.class),
                any(String.class),
                any(Boolean.class)
        )).thenReturn( new Tuple(1L,
                Collections.singletonList(
                        Household.builder().id("some-household-id").clientReferenceId("some-client-ref-id").build())
            )
        );
    }

    private void mockServiceRequestClientWithIndividual() throws Exception {
        when(serviceRequestClient.fetchResult(
                any(StringBuilder.class),
                any(),
                eq(IndividualBulkResponse.class))
        ).thenReturn(
                IndividualBulkResponse.builder().individual(Collections.singletonList(Individual.builder()
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
    @DisplayName("should check row versions if entities are valid")
    @Disabled
    void shouldCheckRowVersionsIfEntitiesAreValid() {
        HouseholdMemberRequest request = HouseholdMemberRequestTestBuilder.builder()
                .withRequestInfo()
                .withHouseholdMember()
                .build();

        List<HouseholdMember> householdMembers = new ArrayList<>();
        householdMembers.add(HouseholdMemberTestBuilder.builder().withHouseholdIdAndIndividualId()
                .build());

        assertDoesNotThrow(() -> householdMemberService.update(request));
    }

    @Test
    @DisplayName("should throw exception if row versions do not match")
    @Disabled
    void shouldThrowExceptionIfRowVersionsDoNotMatch() {
        HouseholdMemberRequest request = HouseholdMemberRequestTestBuilder.builder()
                .withRequestInfo()
                .withHouseholdMember()
                .withRowVersion(2)
                .build();

        List<HouseholdMember> householdMembers = new ArrayList<>();
        householdMembers.add(HouseholdMemberTestBuilder.builder().withHouseholdIdAndIndividualId()
                        .withErrors()
                .build());
        assertThrows(CustomException.class, () -> householdMemberService.update(request));
    }

    @Test
    @DisplayName("should save the updated entities")
    void shouldSaveTheUpdatedEntities() throws Exception {
        HouseholdMemberRequest request = HouseholdMemberRequestTestBuilder.builder()
                .withRequestInfo()
                .withHouseholdMember()
                .withRowVersion(1)
                .build();

        List<HouseholdMember> householdMembers = new ArrayList<>();
        householdMembers.add(HouseholdMemberTestBuilder.builder().withHouseholdIdAndIndividualId()
                .build());

       householdMemberService.update(request);
       verify(householdMemberRepository, times(1)).save(anyList(), anyString());
    }

}
