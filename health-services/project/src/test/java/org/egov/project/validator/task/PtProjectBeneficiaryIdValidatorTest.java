package org.egov.project.validator.task;

import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkRequest;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the unbundle contract of PtProjectBeneficiaryIdValidator: a delivery legitimately carries only ONE of
 * the two parent keys, so the validator must reject a task only when it carries NEITHER.
 *
 * <p>Tests come in two halves. The first runs with project.relationship.validation=false, the value shipped
 * in application.properties, which isolates the INVALID_RELATED_ENTITY_ID check that always runs. The second
 * half re-stubs the flag TRUE, because the existence lookup gated behind it reached the same mixed-key
 * rejection by a second route: it used to sample one accessor for the whole batch, so a sibling carrying only
 * the other key was looked up in the wrong column with a null value and came back NON_EXISTENT_RELATED_ENTITY.
 */
@ExtendWith(MockitoExtension.class)
public class PtProjectBeneficiaryIdValidatorTest {

    @InjectMocks
    private PtProjectBeneficiaryIdValidator ptProjectBeneficiaryIdValidator;

    @Mock
    private ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Mock
    private ProjectConfiguration projectConfiguration;

    @BeforeEach
    void setUp() {
        lenient().when(projectConfiguration.getIsRelationshipValidationEnabled()).thenReturn(Boolean.FALSE);
    }

    @Test
    @DisplayName("should accept a task that carries only projectBeneficiaryClientReferenceId")
    void shouldAcceptTaskWithOnlyBeneficiaryClientReferenceId() {
        // The unbundle case the fix exists for: the beneficiary's server id is not known to the device yet.
        Task task = taskWith(null, "beneficiary-client-ref-1");

        Map<Task, List<Error>> errorDetailsMap = ptProjectBeneficiaryIdValidator
                .validate(requestWith(Collections.singletonList(task)));

        assertTrue(errorDetailsMap.isEmpty());
        assertFalse(task.getHasErrors());
        verifyNoInteractions(projectBeneficiaryRepository);
    }

    @Test
    @DisplayName("should accept a task that carries only projectBeneficiaryId")
    void shouldAcceptTaskWithOnlyBeneficiaryId() {
        Task task = taskWith("beneficiary-id-1", null);

        Map<Task, List<Error>> errorDetailsMap = ptProjectBeneficiaryIdValidator
                .validate(requestWith(Collections.singletonList(task)));

        assertTrue(errorDetailsMap.isEmpty());
        assertFalse(task.getHasErrors());
        verifyNoInteractions(projectBeneficiaryRepository);
    }

    @Test
    @DisplayName("should reject a task whose both beneficiary keys are null with INVALID_RELATED_ENTITY_ID")
    void shouldRejectTaskWhenBothBeneficiaryKeysAreNull() {
        Task task = taskWith(null, null);

        Map<Task, List<Error>> errorDetailsMap = ptProjectBeneficiaryIdValidator
                .validate(requestWith(Collections.singletonList(task)));

        assertEquals(1, errorDetailsMap.size());
        List<Error> errors = errorDetailsMap.values().iterator().next();
        assertEquals(1, errors.size());
        assertEquals("INVALID_RELATED_ENTITY_ID", errors.get(0).getErrorCode());
        assertEquals(Error.ErrorType.NON_RECOVERABLE, errors.get(0).getType());
        assertTrue(task.getHasErrors());
    }

    @Test
    @DisplayName("should reject a task whose both beneficiary keys are blank, not merely null")
    void shouldRejectTaskWhenBothBeneficiaryKeysAreBlank() {
        // A device that serialises an unset key as "" must be treated exactly like one that omits it,
        // otherwise a blank string reaches the repository as a lookup key and matches nothing.
        Task task = taskWith("", "   ");

        Map<Task, List<Error>> errorDetailsMap = ptProjectBeneficiaryIdValidator
                .validate(requestWith(Collections.singletonList(task)));

        assertEquals(1, errorDetailsMap.size());
        List<Error> errors = errorDetailsMap.values().iterator().next();
        assertEquals("INVALID_RELATED_ENTITY_ID", errors.get(0).getErrorCode());
        assertEquals(Error.ErrorType.NON_RECOVERABLE, errors.get(0).getType());
        assertTrue(task.getHasErrors());
    }

    @Test
    @DisplayName("should accept a batch that mixes both beneficiary key shapes")
    void shouldAcceptBatchMixingBothBeneficiaryKeyShapes() {
        // This is the shape that actually regressed. getIdMethod samples the FIRST entity, so a leading task
        // carrying a server id makes getProjectBeneficiaryId the sampled accessor; the two following
        // clientReferenceId-only tasks then both key to null, collapsing the id-to-object map from 3 entries
        // to 2. The old size comparison saw the mismatch and flagged every task whose sampled accessor was
        // null, so both legitimate offline deliveries were rejected NON_RECOVERABLE and never retried.
        Task withServerId = taskWith("beneficiary-id-1", null);
        Task firstOfflineTask = taskWith(null, "beneficiary-client-ref-1");
        Task secondOfflineTask = taskWith(null, "beneficiary-client-ref-2");
        List<Task> tasks = Arrays.asList(withServerId, firstOfflineTask, secondOfflineTask);

        Map<Task, List<Error>> errorDetailsMap = ptProjectBeneficiaryIdValidator.validate(requestWith(tasks));

        assertTrue(errorDetailsMap.isEmpty());
        tasks.forEach(task -> assertFalse(task.getHasErrors()));
        verifyNoInteractions(projectBeneficiaryRepository);
    }

