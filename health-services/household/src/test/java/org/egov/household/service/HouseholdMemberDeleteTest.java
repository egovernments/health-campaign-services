package org.egov.household.service;

import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.validator.Validator;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.helper.HouseholdMemberRequestTestBuilder;
import org.egov.household.helper.HouseholdMemberTestBuilder;
import org.egov.household.household.member.validators.HouseholdHeadValidator;
import org.egov.household.household.member.validators.IndividualValidator;
import org.egov.household.household.member.validators.IsDeletedValidator;
import org.egov.household.household.member.validators.NonExistentEntityValidator;
import org.egov.household.household.member.validators.NullIdValidator;
import org.egov.household.household.member.validators.RowVersionValidator;
import org.egov.household.household.member.validators.UniqueEntityValidator;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.household.web.models.Household;
import org.egov.household.web.models.HouseholdMember;
import org.egov.household.web.models.HouseholdMemberBulkRequest;
import org.egov.household.web.models.HouseholdMemberRequest;
import org.egov.household.web.models.Individual;
import org.egov.household.web.models.IndividualResponse;
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
class HouseholdMemberDeleteTest {

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
    private NullIdValidator nullIdValidator;

    @Mock
    private NonExistentEntityValidator nonExistentEntityValidator;


    @Mock
    private UniqueEntityValidator uniqueEntityValidator;

    @Mock
    private IsDeletedValidator isDeletedValidator;

    @Mock
    private RowVersionValidator rowVersionValidator;

    @Mock
    private IndividualValidator individualValidator;

    @Mock
    private HouseholdHeadValidator householdHeadValidator;

    @Mock
    private HouseholdMemberEnrichmentService householdMemberEnrichmentService;

    private List<Validator<HouseholdMemberBulkRequest, HouseholdMember>> validators;


    @BeforeEach
    void setUp() {
        validators = Arrays.asList(
                nullIdValidator,
                nonExistentEntityValidator,
                uniqueEntityValidator,
                rowVersionValidator,
                isDeletedValidator,
                individualValidator,
                householdHeadValidator);
        ReflectionTestUtils.setField(householdMemberService, "validators", validators);
        lenient().when(householdMemberConfiguration.getCreateTopic()).thenReturn("create-topic");
        lenient().when(householdMemberConfiguration.getUpdateTopic()).thenReturn("update-topic");
        lenient().when(householdMemberConfiguration.getDeleteTopic()).thenReturn("delete-topic");

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
