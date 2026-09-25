package org.egov.individual.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.helper.RequestInfoTestBuilder;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.individual.Individual;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.helper.IndividualTestBuilder;
import org.egov.individual.repository.IndividualRepository;
import org.egov.individual.repository.IndividualRepository.SelectorColumn;
import org.egov.individual.repository.TeamCodeRepository;
import org.egov.individual.util.AdditionalFieldsUtil;
import org.egov.individual.web.models.TeamAssignment;
import org.egov.individual.web.models.TeamAssignmentEntry;
import org.egov.individual.web.models.TeamAssignmentRequest;
import org.egov.individual.web.models.TeamAssignmentResult;
import org.egov.individual.web.models.TeamCode;
import org.egov.individual.web.models.TeamCodeMapping;
import org.egov.individual.web.models.TeamCodeGenerationRequest;
import org.egov.individual.web.models.TeamCodeSearch;
import org.egov.individual.web.models.TeamCodeStatus;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.egov.individual.Constants.AMBIGUOUS_SELECTOR;
import static org.egov.individual.Constants.INDIVIDUAL_NOT_FOUND;
import static org.egov.individual.Constants.INVALID_SELECTOR;
import static org.egov.individual.Constants.INVALID_TEAM_CODE_COUNT;
import static org.egov.individual.Constants.INVALID_TENANT_ID;
import static org.egov.individual.Constants.TEAM_ASSIGNMENT_BATCH_TOO_LARGE;
import static org.egov.individual.Constants.TEAM_CODE_FIELD_KEY;
import static org.egov.individual.Constants.TEAM_CODE_GENERATION_FAILED;
import static org.egov.individual.Constants.TEAM_CODE_INACTIVE;
import static org.egov.individual.Constants.TEAM_CODE_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamCodeServiceTest {

    private static final String TENANT_ID = "taraba";

    private static final String REQUESTER_UUID = "some-uuid";

    private static final String UPDATE_TOPIC = "update-individual-topic";
    private static final String SAVE_TEAM_CODE_TOPIC = "save-individual-team-code-topic";
    private static final String SAVE_TEAM_CODE_MAPPING_TOPIC = "save-individual-team-code-mapping-topic";
    private static final String UPDATE_TEAM_CODE_MAPPING_TOPIC = "update-individual-team-code-mapping-topic";

    @InjectMocks
    private TeamCodeService teamCodeService;

    @Mock
    private IdGenService idGenService;

    @Mock
    private IndividualRepository individualRepository;

    @Mock
    private TeamCodeRepository teamCodeRepository;

    @Mock
    private IndividualProperties properties;

    @Mock
    private Producer producer;

    private RequestInfo requestInfo;

    @BeforeEach
    void setUp() {
        requestInfo = RequestInfoTestBuilder.builder().withCompleteRequestInfo().build();
        lenient().when(properties.getTeamCodeGenerateMax()).thenReturn(100);
        lenient().when(properties.getTeamCodeIdFormat()).thenReturn("team.code");
        lenient().when(properties.getTeamAssignBatchSize()).thenReturn(100);
        lenient().when(properties.getUpdateIndividualTopic()).thenReturn(UPDATE_TOPIC);
        lenient().when(properties.getSaveTeamCodeTopic()).thenReturn(SAVE_TEAM_CODE_TOPIC);
        lenient().when(properties.getSaveTeamCodeMappingTopic()).thenReturn(SAVE_TEAM_CODE_MAPPING_TOPIC);
        lenient().when(properties.getUpdateTeamCodeMappingTopic()).thenReturn(UPDATE_TEAM_CODE_MAPPING_TOPIC);
    }

    @Test
    @DisplayName("should mint exactly the requested number of codes in one idgen round trip")
    void shouldMintExactlyTheRequestedNumberOfCodes() throws InvalidTenantIdException {
        when(idGenService.getIdList(any(RequestInfo.class), eq(TENANT_ID), eq("team.code"), isNull(), eq(3)))
                .thenReturn(Arrays.asList("TEAM-1", "TEAM-2", "TEAM-3"));

        List<String> codes = teamCodeService.generateTeamCodes(generationRequest(3));

        assertEquals(Arrays.asList("TEAM-1", "TEAM-2", "TEAM-3"), codes);
        verify(idGenService, times(1)).getIdList(any(RequestInfo.class), anyString(), anyString(), isNull(), eq(3));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> rowsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(producer, times(1)).push(eq(TENANT_ID), eq(SAVE_TEAM_CODE_TOPIC), rowsCaptor.capture());
        List<TeamCode> rows = (List<TeamCode>) rowsCaptor.getValue();
        assertEquals(3, rows.size());
        rows.forEach(row -> {
            assertEquals(TENANT_ID, row.getTenantId());
            assertEquals(REQUESTER_UUID, row.getAuditDetails().getCreatedBy());
            assertNotNull(row.getId());
            assertEquals(Integer.valueOf(1), row.getRowVersion());
            assertEquals(Boolean.FALSE, row.getIsDeleted());
            // status and assignedCount are derived on read, so a minted row carries neither
            assertNull(row.getStatus());
            assertNull(row.getAssignedCount());
        });
        assertEquals(Arrays.asList("TEAM-1", "TEAM-2", "TEAM-3"),
                rows.stream().map(TeamCode::getTeamCode).toList());
    }

    @Test
    @DisplayName("should reject a count above the configured maximum before calling idgen")
    void shouldRejectCountAboveConfiguredMaximum() {
        CustomException exception = assertThrows(CustomException.class,
                () -> teamCodeService.generateTeamCodes(generationRequest(101)));

        assertEquals(INVALID_TEAM_CODE_COUNT, exception.getCode());
        verifyNoInteractions(idGenService);
        verifyNoInteractions(teamCodeRepository);
    }

    @Test
    @DisplayName("should reject a count below one before calling idgen")
    void shouldRejectCountBelowOne() {
        CustomException exception = assertThrows(CustomException.class,
                () -> teamCodeService.generateTeamCodes(generationRequest(0)));

        assertEquals(INVALID_TEAM_CODE_COUNT, exception.getCode());
        verifyNoInteractions(idGenService);
        verifyNoInteractions(teamCodeRepository);
    }

    @Test
    @DisplayName("should not persist anything when idgen returns fewer codes than requested")
    void shouldNotPersistWhenIdgenReturnsFewerCodesThanRequested() throws InvalidTenantIdException {
        when(idGenService.getIdList(any(RequestInfo.class), eq(TENANT_ID), eq("team.code"), isNull(), eq(3)))
                .thenReturn(Arrays.asList("TEAM-1", "TEAM-2"));

        CustomException exception = assertThrows(CustomException.class,
                () -> teamCodeService.generateTeamCodes(generationRequest(3)));

        assertEquals(TEAM_CODE_GENERATION_FAILED, exception.getCode());
        verify(producer, never()).push(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should default a null count to one")
    void shouldDefaultNullCountToOne() throws InvalidTenantIdException {
        when(idGenService.getIdList(any(RequestInfo.class), eq(TENANT_ID), eq("team.code"), isNull(), eq(1)))
                .thenReturn(Collections.singletonList("TEAM-1"));

        List<String> codes = teamCodeService.generateTeamCodes(generationRequest(null));

        assertEquals(Collections.singletonList("TEAM-1"), codes);
        verify(idGenService, times(1)).getIdList(any(RequestInfo.class), anyString(), anyString(), isNull(), eq(1));
        verify(producer, times(1)).push(eq(TENANT_ID), eq(SAVE_TEAM_CODE_TOPIC), any());
    }

    @Test
    @DisplayName("should resolve the individual by individualId only")
    void shouldResolveByIndividualId() throws InvalidTenantIdException {
        Individual individual = individual(null);
        stubFind(individual);
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        TeamAssignmentResult result = teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-id"))
                .teamCode("TEAM-NEW")
                .build()));

        assertEquals(List.of("individual-id"), result.getAssignments().stream()
                .map(TeamAssignmentEntry::getIndividualId).toList());
        assertEquals("TEAM-NEW", result.getTeamCode());
        assertEquals(SelectorColumn.ID, captureSelectorColumn());
        assertEquals(List.of("individual-id"), captureSelectorValues());
    }

    @Test
    @DisplayName("should resolve the individual by username only")
    void shouldResolveByUsername() throws InvalidTenantIdException {
        Individual individual = individual(null);
        stubFind(individual);
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        TeamAssignmentResult result = teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .username(List.of("some-username"))
                .teamCode("TEAM-NEW")
                .build()));

        assertEquals(List.of("individual-id"), result.getAssignments().stream()
                .map(TeamAssignmentEntry::getIndividualId).toList());
        assertEquals(SelectorColumn.USERNAME, captureSelectorColumn());
        assertEquals(List.of("some-username"), captureSelectorValues());
    }

    @Test
    @DisplayName("should resolve the individual by userUuid only")
    void shouldResolveByUserUuid() throws InvalidTenantIdException {
        Individual individual = individual(null);
        stubFind(individual);
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        TeamAssignmentResult result = teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .userUuid(List.of("some-user-uuid"))
                .teamCode("TEAM-NEW")
                .build()));

        assertEquals(List.of("individual-id"), result.getAssignments().stream()
                .map(TeamAssignmentEntry::getIndividualId).toList());
        assertEquals(SelectorColumn.USER_UUID, captureSelectorColumn());
        assertEquals(List.of("some-user-uuid"), captureSelectorValues());
    }

    @Test
    @DisplayName("should throw when no selector is given")
    void shouldThrowWhenNoSelectorIsGiven() throws InvalidTenantIdException {
        CustomException exception = assertThrows(CustomException.class,
                () -> teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .individualId(List.of("   "))
                        .teamCode("TEAM-NEW")
                        .build())));

        assertEquals(INVALID_SELECTOR, exception.getCode());
        verify(individualRepository, never()).find(any(), anyInt(), anyInt(), anyString(), any(), any());
    }

    @Test
    @DisplayName("should throw when more than one selector is given")
    void shouldThrowWhenMoreThanOneSelectorIsGiven() throws InvalidTenantIdException {
        CustomException exception = assertThrows(CustomException.class,
                () -> teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .individualId(List.of("individual-id"))
                        .username(List.of("some-username"))
                        .teamCode("TEAM-NEW")
                        .build())));

        assertEquals(INVALID_SELECTOR, exception.getCode());
        verify(individualRepository, never()).find(any(), anyInt(), anyInt(), anyString(), any(), any());
    }

    @Test
    @DisplayName("should throw when the selector matches no individual")
    void shouldThrowWhenSelectorMatchesNoIndividual() throws InvalidTenantIdException {
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);
        stubResolvesTo(Collections.emptyMap());

        CustomException exception = assertThrows(CustomException.class,
                () -> teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .individualId(List.of("individual-id"))
                        .teamCode("TEAM-NEW")
                        .build())));

        assertEquals(INDIVIDUAL_NOT_FOUND, exception.getCode());
        verify(individualRepository, never()).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should throw when the selector matches more than one individual")
    void shouldThrowWhenSelectorIsAmbiguous() throws InvalidTenantIdException {
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);
        // one username, two system users carrying it - no uniqueness constraint on the column
        stubResolvesTo(Map.of("some-username", List.of("individual-a", "individual-b")));

        CustomException exception = assertThrows(CustomException.class,
                () -> teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .username(List.of("some-username"))
                        .teamCode("TEAM-NEW")
                        .build())));

        assertEquals(AMBIGUOUS_SELECTOR, exception.getCode());
        verify(individualRepository, never()).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should throw when the requested team code was never minted")
    void shouldThrowWhenTeamCodeIsUnknown() throws InvalidTenantIdException {
        stubFind(individual(null));
        when(teamCodeRepository.findByTeamCodes(TENANT_ID, Collections.singletonList("TEAM-NEW")))
                .thenReturn(Collections.emptyList());

        CustomException exception = assertThrows(CustomException.class,
                () -> teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .individualId(List.of("individual-id"))
                        .teamCode("TEAM-NEW")
                        .build())));

        assertEquals(TEAM_CODE_NOT_FOUND, exception.getCode());
        verify(individualRepository, never()).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should throw when the requested team code is retired")
    void shouldThrowWhenTeamCodeIsInactive() throws InvalidTenantIdException {
        stubFind(individual(null));
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.INACTIVE);

        CustomException exception = assertThrows(CustomException.class,
                () -> teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .individualId(List.of("individual-id"))
                        .teamCode("TEAM-NEW")
                        .build())));

        assertEquals(TEAM_CODE_INACTIVE, exception.getCode());
        verify(individualRepository, never()).save(anyList(), anyString());
    }

    @Test
    @DisplayName("should upsert the team code field instead of appending a second one")
    void shouldUpsertTheTeamCodeFieldInsteadOfAppending() throws InvalidTenantIdException {
        Individual individual = individual("TEAM-OLD");
        stubFind(individual);
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-id"))
                .teamCode("TEAM-NEW")
                .build()));

        Individual saved = captureSaved();
        assertEquals(1, teamCodeFieldCount(saved));
        assertEquals("TEAM-NEW", AdditionalFieldsUtil.getFieldValue(saved.getAdditionalFields(), TEAM_CODE_FIELD_KEY));
    }

    @Test
    @DisplayName("should keep the pre existing team mapping fields when assigning")
    void shouldKeepPreExistingTeamMappingFieldsWhenAssigning() throws InvalidTenantIdException {
        Individual individual = individual("TEAM-OLD");
        stubFind(individual);
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-id"))
                .teamCode("TEAM-NEW")
                .build()));

        Individual saved = captureSaved();
        assertEquals("some-project", AdditionalFieldsUtil.getFieldValue(saved.getAdditionalFields(),
                "team_mapping_project"));
        assertEquals("some-staff-id", AdditionalFieldsUtil.getFieldValue(saved.getAdditionalFields(),
                "team_mapping_staff_id"));
        assertEquals("Individual", saved.getAdditionalFields().getSchema());
    }

    @Test
    @DisplayName("should remove the team code field when the requested code is blank")
    void shouldRemoveTheTeamCodeFieldWhenRequestedCodeIsBlank() throws InvalidTenantIdException {
        Individual individual = individual("TEAM-OLD");
        stubFind(individual);

        TeamAssignmentResult result = teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-id"))
                .teamCode("   ")
                .build()));

        assertNull(result.getTeamCode());
        Individual saved = captureSaved();
        assertEquals(0, teamCodeFieldCount(saved));
        assertEquals("some-project", AdditionalFieldsUtil.getFieldValue(saved.getAdditionalFields(),
                "team_mapping_project"));
        verify(teamCodeRepository, never()).findByTeamCodes(anyString(), anyList());
    }

    @Test
    @DisplayName("should do nothing when the stored code already equals the requested one")
    void shouldDoNothingWhenTheStoredCodeAlreadyEqualsTheRequestedOne() throws InvalidTenantIdException {
        Individual individual = individual("TEAM-OLD");
        stubFind(individual);
        // the code is still validated up front even when every individual turns out to be a no-op
        stubTeamCodeLookup("TEAM-OLD", TeamCodeStatus.ASSIGNED);

        TeamAssignmentResult result = teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-id"))
                .teamCode("TEAM-OLD")
                .build()));

        assertEquals("TEAM-OLD", result.getTeamCode());
        assertEquals(0, result.getUpdatedCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(List.of("individual-id"), result.getAssignments().stream()
                .map(TeamAssignmentEntry::getIndividualId).toList());
        assertEquals(Integer.valueOf(3), individual.getRowVersion());
        verify(individualRepository, never()).save(anyList(), anyString());
        // the mapping trail is still maintained on the no-op path: `current` comes from the
        // asynchronously persisted individual row, so it can be stale or null even when the
        // membership is already right, and the mapping writes are idempotent
        assertEquals(1, captureMappingEvents(SAVE_TEAM_CODE_MAPPING_TOPIC).size());
        verify(producer, times(1)).push(eq(TENANT_ID), eq(SAVE_TEAM_CODE_MAPPING_TOPIC), any());
    }

    @Test
    @DisplayName("should do nothing when a removal is requested and no team code is present")
    void shouldDoNothingWhenRemovalIsRequestedAndNoTeamCodeIsPresent() throws InvalidTenantIdException {
        stubFind(individual(null));

        TeamAssignmentResult result = teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-id"))
                .build()));

        assertNull(result.getTeamCode());
        assertEquals(List.of("individual-id"), result.getAssignments().stream()
                .map(TeamAssignmentEntry::getIndividualId).toList());
        verify(individualRepository, never()).save(anyList(), anyString());
        // nothing to assign, so nothing goes to the save topic - but the removal is still
        // published, so the persister ends any live membership the stale individual row hid
        verify(producer, never()).push(anyString(), eq(SAVE_TEAM_CODE_MAPPING_TOPIC), any());
        verify(producer, times(1)).push(eq(TENANT_ID), eq(UPDATE_TEAM_CODE_MAPPING_TOPIC), any());
    }

    @Test
    @DisplayName("should bump the row version and stamp the requester on the audit details")
    void shouldBumpRowVersionAndStampRequester() throws InvalidTenantIdException {
        Individual individual = individual(null);
        stubFind(individual);
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-id"))
                .teamCode("TEAM-NEW")
                .build()));

        Individual saved = captureSaved();
        assertEquals(Integer.valueOf(4), saved.getRowVersion());
        assertEquals(REQUESTER_UUID, saved.getAuditDetails().getLastModifiedBy());
    }

    @Test
    @DisplayName("should publish the assignment to the configured update individual topic")
    void shouldPublishAssignmentToTheConfiguredTopic() throws InvalidTenantIdException {
        stubFind(individual(null));
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-id"))
                .teamCode("TEAM-NEW")
                .build()));

        verify(individualRepository, times(1)).save(anyList(), eq(UPDATE_TOPIC));
    }

    @Test
    @DisplayName("should move the assigned count from the old code to the new one when replacing")
    void shouldMoveAssignedCountWhenReplacing() throws InvalidTenantIdException {
                stubFind(individual("TEAM-OLD"));
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-id"))
                .teamCode("TEAM-NEW")
                .build()));

                TeamCodeMapping mappingCapturedFromTopic = captureMappingEvents(SAVE_TEAM_CODE_MAPPING_TOPIC).get(0);
        TeamCodeMapping mapping = mappingCapturedFromTopic;
        assertEquals("TEAM-NEW", mapping.getTeamCode());
        assertEquals("individual-id", mapping.getIndividualId());
        assertEquals(TeamCodeStatus.ASSIGNED, mapping.getStatus());
        assertEquals(REQUESTER_UUID, mapping.getAssignedBy());

        // every other live membership is swept, keeping only the requested code - the old code is
        // not named, because the individual row that would name it is written asynchronously
        verify(producer, times(1)).push(eq(TENANT_ID), eq(SAVE_TEAM_CODE_MAPPING_TOPIC), any());
        
        // and the codes the sweep actually closed get recounted too
        
    }

    @Test
    @DisplayName("should close out every mapping on a removal")
    void shouldDecrementOnlyTheOldCodeOnRemoval() throws InvalidTenantIdException {
        stubFind(individual("TEAM-OLD"));
                teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-id"))
                .build()));

        // a removal keeps nothing, so the sweep is called with a null keepTeamCode
        // a removal goes to the update topic, which only ends live memberships
        verify(producer, times(1)).push(eq(TENANT_ID), eq(UPDATE_TEAM_CODE_MAPPING_TOPIC), any());
        verify(producer, never()).push(anyString(), eq(SAVE_TEAM_CODE_MAPPING_TOPIC), any());
        
    }

    @Test
    @DisplayName("should record the individual's userUuid on the mapping so the trail is resolvable to a user")
    void shouldRecordUserUuidOnTheMapping() throws InvalidTenantIdException {
        Individual individual = individual(null);
        individual.setUserUuid("user-uuid-1");
        stubFind(individual);
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-id"))
                .teamCode("TEAM-NEW")
                .build()));

                TeamCodeMapping mappingCapturedFromTopic = captureMappingEvents(SAVE_TEAM_CODE_MAPPING_TOPIC).get(0);
        TeamCodeMapping mapping = mappingCapturedFromTopic;
        assertEquals("user-uuid-1", mapping.getUserUuid());
        assertEquals(TENANT_ID, mapping.getTenantId());
        assertNull(mapping.getUnassignedBy());
        assertNull(mapping.getUnassignedTime());
    }

    @Test
    @DisplayName("should pass the unassigned filter and paging straight through to the repository")
    void shouldSearchUnassignedTeamCodes() throws InvalidTenantIdException {
        TeamCodeSearch criteria = TeamCodeSearch.builder()
                .status(Collections.singletonList(TeamCodeStatus.UNASSIGNED))
                .build();
        when(teamCodeRepository.search(eq(criteria), eq(50), eq(0), eq(TENANT_ID), eq(Boolean.FALSE)))
                .thenReturn(SearchResponse.<TeamCode>builder()
                        .totalCount(2L)
                        .response(Arrays.asList(teamCode("TEAM-1"), teamCode("TEAM-2")))
                        .build());

        SearchResponse<TeamCode> result = teamCodeService.searchTeamCodes(criteria, 50, 0, TENANT_ID,
                Boolean.FALSE, Boolean.FALSE);

        assertEquals(2L, result.getTotalCount());
        assertEquals(2, result.getResponse().size());
        // members are only loaded on request, so the default search stays a single query
        assertNull(result.getResponse().get(0).getMembers());
        verify(teamCodeRepository, never()).findMappingsByTeamCodes(anyString(), anyList(), anyBoolean());
    }

    @Test
    @DisplayName("should attach each team code's members in one query when includeMembers is set")
    void shouldAttachMembersWhenRequested() throws InvalidTenantIdException {
        when(teamCodeRepository.search(any(), anyInt(), anyInt(), eq(TENANT_ID), any()))
                .thenReturn(SearchResponse.<TeamCode>builder()
                        .totalCount(2L)
                        .response(Arrays.asList(teamCode("TEAM-1"), teamCode("TEAM-2")))
                        .build());
        when(teamCodeRepository.findMappingsByTeamCodes(eq(TENANT_ID), eq(Arrays.asList("TEAM-1", "TEAM-2")),
                eq(false)))
                .thenReturn(Arrays.asList(
                        mapping("TEAM-1", "individual-a", TeamCodeStatus.ASSIGNED),
                        mapping("TEAM-1", "individual-b", TeamCodeStatus.UNASSIGNED)));

        SearchResponse<TeamCode> result = teamCodeService.searchTeamCodes(TeamCodeSearch.builder().build(), 50, 0,
                TENANT_ID, Boolean.FALSE, Boolean.TRUE);

        // one query for the whole page, not one per team code
        verify(teamCodeRepository, times(1)).findMappingsByTeamCodes(anyString(), anyList(), anyBoolean());
        assertEquals(2, result.getResponse().get(0).getMembers().size());
        // a code nobody was ever mapped to reports an empty member list rather than null
        assertEquals(Collections.emptyList(), result.getResponse().get(1).getMembers());
    }

    @Test
    @DisplayName("should reject a search with no tenant before touching the repository")
    void shouldRejectSearchWithoutTenant() {
        CustomException exception = assertThrows(CustomException.class, () -> teamCodeService.searchTeamCodes(
                TeamCodeSearch.builder().build(), 50, 0, "  ", Boolean.FALSE, Boolean.FALSE));

        assertEquals(INVALID_TENANT_ID, exception.getCode());
        verifyNoInteractions(teamCodeRepository);
    }

    private void stubTeamCodeLookup(String teamCode, TeamCodeStatus status) throws InvalidTenantIdException {
        lenient().when(teamCodeRepository.findByTeamCodes(TENANT_ID, Collections.singletonList(teamCode)))
                .thenReturn(Collections.singletonList(TeamCode.builder()
                        .id("team-code-id")
                        .tenantId(TENANT_ID)
                        .teamCode(teamCode)
                        .status(status)
                        .assignedCount(0)
                        .build()));
    }

    /**
     * The membership events the service published, read back off the topic.
     */
    @SuppressWarnings("unchecked")
    private List<TeamCodeMapping> captureMappingEvents(String topic) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(producer).push(eq(TENANT_ID), eq(topic), captor.capture());
        return (List<TeamCodeMapping>) captor.getValue();
    }

    private TeamCode teamCode(String code) {
        return TeamCode.builder()
                .id(code + "-id")
                .tenantId(TENANT_ID)
                .teamCode(code)
                .status(TeamCodeStatus.UNASSIGNED)
                .assignedCount(0)
                .build();
    }

    private TeamCodeMapping mapping(String code, String individualId, TeamCodeStatus status) {
        return TeamCodeMapping.builder()
                .id(code + "-" + individualId)
                .tenantId(TENANT_ID)
                .teamCode(code)
                .individualId(individualId)
                .status(status)
                .assignedBy(REQUESTER_UUID)
                .assignedTime(1L)
                .build();
    }

    @Test
    @DisplayName("should assign one code to a whole team in a single publish")
    void shouldAssignOneCodeToManyIndividuals() throws InvalidTenantIdException {
        Individual first = individual(null);
        first.setId("individual-a");
        Individual second = individual(null);
        second.setId("individual-b");
        stubFind(first, second);
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        TeamAssignmentResult result = teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .username(List.of("user-a", "user-b"))
                .teamCode("TEAM-NEW")
                .build()));

        assertEquals(List.of("individual-a", "individual-b"), result.getAssignments().stream()
                .map(TeamAssignmentEntry::getIndividualId).toList());
        assertEquals(2, result.getUpdatedCount());
        assertEquals(0, result.getSkippedCount());

        // one publish for the batch, not one per individual
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Individual>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(individualRepository, times(1)).save(savedCaptor.capture(), eq(UPDATE_TOPIC));
        assertEquals(2, savedCaptor.getValue().size());

        // a mapping per member, but the code is recounted once
        assertEquals(2, captureMappingEvents(SAVE_TEAM_CODE_MAPPING_TOPIC).size());
        
    }

    @Test
    @DisplayName("should count the individuals that already carry the code as skipped")
    void shouldSplitUpdatedAndSkippedAcrossTheBatch() throws InvalidTenantIdException {
        Individual alreadyTagged = individual("TEAM-NEW");
        alreadyTagged.setId("individual-a");
        Individual untagged = individual(null);
        untagged.setId("individual-b");
        stubFind(alreadyTagged, untagged);
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        TeamAssignmentResult result = teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-a", "individual-b"))
                .teamCode("TEAM-NEW")
                .build()));

        assertEquals(1, result.getUpdatedCount());
        assertEquals(1, result.getSkippedCount());
        // only the individual that actually changed is republished
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Individual>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(individualRepository, times(1)).save(savedCaptor.capture(), eq(UPDATE_TOPIC));
        assertEquals(1, savedCaptor.getValue().size());
        assertEquals("individual-b", savedCaptor.getValue().get(0).getId());
        // both get their mapping maintained - only one gets republished
        assertEquals(2, captureMappingEvents(SAVE_TEAM_CODE_MAPPING_TOPIC).size());
    }

    @Test
    @DisplayName("should recount every code the batch touched, each exactly once")
    void shouldRecountEveryAffectedCodeOnce() throws InvalidTenantIdException {
                Individual fromOld = individual("TEAM-OLD");
        fromOld.setId("individual-a");
        Individual alsoFromOld = individual("TEAM-OLD");
        alsoFromOld.setId("individual-b");
        stubFind(fromOld, alsoFromOld);
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-a", "individual-b"))
                .teamCode("TEAM-NEW")
                .build()));

        // one sweep per individual, but TEAM-OLD is recounted once for the whole batch
        verify(producer, times(1)).push(eq(TENANT_ID), eq(SAVE_TEAM_CODE_MAPPING_TOPIC), any());
        
        
    }

    @Test
    @DisplayName("should de-duplicate and trim selector values before resolving")
    void shouldDeduplicateSelectorValues() throws InvalidTenantIdException {
        stubFind(individual(null));
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(Arrays.asList("individual-id", "  individual-id  ", "   ", null))
                .teamCode("TEAM-NEW")
                .build()));

        assertEquals(List.of("individual-id"), captureSelectorValues());
    }

    @Test
    @DisplayName("should reject a batch larger than the configured cap before touching the database")
    void shouldRejectOversizedBatch() throws InvalidTenantIdException {
        when(properties.getTeamAssignBatchSize()).thenReturn(2);

        CustomException exception = assertThrows(CustomException.class,
                () -> teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .individualId(List.of("a", "b", "c"))
                        .teamCode("TEAM-NEW")
                        .build())));

        assertEquals(TEAM_ASSIGNMENT_BATCH_TOO_LARGE, exception.getCode());
        verifyNoInteractions(individualRepository);
        verifyNoInteractions(teamCodeRepository);
    }

    @Test
    @DisplayName("should write nothing when only some of the selector values resolve")
    void shouldWriteNothingWhenPartOfTheBatchDoesNotResolve() throws InvalidTenantIdException {
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);
        stubResolvesTo(Map.of("user-a", List.of("individual-a")));

        CustomException exception = assertThrows(CustomException.class,
                () -> teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .username(List.of("user-a", "user-missing"))
                        .teamCode("TEAM-NEW")
                        .build())));

        assertEquals(INDIVIDUAL_NOT_FOUND, exception.getCode());
        verify(individualRepository, never()).save(anyList(), anyString());
        verify(producer, never()).push(anyString(), eq(SAVE_TEAM_CODE_MAPPING_TOPIC), any());
    }

    @Test
    @DisplayName("should reject a request that populates more than one selector list")
    void shouldRejectMultiplePopulatedSelectorLists() {
        CustomException exception = assertThrows(CustomException.class,
                () -> teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                        .tenantId(TENANT_ID)
                        .individualId(List.of("individual-id"))
                        .username(List.of("some-username"))
                        .teamCode("TEAM-NEW")
                        .build())));

        assertEquals(INVALID_SELECTOR, exception.getCode());
        verifyNoInteractions(individualRepository);
    }

    @Test
    @DisplayName("should echo every identifier so the caller can match on the one it sent")
    void shouldEchoAllIdentifiersInTheResult() throws InvalidTenantIdException {
        Individual individual = individual(null);
        individual.setUserUuid("uu-1");
        individual.setUserDetails(org.egov.common.models.individual.UserDetails.builder()
                .username("User-1").build());
        stubFind(individual);
        stubTeamCodeLookup("TEAM-NEW", TeamCodeStatus.UNASSIGNED);

        TeamAssignmentResult result = teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .userUuid(List.of("uu-1"))
                .teamCode("TEAM-NEW")
                .build()));

        TeamAssignmentEntry entry = result.getAssignments().get(0);
        // the caller selected by userUuid, so that value has to come back to match on
        assertEquals("uu-1", entry.getUserUuid());
        assertEquals("User-1", entry.getUsername());
        assertEquals("individual-id", entry.getIndividualId());
        assertEquals("UPDATED", entry.getStatus());
    }

    @Test
    @DisplayName("should mark an individual that already carried the code as SKIPPED")
    void shouldMarkUnchangedIndividualAsSkipped() throws InvalidTenantIdException {
        stubFind(individual("TEAM-OLD"));
        stubTeamCodeLookup("TEAM-OLD", TeamCodeStatus.ASSIGNED);

        TeamAssignmentResult result = teamCodeService.updateTeamCode(updateRequest(TeamAssignment.builder()
                .tenantId(TENANT_ID)
                .individualId(List.of("individual-id"))
                .teamCode("TEAM-OLD")
                .build()));

        assertEquals("SKIPPED", result.getAssignments().get(0).getStatus());
        assertEquals(1, result.getSkippedCount());
    }

    private TeamCodeGenerationRequest generationRequest(Integer count) {
        return TeamCodeGenerationRequest.builder()
                .requestInfo(requestInfo)
                .tenantId(TENANT_ID)
                .count(count)
                .build();
    }

    private TeamAssignmentRequest updateRequest(TeamAssignment assignment) {
        return TeamAssignmentRequest.builder()
                .requestInfo(requestInfo)
                .teamAssignment(assignment)
                .build();
    }

    /**
     * An individual as it comes out of the repository, carrying the team_mapping_* keys that every
     * assignment and removal has to leave alone.
     */
    private Individual individual(String teamCode) {
        Individual individual = IndividualTestBuilder.builder()
                .withId("individual-id")
                .withName()
                .withTenantId(TENANT_ID)
                .withAddress()
                .withRowVersion(3)
                .withAuditDetails()
                .build();
        List<Field> fields = new ArrayList<>();
        fields.add(Field.builder().key("team_mapping_project").value("some-project").build());
        fields.add(Field.builder().key("team_mapping_staff_id").value("some-staff-id").build());
        if (teamCode != null) {
            fields.add(Field.builder().key(TEAM_CODE_FIELD_KEY).value(teamCode).build());
        }
        individual.setAdditionalFields(AdditionalFields.builder()
                .schema("Individual")
                .version(1)
                .fields(fields)
                .build());
        return individual;
    }

    /**
     * Resolution is two steps now: the selector values are mapped to ids by a system-user-only
     * query, then the full individuals are loaded by id. This stubs both, mapping the nth selector
     * value to the nth individual.
     */
    private void stubFind(Individual... individuals) throws InvalidTenantIdException {
        List<Individual> response = Arrays.asList(individuals);
        lenient().when(individualRepository.findSystemUserIdsBySelector(eq(TENANT_ID), any(SelectorColumn.class),
                        anyList()))
                .thenAnswer(invocation -> {
                    List<String> values = invocation.getArgument(2);
                    Map<String, List<String>> idsByValue = new LinkedHashMap<>();
                    for (int i = 0; i < values.size() && i < response.size(); i++) {
                        idsByValue.put(values.get(i), List.of(response.get(i).getId()));
                    }
                    return idsByValue;
                });
        lenient().when(individualRepository.findById(eq(TENANT_ID), anyList(), eq("id"), eq(Boolean.FALSE)))
                .thenReturn(SearchResponse.<Individual>builder()
                        .totalCount((long) response.size())
                        .response(response)
                        .build());
    }

    private void stubResolvesTo(Map<String, List<String>> idsByValue) throws InvalidTenantIdException {
        when(individualRepository.findSystemUserIdsBySelector(eq(TENANT_ID), any(SelectorColumn.class), anyList()))
                .thenReturn(idsByValue);
    }

    /**
     * The column the service resolved against, so each selector test can prove it did not fall
     * through to a different one.
     */
    private SelectorColumn captureSelectorColumn() throws InvalidTenantIdException {
        ArgumentCaptor<SelectorColumn> columnCaptor = ArgumentCaptor.forClass(SelectorColumn.class);
        verify(individualRepository, times(1)).findSystemUserIdsBySelector(eq(TENANT_ID), columnCaptor.capture(),
                anyList());
        return columnCaptor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<String> captureSelectorValues() throws InvalidTenantIdException {
        ArgumentCaptor<List<String>> valuesCaptor = ArgumentCaptor.forClass(List.class);
        verify(individualRepository, times(1)).findSystemUserIdsBySelector(eq(TENANT_ID), any(SelectorColumn.class),
                valuesCaptor.capture());
        return valuesCaptor.getValue();
    }

    private Individual captureSaved() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Individual>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(individualRepository, times(1)).save(savedCaptor.capture(), eq(UPDATE_TOPIC));
        assertEquals(1, savedCaptor.getValue().size());
        return savedCaptor.getValue().get(0);
    }

    private long teamCodeFieldCount(Individual individual) {
        if (individual.getAdditionalFields() == null || individual.getAdditionalFields().getFields() == null) {
            return 0;
        }
        return individual.getAdditionalFields().getFields().stream()
                .filter(field -> TEAM_CODE_FIELD_KEY.equals(field.getKey()))
                .count();
    }
}
