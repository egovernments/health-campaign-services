package org.egov.project.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.producer.Producer;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.IndividualTeamCodeRepository;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.util.AdditionalFieldsUtil;
import org.egov.project.web.models.team.BeneficiaryTeamAssignment;
import org.egov.project.web.models.team.BeneficiaryTeamAssignmentRequest;
import org.egov.project.web.models.team.BeneficiaryTeamAssignmentResult;
import org.egov.project.web.models.team.BeneficiaryTeamMapping;
import org.egov.project.web.models.team.BeneficiaryTeamRelocationResult;
import org.egov.project.web.models.team.BeneficiaryTeamRelocationRequest;
import org.egov.project.web.models.team.BeneficiaryTeamRelocation;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.egov.project.Constants.INVALID_PROJECT_ID;
import static org.egov.project.Constants.INVALID_RELOCATION;
import static org.egov.project.Constants.INVALID_TEAM_SELECTOR;
import static org.egov.project.Constants.PROJECT_BENEFICIARY_NOT_FOUND;
import static org.egov.project.Constants.RELOCATION_TOO_LARGE;
import static org.egov.project.Constants.REGISTERED_BY_TEAM;
import static org.egov.project.Constants.USERNAME_NOT_FOUND;
import static org.egov.project.Constants.USER_NOT_IN_TEAM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeneficiaryTeamServiceTest {

    private static final String TENANT_ID = "mhbase";
    private static final String REQUESTER_UUID = "requester-uuid";
    private static final String UPDATE_TOPIC = "update-project-beneficiary-topic";
    private static final String MAPPING_TOPIC = "save-project-beneficiary-team-mapping-topic";
    private static final String TEAM_CODE = "TEAM-2026-09-24-000001";
    private static final String PROJECT_ID = "some-project-id";

    @InjectMocks
    private BeneficiaryTeamService beneficiaryTeamService;

    @Mock
    private ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Mock
    private IndividualTeamCodeRepository individualTeamCodeRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectConfiguration projectConfiguration;

    @Mock
    private Producer producer;

    private RequestInfo requestInfo;

    @BeforeEach
    void setUp() throws InvalidTenantIdException {
        requestInfo = RequestInfo.builder()
                .userInfo(User.builder().uuid(REQUESTER_UUID).build())
                .build();
        lenient().when(projectConfiguration.getUpdateProjectBeneficiaryTopic()).thenReturn(UPDATE_TOPIC);
        lenient().when(projectRepository.validateIds(eq(TENANT_ID), anyList(), eq("id")))
                .thenReturn(List.of(PROJECT_ID));
        lenient().when(projectConfiguration.getBeneficiaryTeamMappingTopic()).thenReturn(MAPPING_TOPIC);
        lenient().when(projectConfiguration.getBeneficiaryTeamSearchBatchSize()).thenReturn(1000);
        lenient().when(projectConfiguration.getBeneficiaryTeamKafkaBatchSize()).thenReturn(100);
        lenient().when(projectConfiguration.getBeneficiaryTeamPageSize()).thenReturn(1000);
    }

    @Test
    @DisplayName("should attribute beneficiaries addressed by clientReferenceId")
    void shouldAssignByClientReferenceIds() throws InvalidTenantIdException {
        stubExistingClientReferenceIds("crid-a", "crid-b");
        stubTeamMembers();
        ProjectBeneficiary first = beneficiary("pb-a", "crid-a", null);
        ProjectBeneficiary second = beneficiary("pb-b", "crid-b", null);
        when(projectBeneficiaryRepository.findByClientReferenceIds(eq(TENANT_ID),
                eq(List.of("crid-a", "crid-b")), eq(PROJECT_ID))).thenReturn(Arrays.asList(first, second));

        BeneficiaryTeamAssignmentResult result = beneficiaryTeamService.updateTeamCode(request(
                BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .teamCode(TEAM_CODE)
                        .clientReferenceIds(List.of("crid-a", "crid-b"))
                        .projectId(PROJECT_ID)
                        .build()));

        assertEquals(2, result.getMatchedCount());
        assertEquals(2, result.getUpdatedCount());
        assertEquals(0, result.getSkippedCount());
        assertEquals(TEAM_CODE, AdditionalFieldsUtil.getFieldValue(first.getAdditionalFields(), REGISTERED_BY_TEAM));
        assertEquals(Integer.valueOf(2), first.getRowVersion());
        assertEquals(REQUESTER_UUID, first.getAuditDetails().getLastModifiedBy());
    }

    @Test
    @DisplayName("should look up clientReferenceIds in configured batches")
    void shouldBatchClientReferenceIdLookups() throws InvalidTenantIdException {
        stubExistingClientReferenceIds("a", "b", "c", "d", "e");
        stubTeamMembers();
        when(projectConfiguration.getBeneficiaryTeamSearchBatchSize()).thenReturn(2);
        when(projectBeneficiaryRepository.findByClientReferenceIds(anyString(), anyList(), eq(PROJECT_ID)))
                .thenReturn(Collections.emptyList());

        beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                .tenantId(TENANT_ID)
                .teamCode(TEAM_CODE)
                .clientReferenceIds(List.of("a", "b", "c", "d", "e"))
                .projectId(PROJECT_ID)
                        .build()));

        // 5 ids at a batch size of 2 -> 3 lookups
        verify(projectBeneficiaryRepository, times(3)).findByClientReferenceIds(anyString(), anyList(), eq(PROJECT_ID));
    }

    @Test
    @DisplayName("should publish beneficiaries and history in kafka sized chunks")
    void shouldPublishInKafkaBatches() throws InvalidTenantIdException {
        stubExistingClientReferenceIds("crid-0", "crid-1", "crid-2", "crid-3", "crid-4");
        stubTeamMembers();
        when(projectConfiguration.getBeneficiaryTeamKafkaBatchSize()).thenReturn(2);
        List<ProjectBeneficiary> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            five.add(beneficiary("pb-" + i, "crid-" + i, null));
        }
        when(projectBeneficiaryRepository.findByClientReferenceIds(anyString(), anyList(), eq(PROJECT_ID)))
                .thenReturn(five);

        beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                .tenantId(TENANT_ID)
                .teamCode(TEAM_CODE)
                .clientReferenceIds(List.of("crid-0", "crid-1", "crid-2", "crid-3", "crid-4"))
                .projectId(PROJECT_ID)
                        .build()));

        // 5 changed rows at a kafka batch of 2 -> 3 pushes each to the beneficiary and history topics
        verify(producer, times(3)).push(eq(TENANT_ID), eq(UPDATE_TOPIC), any());
        verify(producer, times(3)).push(eq(TENANT_ID), eq(MAPPING_TOPIC), any());
    }

    @Test
    @DisplayName("should skip beneficiaries that already carry the requested code")
    void shouldSkipAlreadyAttributedBeneficiaries() throws InvalidTenantIdException {
        stubExistingClientReferenceIds("crid-a", "crid-b");
        stubTeamMembers();
        ProjectBeneficiary tagged = beneficiary("pb-a", "crid-a", TEAM_CODE);
        ProjectBeneficiary untagged = beneficiary("pb-b", "crid-b", null);
        when(projectBeneficiaryRepository.findByClientReferenceIds(anyString(), anyList(), eq(PROJECT_ID)))
                .thenReturn(Arrays.asList(tagged, untagged));

        BeneficiaryTeamAssignmentResult result = beneficiaryTeamService.updateTeamCode(request(
                BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .teamCode(TEAM_CODE)
                        .clientReferenceIds(List.of("crid-a", "crid-b"))
                        .projectId(PROJECT_ID)
                        .build()));

        assertEquals(2, result.getMatchedCount());
        assertEquals(1, result.getUpdatedCount());
        assertEquals(1, result.getSkippedCount());
        // only the changed one is published, and only it gets a history row
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(producer, times(1)).push(eq(TENANT_ID), eq(UPDATE_TOPIC), captor.capture());
        assertEquals(1, ((List<?>) captor.getValue()).size());
    }

    @Test
    @DisplayName("should overwrite a different existing team code and record the previous one")
    void shouldOverwriteAndRecordPreviousTeamCode() throws InvalidTenantIdException {
        stubExistingClientReferenceIds("crid-a");
        stubTeamMembers();
        ProjectBeneficiary previouslyTagged = beneficiary("pb-a", "crid-a", "TEAM-OLD");
        when(projectBeneficiaryRepository.findByClientReferenceIds(anyString(), anyList(), eq(PROJECT_ID)))
                .thenReturn(Collections.singletonList(previouslyTagged));

        beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                .tenantId(TENANT_ID)
                .teamCode(TEAM_CODE)
                .clientReferenceIds(List.of("crid-a"))
                .projectId(PROJECT_ID)
                        .build()));

        assertEquals(TEAM_CODE,
                AdditionalFieldsUtil.getFieldValue(previouslyTagged.getAdditionalFields(), REGISTERED_BY_TEAM));
        BeneficiaryTeamMapping history = captureHistory().get(0);
        assertEquals("TEAM-OLD", history.getPreviousTeamCode());
        assertEquals(TEAM_CODE, history.getTeamCode());
        assertEquals("pb-a", history.getProjectBeneficiaryId());
        assertEquals("crid-a", history.getProjectBeneficiaryClientReferenceId());
        assertEquals(REQUESTER_UUID, history.getAuditDetails().getCreatedBy());
        // relocationReason belongs to the relocate flow, not to an assignment
        assertNull(history.getRelocationReason());
    }

    @Test
    @DisplayName("should keyset page through everything a user registered")
    void shouldKeysetPageByCreatedBy() throws InvalidTenantIdException {
        stubTeamMembers("uu-1");
        when(projectConfiguration.getBeneficiaryTeamPageSize()).thenReturn(2);
        when(projectBeneficiaryRepository.findPageByCreatedBy(eq(TENANT_ID), eq(List.of("uu-1")), eq(PROJECT_ID),
                isNull(), eq(2)))
                .thenReturn(Arrays.asList(beneficiary("pb-1", "crid-1", null), beneficiary("pb-2", "crid-2", null)));
        when(projectBeneficiaryRepository.findPageByCreatedBy(eq(TENANT_ID), eq(List.of("uu-1")), eq(PROJECT_ID),
                eq("pb-2"), eq(2)))
                .thenReturn(Collections.singletonList(beneficiary("pb-3", "crid-3", null)));

        BeneficiaryTeamAssignmentResult result = beneficiaryTeamService.updateTeamCode(request(
                BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .teamCode(TEAM_CODE)
                        .userUuids(List.of("uu-1"))
                        .projectId(PROJECT_ID)
                        .build()));

        assertEquals(3, result.getMatchedCount());
        assertEquals(3, result.getUpdatedCount());
        // the cursor advanced to the last id of the first page, and a short page ended the loop
        verify(projectBeneficiaryRepository, times(1)).findPageByCreatedBy(anyString(), anyList(), eq(PROJECT_ID),
                isNull(), anyInt());
        verify(projectBeneficiaryRepository, times(1)).findPageByCreatedBy(anyString(), anyList(), eq(PROJECT_ID),
                eq("pb-2"), anyInt());
    }

    @Test
    @DisplayName("should resolve usernames to the userUuid held in createdBy")
    void shouldResolveUsernames() throws InvalidTenantIdException {
        stubTeamMembers("uu-1");
        when(individualTeamCodeRepository.resolveUsernamesToUserUuids(eq(TENANT_ID), eq(List.of("staff01")),
                any(RequestInfo.class))).thenReturn(Map.of("staff01", "uu-1"));
        when(projectBeneficiaryRepository.findPageByCreatedBy(anyString(), anyList(), eq(PROJECT_ID), isNull(), anyInt()))
                .thenReturn(Collections.emptyList());

        beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                .tenantId(TENANT_ID)
                .teamCode(TEAM_CODE)
                .usernames(List.of("staff01"))
                .projectId(PROJECT_ID)
                        .build()));

        verify(projectBeneficiaryRepository, times(1)).findPageByCreatedBy(eq(TENANT_ID), eq(List.of("uu-1")),
                eq(PROJECT_ID), isNull(), anyInt());
    }

    @Test
    @DisplayName("should reject the whole call when a username does not resolve")
    void shouldRejectUnknownUsername() {
        stubTeamMembers("uu-1");
        when(individualTeamCodeRepository.resolveUsernamesToUserUuids(anyString(), anyList(),
                any(RequestInfo.class))).thenReturn(Map.of("staff01", "uu-1"));

        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .teamCode(TEAM_CODE)
                        .usernames(List.of("staff01", "ghost"))
                        .projectId(PROJECT_ID)
                        .build())));

        assertEquals(USERNAME_NOT_FOUND, exception.getCode());
        assertTrue(exception.getMessage().contains("ghost"));
        verifyNoInteractions(producer);
    }

    @Test
    @DisplayName("should reject the whole call when a user is not on the team")
    void shouldRejectUserNotOnTheTeam() {
        stubTeamMembers("uu-1");

        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .teamCode(TEAM_CODE)
                        .userUuids(List.of("uu-1", "uu-outsider"))
                        .projectId(PROJECT_ID)
                        .build())));

        assertEquals(USER_NOT_IN_TEAM, exception.getCode());
        assertTrue(exception.getMessage().contains("uu-outsider"));
        verifyNoInteractions(producer);
        verifyNoInteractions(projectBeneficiaryRepository);
    }

    @Test
    @DisplayName("should remove the attribution when the team code is blank")
    void shouldRemoveAttributionWhenTeamCodeBlank() throws InvalidTenantIdException {
        stubExistingClientReferenceIds("crid-a");
        ProjectBeneficiary tagged = beneficiary("pb-a", "crid-a", "TEAM-OLD");
        when(projectBeneficiaryRepository.findByClientReferenceIds(anyString(), anyList(), eq(PROJECT_ID)))
                .thenReturn(Collections.singletonList(tagged));

        BeneficiaryTeamAssignmentResult result = beneficiaryTeamService.updateTeamCode(request(
                BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .clientReferenceIds(List.of("crid-a"))
                        .projectId(PROJECT_ID)
                        .build()));

        assertNull(result.getTeamCode());
        assertEquals(1, result.getUpdatedCount());
        assertNull(AdditionalFieldsUtil.getFieldValue(tagged.getAdditionalFields(), REGISTERED_BY_TEAM));
        // a removal has no code to validate membership against
        verifyNoInteractions(individualTeamCodeRepository);
        assertEquals("TEAM-OLD", captureHistory().get(0).getPreviousTeamCode());
    }

    @Test
    @DisplayName("should reject a request that mixes the two addressing modes")
    void shouldRejectBothModesAtOnce() throws InvalidTenantIdException {
        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .teamCode(TEAM_CODE)
                        .clientReferenceIds(List.of("crid-a"))
                        .userUuids(List.of("uu-1"))
                        .projectId(PROJECT_ID)
                        .build())));

        assertEquals(INVALID_TEAM_SELECTOR, exception.getCode());
        verifyNoInteractions(projectBeneficiaryRepository);
        verifyNoInteractions(producer);
    }

    @Test
    @DisplayName("should reject a request with no selector at all")
    void shouldRejectNoSelector() throws InvalidTenantIdException {
        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .teamCode(TEAM_CODE)
                        .projectId(PROJECT_ID)
                        .build())));

        assertEquals(INVALID_TEAM_SELECTOR, exception.getCode());
        verifyNoInteractions(projectBeneficiaryRepository);
    }

    @Test
    @DisplayName("should trim and de-duplicate selector values")
    void shouldNormaliseSelectorValues() throws InvalidTenantIdException {
        stubExistingClientReferenceIds("crid-a");
        stubTeamMembers();
        when(projectBeneficiaryRepository.findByClientReferenceIds(anyString(), anyList(), eq(PROJECT_ID)))
                .thenReturn(Collections.emptyList());

        beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                .tenantId(TENANT_ID)
                .teamCode(TEAM_CODE)
                .clientReferenceIds(Arrays.asList("crid-a", "  crid-a  ", "   ", null))
                .projectId(PROJECT_ID)
                        .build()));

        verify(projectBeneficiaryRepository, times(1)).findByClientReferenceIds(eq(TENANT_ID),
                eq(List.of("crid-a")), eq(PROJECT_ID));
    }

    @Test
    @DisplayName("should pass projectId through as a narrowing filter when given")
    void shouldNarrowByProjectId() throws InvalidTenantIdException {
        stubExistingClientReferenceIds("crid-a");
        stubTeamMembers();
        when(projectBeneficiaryRepository.findByClientReferenceIds(anyString(), anyList(), eq("project-1")))
                .thenReturn(Collections.emptyList());

        beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                .tenantId(TENANT_ID)
                .teamCode(TEAM_CODE)
                .clientReferenceIds(List.of("crid-a"))
                .projectId("project-1")
                .build()));

        verify(projectBeneficiaryRepository, times(1)).findByClientReferenceIds(eq(TENANT_ID), anyList(),
                eq("project-1"));
        verify(projectBeneficiaryRepository, never()).findByClientReferenceIds(anyString(), anyList(), eq(PROJECT_ID));
    }

    /**
     * Mode A validates before it writes, so every mode A test has to say which clientReferenceIds
     * exist.
     */
    @Test
    @DisplayName("should reject the whole call when any clientReferenceId does not exist")
    void shouldRejectUnknownClientReferenceId() throws InvalidTenantIdException {
        stubTeamMembers();
        stubExistingClientReferenceIds("crid-a");

        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .teamCode(TEAM_CODE)
                        .clientReferenceIds(List.of("crid-a", "crid-ghost"))
                        .projectId(PROJECT_ID)
                        .build())));

        assertEquals(PROJECT_BENEFICIARY_NOT_FOUND, exception.getCode());
        assertTrue(exception.getMessage().contains("crid-ghost"));
        // nothing was fetched or published - the bad id is caught before the write phase
        verify(projectBeneficiaryRepository, never()).findByClientReferenceIds(anyString(), anyList(), any());
        verifyNoInteractions(producer);
    }

    @Test
    @DisplayName("should validate every batch before fetching anything to write")
    void shouldValidateBeforeWriting() throws InvalidTenantIdException {
        stubTeamMembers();
        stubExistingClientReferenceIds("crid-a", "crid-b");
        when(projectConfiguration.getBeneficiaryTeamSearchBatchSize()).thenReturn(1);
        when(projectBeneficiaryRepository.findByClientReferenceIds(anyString(), anyList(), any()))
                .thenReturn(Collections.emptyList());

        beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                .tenantId(TENANT_ID)
                .teamCode(TEAM_CODE)
                .clientReferenceIds(List.of("crid-a", "crid-b"))
                .projectId(PROJECT_ID)
                        .build()));

        // at a batch size of 1 both validation batches must complete before the first row is
        // fetched, otherwise a bad id in batch 2 could not stop batch 1 from being written
        InOrder inOrder = inOrder(projectBeneficiaryRepository);
        inOrder.verify(projectBeneficiaryRepository, times(2))
                .findExistingClientReferenceIds(anyString(), anyList(), any());
        inOrder.verify(projectBeneficiaryRepository, times(2))
                .findByClientReferenceIds(anyString(), anyList(), any());
    }

    @Test
    @DisplayName("should cap the id list in the error when a large batch is mostly unknown")
    void shouldCapTheMissingIdListInTheError() throws InvalidTenantIdException {
        stubTeamMembers();
        stubExistingClientReferenceIds();
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            many.add("ghost-" + i);
        }

        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .teamCode(TEAM_CODE)
                        .clientReferenceIds(many)
                        .projectId(PROJECT_ID)
                        .build())));

        assertEquals(PROJECT_BENEFICIARY_NOT_FOUND, exception.getCode());
        assertTrue(exception.getMessage().contains("25 clientReferenceIds do not exist"));
        assertTrue(exception.getMessage().contains("and 5 more"));
    }

    @Test
    @DisplayName("should treat a beneficiary outside the given project as missing")
    void shouldTreatOutOfProjectBeneficiaryAsMissing() throws InvalidTenantIdException {
        stubTeamMembers();
        // the projectId narrowed query finds nothing
        when(projectBeneficiaryRepository.findExistingClientReferenceIds(anyString(), anyList(), eq("project-9")))
                .thenReturn(Collections.emptySet());

        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .teamCode(TEAM_CODE)
                        .clientReferenceIds(List.of("crid-a"))
                        .projectId("project-9")
                        .build())));

        assertEquals(PROJECT_BENEFICIARY_NOT_FOUND, exception.getCode());
        assertTrue(exception.getMessage().contains("in project project-9"));
    }

    // ---- relocate ----

    @Test
    @DisplayName("should relocate an entire team, reading all ids before writing anything")
    void shouldRelocateAWholeTeam() throws InvalidTenantIdException {
        when(projectConfiguration.getBeneficiaryTeamRelocatePageSize()).thenReturn(2);
        when(projectConfiguration.getBeneficiaryTeamRelocateMaxRecords()).thenReturn(1000);
        // two full pages then a short one ends the read
        when(projectBeneficiaryRepository.findIdsByRegisteredByTeam(TENANT_ID, "TEAM-OLD", PROJECT_ID, 2, 0))
                .thenReturn(List.of("pb-1", "pb-2"));
        when(projectBeneficiaryRepository.findIdsByRegisteredByTeam(TENANT_ID, "TEAM-OLD", PROJECT_ID, 2, 2))
                .thenReturn(List.of("pb-3"));
        when(projectBeneficiaryRepository.findByIds(eq(TENANT_ID), anyList()))
                .thenReturn(List.of(beneficiary("pb-1", "crid-1", "TEAM-OLD"),
                        beneficiary("pb-2", "crid-2", "TEAM-OLD"),
                        beneficiary("pb-3", "crid-3", "TEAM-OLD")));

        BeneficiaryTeamRelocationResult result = beneficiaryTeamService.relocateTeamCode(relocateRequest(
                BeneficiaryTeamRelocation.builder()
                        .tenantId(TENANT_ID)
                        .fromTeamCode("TEAM-OLD")
                        .toTeamCode(TEAM_CODE)
                        .relocationReason("Distributor reassigned to Ward 7")
                        .projectId(PROJECT_ID)
                        .build()));

        assertEquals("TEAM-OLD", result.getFromTeamCode());
        assertEquals(TEAM_CODE, result.getToTeamCode());
        assertEquals(3, result.getRelocatedCount());

        InOrder inOrder = inOrder(projectBeneficiaryRepository);
        // the whole read phase precedes the write phase: the predicate being paged is the one being
        // mutated, so a read interleaved with writes would skip rows
        inOrder.verify(projectBeneficiaryRepository, times(2))
                .findIdsByRegisteredByTeam(anyString(), anyString(), any(), anyInt(), anyInt());
        inOrder.verify(projectBeneficiaryRepository).findByIds(anyString(), anyList());
        // and the team predicate is never consulted again once writing starts
        verify(projectBeneficiaryRepository, times(2))
                .findIdsByRegisteredByTeam(anyString(), anyString(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("should record the relocation reason and previous team on every history row")
    void shouldRecordRelocationReasonAndPreviousTeam() throws InvalidTenantIdException {
        when(projectConfiguration.getBeneficiaryTeamRelocatePageSize()).thenReturn(10);
        when(projectConfiguration.getBeneficiaryTeamRelocateMaxRecords()).thenReturn(1000);
        when(projectBeneficiaryRepository.findIdsByRegisteredByTeam(anyString(), anyString(), any(), anyInt(),
                anyInt())).thenReturn(List.of("pb-1"));
        when(projectBeneficiaryRepository.findByIds(eq(TENANT_ID), anyList()))
                .thenReturn(List.of(beneficiary("pb-1", "crid-1", "TEAM-OLD")));

        beneficiaryTeamService.relocateTeamCode(relocateRequest(BeneficiaryTeamRelocation.builder()
                .tenantId(TENANT_ID)
                .fromTeamCode("TEAM-OLD")
                .toTeamCode(TEAM_CODE)
                .relocationReason("Staff transfer")
                .projectId(PROJECT_ID)
                        .build()));

        BeneficiaryTeamMapping history = captureHistory().get(0);
        assertEquals("Staff transfer", history.getRelocationReason());
        assertEquals("TEAM-OLD", history.getPreviousTeamCode());
        assertEquals(TEAM_CODE, history.getTeamCode());
    }

    @Test
    @DisplayName("should relocate by clientReferenceId without needing a fromTeamCode")
    void shouldRelocateByClientReferenceIds() throws InvalidTenantIdException {
        stubExistingClientReferenceIds("crid-a");
        when(projectBeneficiaryRepository.findByClientReferenceIds(anyString(), anyList(), any()))
                .thenReturn(List.of(beneficiary("pb-a", "crid-a", "TEAM-WHATEVER")));

        BeneficiaryTeamRelocationResult result = beneficiaryTeamService.relocateTeamCode(relocateRequest(
                BeneficiaryTeamRelocation.builder()
                        .tenantId(TENANT_ID)
                        .toTeamCode(TEAM_CODE)
                        .clientReferenceIds(List.of("crid-a"))
                        .relocationReason("Manual correction")
                        .projectId(PROJECT_ID)
                        .build()));

        assertNull(result.getFromTeamCode());
        assertEquals(1, result.getRelocatedCount());
        // no team predicate read at all in this mode
        verify(projectBeneficiaryRepository, never()).findIdsByRegisteredByTeam(anyString(), anyString(), any(),
                anyInt(), anyInt());
        BeneficiaryTeamMapping history = captureHistory().get(0);
        assertEquals("TEAM-WHATEVER", history.getPreviousTeamCode());
        assertEquals("Manual correction", history.getRelocationReason());
    }

    @Test
    @DisplayName("should validate both ends of the move in one call to the individual service")
    void shouldValidateBothTeamCodes() throws InvalidTenantIdException {
        when(projectConfiguration.getBeneficiaryTeamRelocatePageSize()).thenReturn(10);
        when(projectConfiguration.getBeneficiaryTeamRelocateMaxRecords()).thenReturn(1000);
        when(projectBeneficiaryRepository.findIdsByRegisteredByTeam(anyString(), anyString(), any(), anyInt(),
                anyInt())).thenReturn(Collections.emptyList());

        beneficiaryTeamService.relocateTeamCode(relocateRequest(BeneficiaryTeamRelocation.builder()
                .tenantId(TENANT_ID)
                .fromTeamCode("TEAM-OLD")
                .toTeamCode(TEAM_CODE)
                .relocationReason("why")
                .projectId(PROJECT_ID)
                        .build()));

        verify(individualTeamCodeRepository, times(1)).validateTeamCodesAssignable(eq(TENANT_ID),
                eq(List.of("TEAM-OLD", TEAM_CODE)), any(RequestInfo.class));
    }

    @Test
    @DisplayName("should reject relocating a team onto itself")
    void shouldRejectSameFromAndTo() {
        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.relocateTeamCode(relocateRequest(
                        BeneficiaryTeamRelocation.builder()
                                .tenantId(TENANT_ID)
                                .fromTeamCode(TEAM_CODE)
                                .toTeamCode(TEAM_CODE)
                                .relocationReason("why")
                                .projectId(PROJECT_ID)
                        .build())));

        assertEquals(INVALID_RELOCATION, exception.getCode());
        verifyNoInteractions(projectBeneficiaryRepository);
        verifyNoInteractions(individualTeamCodeRepository);
    }

    @Test
    @DisplayName("should reject a relocation with no reason, before any read")
    void shouldRejectBlankRelocationReason() {
        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.relocateTeamCode(relocateRequest(
                        BeneficiaryTeamRelocation.builder()
                                .tenantId(TENANT_ID)
                                .fromTeamCode("TEAM-OLD")
                                .toTeamCode(TEAM_CODE)
                                .relocationReason("   ")
                                .projectId(PROJECT_ID)
                        .build())));

        assertEquals(INVALID_RELOCATION, exception.getCode());
        verifyNoInteractions(projectBeneficiaryRepository);
    }

    @Test
    @DisplayName("should reject a relocation that mixes fromTeamCode with clientReferenceIds")
    void shouldRejectMixedRelocationSelectors() {
        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.relocateTeamCode(relocateRequest(
                        BeneficiaryTeamRelocation.builder()
                                .tenantId(TENANT_ID)
                                .fromTeamCode("TEAM-OLD")
                                .toTeamCode(TEAM_CODE)
                                .clientReferenceIds(List.of("crid-a"))
                                .relocationReason("why")
                                .projectId(PROJECT_ID)
                        .build())));

        assertEquals(INVALID_TEAM_SELECTOR, exception.getCode());
        verifyNoInteractions(projectBeneficiaryRepository);
    }

    @Test
    @DisplayName("should refuse a team larger than the configured cap without writing anything")
    void shouldRejectOversizedRelocation() throws InvalidTenantIdException {
        when(projectConfiguration.getBeneficiaryTeamRelocatePageSize()).thenReturn(2);
        when(projectConfiguration.getBeneficiaryTeamRelocateMaxRecords()).thenReturn(3);
        when(projectBeneficiaryRepository.findIdsByRegisteredByTeam(anyString(), anyString(), any(), anyInt(),
                anyInt())).thenReturn(List.of("pb-1", "pb-2"));

        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.relocateTeamCode(relocateRequest(
                        BeneficiaryTeamRelocation.builder()
                                .tenantId(TENANT_ID)
                                .fromTeamCode("TEAM-OLD")
                                .toTeamCode(TEAM_CODE)
                                .relocationReason("why")
                                .projectId(PROJECT_ID)
                        .build())));

        assertEquals(RELOCATION_TOO_LARGE, exception.getCode());
        verify(projectBeneficiaryRepository, never()).findByIds(anyString(), anyList());
        verifyNoInteractions(producer);
    }

    @Test
    @DisplayName("should skip beneficiaries already on the destination team")
    void shouldSkipBeneficiariesAlreadyOnDestinationTeam() throws InvalidTenantIdException {
        when(projectConfiguration.getBeneficiaryTeamRelocatePageSize()).thenReturn(10);
        when(projectConfiguration.getBeneficiaryTeamRelocateMaxRecords()).thenReturn(1000);
        when(projectBeneficiaryRepository.findIdsByRegisteredByTeam(anyString(), anyString(), any(), anyInt(),
                anyInt())).thenReturn(List.of("pb-1", "pb-2"));
        when(projectBeneficiaryRepository.findByIds(eq(TENANT_ID), anyList()))
                .thenReturn(List.of(beneficiary("pb-1", "crid-1", "TEAM-OLD"),
                        beneficiary("pb-2", "crid-2", TEAM_CODE)));

        BeneficiaryTeamRelocationResult result = beneficiaryTeamService.relocateTeamCode(relocateRequest(
                BeneficiaryTeamRelocation.builder()
                        .tenantId(TENANT_ID)
                        .fromTeamCode("TEAM-OLD")
                        .toTeamCode(TEAM_CODE)
                        .relocationReason("why")
                        .projectId(PROJECT_ID)
                        .build()));

        assertEquals(2, result.getMatchedCount());
        assertEquals(1, result.getRelocatedCount());
        assertEquals(1, result.getSkippedCount());
    }

    // ---- projectId is mandatory and must name a real project ----

    @Test
    @DisplayName("should reject an assignment with no projectId before touching anything")
    void shouldRejectAssignmentWithoutProjectId() {
        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .teamCode(TEAM_CODE)
                        .clientReferenceIds(List.of("crid-a"))
                        .build())));

        assertEquals(INVALID_PROJECT_ID, exception.getCode());
        verifyNoInteractions(projectBeneficiaryRepository);
        verifyNoInteractions(individualTeamCodeRepository);
        verifyNoInteractions(producer);
    }

    @Test
    @DisplayName("should reject an assignment whose projectId does not exist")
    void shouldRejectAssignmentWithUnknownProjectId() throws InvalidTenantIdException {
        when(projectRepository.validateIds(eq(TENANT_ID), anyList(), eq("id")))
                .thenReturn(Collections.emptyList());

        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .teamCode(TEAM_CODE)
                        .clientReferenceIds(List.of("crid-a"))
                        .projectId("ghost-project")
                        .build())));

        assertEquals(INVALID_PROJECT_ID, exception.getCode());
        assertTrue(exception.getMessage().contains("ghost-project"));
        verifyNoInteractions(projectBeneficiaryRepository);
        verifyNoInteractions(producer);
    }

    @Test
    @DisplayName("should reject a relocation whose projectId does not exist")
    void shouldRejectRelocationWithUnknownProjectId() throws InvalidTenantIdException {
        when(projectRepository.validateIds(eq(TENANT_ID), anyList(), eq("id")))
                .thenReturn(Collections.emptyList());

        CustomException exception = assertThrows(CustomException.class,
                () -> beneficiaryTeamService.relocateTeamCode(relocateRequest(
                        BeneficiaryTeamRelocation.builder()
                                .tenantId(TENANT_ID)
                                .fromTeamCode("TEAM-OLD")
                                .toTeamCode(TEAM_CODE)
                                .relocationReason("why")
                                .projectId("ghost-project")
                                .build())));

        assertEquals(INVALID_PROJECT_ID, exception.getCode());
        // the project is checked before the team codes, so nothing reaches the individual service
        verifyNoInteractions(individualTeamCodeRepository);
        verifyNoInteractions(projectBeneficiaryRepository);
        verifyNoInteractions(producer);
    }

    @Test
    @DisplayName("should scope a mode B assignment to the given project")
    void shouldScopeUserAssignmentToTheProject() throws InvalidTenantIdException {
        stubTeamMembers("uu-1");
        when(projectBeneficiaryRepository.findPageByCreatedBy(anyString(), anyList(), anyString(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        beneficiaryTeamService.updateTeamCode(request(BeneficiaryTeamAssignment.builder()
                .tenantId(TENANT_ID)
                .teamCode(TEAM_CODE)
                .userUuids(List.of("uu-1"))
                .projectId(PROJECT_ID)
                .build()));

        // without this the assignment would reach every beneficiary the user ever registered,
        // including earlier campaigns under a different team
        verify(projectBeneficiaryRepository, times(1)).findPageByCreatedBy(eq(TENANT_ID), eq(List.of("uu-1")),
                eq(PROJECT_ID), isNull(), anyInt());
    }

    private BeneficiaryTeamRelocationRequest relocateRequest(BeneficiaryTeamRelocation relocation) {
        return BeneficiaryTeamRelocationRequest.builder()
                .requestInfo(requestInfo)
                .beneficiaryTeamRelocation(relocation)
                .build();
    }

    private void stubExistingClientReferenceIds(String... clientReferenceIds) throws InvalidTenantIdException {
        lenient().when(projectBeneficiaryRepository.findExistingClientReferenceIds(anyString(), anyList(), any()))
                .thenAnswer(invocation -> {
                    List<String> requested = invocation.getArgument(1);
                    Set<String> existing = Set.of(clientReferenceIds);
                    return requested.stream().filter(existing::contains).collect(java.util.stream.Collectors.toSet());
                });
    }

    private void stubTeamMembers(String... memberUserUuids) {
        lenient().when(individualTeamCodeRepository.fetchAssignableTeamMemberUserUuids(eq(TENANT_ID),
                eq(TEAM_CODE), any(RequestInfo.class))).thenReturn(Set.of(memberUserUuids));
    }

    @SuppressWarnings("unchecked")
    private List<BeneficiaryTeamMapping> captureHistory() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(producer).push(eq(TENANT_ID), eq(MAPPING_TOPIC), captor.capture());
        return (List<BeneficiaryTeamMapping>) captor.getValue();
    }

    private BeneficiaryTeamAssignmentRequest request(BeneficiaryTeamAssignment assignment) {
        return BeneficiaryTeamAssignmentRequest.builder()
                .requestInfo(requestInfo)
                .beneficiaryTeamAssignment(assignment)
                .build();
    }

    private ProjectBeneficiary beneficiary(String id, String clientReferenceId, String teamCode) {
        List<Field> fields = new ArrayList<>();
        fields.add(Field.builder().key("some_other_key").value("keep-me").build());
        if (teamCode != null) {
            fields.add(Field.builder().key(REGISTERED_BY_TEAM).value(teamCode).build());
        }
        ProjectBeneficiary beneficiary = ProjectBeneficiaryTestBuilderLite.build(id, clientReferenceId);
        beneficiary.setAdditionalFields(AdditionalFields.builder()
                .schema("ProjectBeneficiary").version(1).fields(fields).build());
        return beneficiary;
    }

    /**
     * The shared ProjectBeneficiaryTestBuilder hardcodes one id, so these tests need distinct rows.
     */
    private static final class ProjectBeneficiaryTestBuilderLite {
        private static ProjectBeneficiary build(String id, String clientReferenceId) {
            ProjectBeneficiary beneficiary = ProjectBeneficiary.builder().build();
            beneficiary.setId(id);
            beneficiary.setClientReferenceId(clientReferenceId);
            beneficiary.setTenantId(TENANT_ID);
            beneficiary.setProjectId("some-project-id");
            beneficiary.setRowVersion(1);
            beneficiary.setAuditDetails(org.egov.common.contract.models.AuditDetails.builder()
                    .createdBy("creator").createdTime(1L).lastModifiedBy("creator").lastModifiedTime(1L).build());
            return beneficiary;
        }
    }
}