    @Test
    @DisplayName("should reject only the keyless task in a batch and leave its siblings valid")
    void shouldRejectOnlyTheKeylessTaskInABatch() {
        // One malformed record must not take its siblings down with it.
        Task keyless = taskWith(null, null);
        Task valid = taskWith(null, "beneficiary-client-ref-1");

        Map<Task, List<Error>> errorDetailsMap = ptProjectBeneficiaryIdValidator
                .validate(requestWith(Arrays.asList(valid, keyless)));

        assertEquals(1, errorDetailsMap.size());
        assertTrue(keyless.getHasErrors());
        assertFalse(valid.getHasErrors());
    }

    @Test
    @DisplayName("should accept a mixed-key batch when relationship validation is enabled, looking each shape "
            + "up in its own column")
    void shouldAcceptMixedKeyBatchWhenRelationshipValidationEnabled() throws Exception {
        // The second route to the D5 defect. A leading task carrying a server id used to make
        // getProjectBeneficiaryId the accessor SAMPLED for the whole batch, so both clientReferenceId-only
        // siblings contributed a null key, were looked up in the id column, came back absent and were flagged
        // NON_EXISTENT_RELATED_ENTITY / NON_RECOVERABLE - so the device never retried them.
        lenient().when(projectConfiguration.getIsRelationshipValidationEnabled()).thenReturn(Boolean.TRUE);
        Task withServerId = taskWith("beneficiary-id-1", null);
        Task firstOfflineTask = taskWith(null, "beneficiary-client-ref-1");
        Task secondOfflineTask = taskWith(null, "beneficiary-client-ref-2");
        List<Task> tasks = Arrays.asList(withServerId, firstOfflineTask, secondOfflineTask);
        when(projectBeneficiaryRepository.validateIds("default",
                Collections.singletonList("beneficiary-id-1"), "id"))
                .thenReturn(Collections.singletonList("beneficiary-id-1"));
        when(projectBeneficiaryRepository.validateIds("default",
                Arrays.asList("beneficiary-client-ref-1", "beneficiary-client-ref-2"), "clientReferenceId"))
                .thenReturn(Arrays.asList("beneficiary-client-ref-1", "beneficiary-client-ref-2"));

        Map<Task, List<Error>> errorDetailsMap = ptProjectBeneficiaryIdValidator.validate(requestWith(tasks));

        assertTrue(errorDetailsMap.isEmpty());
        tasks.forEach(task -> assertFalse(task.getHasErrors()));
        // Two lookups, one per column - not one sampled lookup covering both shapes.
        verify(projectBeneficiaryRepository, times(1))
                .validateIds(anyString(), anyList(), eq("id"));
        verify(projectBeneficiaryRepository, times(1))
                .validateIds(anyString(), anyList(), eq("clientReferenceId"));
    }

    @Test
    @DisplayName("should flag only the genuinely absent key in a mixed-key batch")
    void shouldFlagOnlyTheGenuinelyAbsentKeyInAMixedKeyBatch() throws Exception {
        lenient().when(projectConfiguration.getIsRelationshipValidationEnabled()).thenReturn(Boolean.TRUE);
        Task withServerId = taskWith("beneficiary-id-1", null);
        Task known = taskWith(null, "beneficiary-client-ref-1");
        Task unknown = taskWith(null, "beneficiary-client-ref-missing");
        when(projectBeneficiaryRepository.validateIds("default",
                Collections.singletonList("beneficiary-id-1"), "id"))
                .thenReturn(Collections.singletonList("beneficiary-id-1"));
        when(projectBeneficiaryRepository.validateIds("default",
                Arrays.asList("beneficiary-client-ref-1", "beneficiary-client-ref-missing"), "clientReferenceId"))
                .thenReturn(Collections.singletonList("beneficiary-client-ref-1"));

        Map<Task, List<Error>> errorDetailsMap = ptProjectBeneficiaryIdValidator
                .validate(requestWith(Arrays.asList(withServerId, known, unknown)));

        assertEquals(1, errorDetailsMap.size());
        assertTrue(unknown.getHasErrors());
        assertFalse(known.getHasErrors());
        assertFalse(withServerId.getHasErrors());
        List<Error> errors = errorDetailsMap.get(unknown);
        assertEquals("NON_EXISTENT_RELATED_ENTITY", errors.get(0).getErrorCode());
    }

