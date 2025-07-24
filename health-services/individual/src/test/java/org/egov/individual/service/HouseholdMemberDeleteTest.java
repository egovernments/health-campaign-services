package org.egov.individual.service;

import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.models.household.HouseholdMemberRequest;
import org.egov.common.validator.Validator;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.helper.HouseholdMemberRequestTestBuilder;
import org.egov.individual.validators.member.HmHouseholdHeadValidator;
import org.egov.individual.validators.member.HmIndividualValidator;
import org.egov.individual.validators.member.HmIsDeletedValidator;
import org.egov.individual.validators.member.HmNonExistentEntityValidator;
import org.egov.individual.validators.member.HmNullIdValidator;
import org.egov.individual.validators.member.HmRowVersionValidator;
import org.egov.individual.validators.member.HmUniqueEntityValidator;
import org.egov.individual.repository.HouseholdMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HouseholdMemberDeleteTest {

    @InjectMocks
    HouseholdMemberService householdMemberService;

    @Mock
    HouseholdMemberRepository householdMemberRepository;

    @Mock
    IndividualProperties householdMemberConfiguration;

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
        lenient().when(householdMemberConfiguration.getCreateHouseholdMemberTopic()).thenReturn("create-topic");
        lenient().when(householdMemberConfiguration.getUpdateHouseholdMemberTopic()).thenReturn("update-topic");
        lenient().when(householdMemberConfiguration.getDeleteHouseholdMemberTopic()).thenReturn("delete-topic");

    }

    @Test
    @DisplayName("should delete the individual and related entities")
    void shouldDeleteTheIndividualAndRelatedEntities() {
        HouseholdMemberRequest request = HouseholdMemberRequestTestBuilder.builder()
                .withRequestInfo()
                .withDeletedHouseholdMember()
                .build();

        householdMemberService.delete(request);
        verify(householdMemberRepository, times(1)).save(anyList(), anyString());

    }


}