    @Test
    @DisplayName("should flag every task sharing an absent key, not just one of them")
    void shouldFlagEveryTaskSharingAnAbsentKey() throws Exception {
        // Two deliveries against the same beneficiary is ordinary. Iterating an id-to-object map kept one
        // task per key, so the siblings slipped through unflagged. The two tasks are given distinct
        // actualStartDates: Task carries Lombok @Data with the default callSuper=false, so only
        // Task-declared fields take part in equals, and two otherwise identical tasks would collapse into a
        // single errorDetailsMap key no matter what the validator did.
        lenient().when(projectConfiguration.getIsRelationshipValidationEnabled()).thenReturn(Boolean.TRUE);
        Task first = taskWith(null, "beneficiary-client-ref-missing", 1L);
        Task second = taskWith(null, "beneficiary-client-ref-missing", 2L);
        when(projectBeneficiaryRepository.validateIds("default",
                Collections.singletonList("beneficiary-client-ref-missing"), "clientReferenceId"))
                .thenReturn(Collections.emptyList());

        Map<Task, List<Error>> errorDetailsMap = ptProjectBeneficiaryIdValidator
                .validate(requestWith(Arrays.asList(first, second)));

        assertEquals(2, errorDetailsMap.size());
        assertTrue(first.getHasErrors());
        assertTrue(second.getHasErrors());
    }

    @Test
    @DisplayName("should query only the id column for a batch that carries only server ids")
    void shouldQueryOnlyTheIdColumnForASingleShapeBatch() throws Exception {
        // Partitioning must not cost a second query when there is nothing of the other shape to look up.
        lenient().when(projectConfiguration.getIsRelationshipValidationEnabled()).thenReturn(Boolean.TRUE);
        Task first = taskWith("beneficiary-id-1", null);
        Task second = taskWith("beneficiary-id-2", null);
        when(projectBeneficiaryRepository.validateIds("default",
                Arrays.asList("beneficiary-id-1", "beneficiary-id-2"), "id"))
                .thenReturn(Arrays.asList("beneficiary-id-1", "beneficiary-id-2"));

        Map<Task, List<Error>> errorDetailsMap = ptProjectBeneficiaryIdValidator
                .validate(requestWith(Arrays.asList(first, second)));

        assertTrue(errorDetailsMap.isEmpty());
        verify(projectBeneficiaryRepository, times(1))
                .validateIds(anyString(), anyList(), eq("id"));
        verify(projectBeneficiaryRepository, never())
                .validateIds(anyString(), anyList(), eq("clientReferenceId"));
    }

    @Test
    @DisplayName("should not query, and not throw, when every task in the batch was already flagged")
    void shouldNotQueryWhenEveryTaskWasAlreadyFlagged() {
        // getTenantId does findAny().get(): with no task left to validate an unguarded call throws
        // NoSuchElementException out of validate(), and the bulk consumer's catch-all discards the batch.
        lenient().when(projectConfiguration.getIsRelationshipValidationEnabled()).thenReturn(Boolean.TRUE);
        Task keyless = taskWith(null, null);

        Map<Task, List<Error>> errorDetailsMap = ptProjectBeneficiaryIdValidator
                .validate(requestWith(Collections.singletonList(keyless)));

        assertEquals(1, errorDetailsMap.size());
        assertEquals("INVALID_RELATED_ENTITY_ID", errorDetailsMap.get(keyless).get(0).getErrorCode());
        verifyNoInteractions(projectBeneficiaryRepository);
    }

    @Test
    @DisplayName("should flag every task with TENANT_ID_INVALID when the tenant schema cannot be resolved")
    void shouldFlagEveryTaskWhenTenantIdIsInvalid() throws Exception {
        lenient().when(projectConfiguration.getIsRelationshipValidationEnabled()).thenReturn(Boolean.TRUE);
        Task withServerId = taskWith("beneficiary-id-1", null);
        Task offlineTask = taskWith(null, "beneficiary-client-ref-1");
        when(projectBeneficiaryRepository.validateIds(anyString(), anyList(), anyString()))
                .thenThrow(new InvalidTenantIdException("invalid tenant"));

        Map<Task, List<Error>> errorDetailsMap = ptProjectBeneficiaryIdValidator
                .validate(requestWith(Arrays.asList(withServerId, offlineTask)));

        assertEquals(2, errorDetailsMap.size());
        errorDetailsMap.values().forEach(errors ->
                assertEquals("TENANT_ID_INVALID", errors.get(0).getErrorCode()));
    }

    private TaskBulkRequest requestWith(List<Task> tasks) {
        return TaskBulkRequest.builder().tasks(tasks).build();
    }

    private Task taskWith(String projectBeneficiaryId, String projectBeneficiaryClientReferenceId) {
        return taskWith(projectBeneficiaryId, projectBeneficiaryClientReferenceId, null);
    }

    private Task taskWith(String projectBeneficiaryId, String projectBeneficiaryClientReferenceId,
                          Long actualStartDate) {
        Task.TaskBuilder<Task, ?> builder = (Task.TaskBuilder<Task, ?>) Task.builder();
        return builder.tenantId("default")
                .projectId("project-id-1")
                .actualStartDate(actualStartDate)
                .projectBeneficiaryId(projectBeneficiaryId)
                .projectBeneficiaryClientReferenceId(projectBeneficiaryClientReferenceId)
                .hasErrors(Boolean.FALSE)
                .build();
    }
}
