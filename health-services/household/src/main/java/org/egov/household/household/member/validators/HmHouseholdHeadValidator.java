package org.egov.household.household.member.validators;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.Error;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkRequest;
import org.egov.common.utils.CommonUtils;
import org.egov.common.validator.Validator;
import org.egov.household.config.HouseholdMemberConfiguration;
import org.egov.household.repository.HouseholdMemberRepository;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.household.Constants.CLIENT_REFERENCE_ID_FIELD;
import static org.egov.common.utils.ValidatorUtils.getErrorForInvalidTenantId;
import static org.egov.household.Constants.*;
import static org.egov.household.Constants.HOUSEHOLD_ALREADY_HAS_HEAD;
import static org.egov.household.Constants.HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE;
import static org.egov.household.Constants.HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD;
import static org.egov.household.Constants.HOUSEHOLD_ID_FIELD;
import static org.egov.household.Constants.ID_FIELD;

@Component
@Order(9)
@Slf4j
public class HmHouseholdHeadValidator implements Validator<HouseholdMemberBulkRequest, HouseholdMember> {

    private final HouseholdMemberRepository householdMemberRepository;

    private final HouseholdMemberConfiguration householdMemberConfiguration;

    public HmHouseholdHeadValidator(HouseholdMemberRepository householdMemberRepository,
                                    HouseholdMemberConfiguration householdMemberConfiguration) {
        this.householdMemberRepository = householdMemberRepository;
        this.householdMemberConfiguration = householdMemberConfiguration;
    }

    @Override
    public Map<HouseholdMember, List<Error>> validate(HouseholdMemberBulkRequest householdMemberBulkRequest) {
        HashMap<HouseholdMember, List<Error>> errorDetailsMap = new HashMap<>();
        log.debug("validating head of household member");
        List<HouseholdMember> householdMembers = householdMemberBulkRequest.getHouseholdMembers().stream()
                .filter(notHavingErrors()).collect(Collectors.toList());
        if(!householdMembers.isEmpty()){
            String tenantId = CommonUtils.getTenantId(householdMembers);
            Method householdMemberidMethod = getIdMethod(householdMembers, ID_FIELD, CLIENT_REFERENCE_ID_FIELD);
            // TRAP: the parent key must be chosen PER MEMBER. getIdMethod samples one arbitrary element,
            // so on a batch mixing both parent-key shapes the sampled accessor returns null for the other
            // shape and groupingBy throws NPE out of validate() - the bulk consumer then discards the
            // whole batch behind an HTTP 202.
            // Members carrying BOTH keys are read first as an alias relation, so a household whose head
            // already has a server id is not split from siblings that still carry only the client
            // reference.
            Map<String, String> householdIdByHouseholdClientReferenceId =
                    buildHouseholdIdAliases(householdMembers);
            Map<String, List<HouseholdMember>> membersByHouseholdId = new LinkedHashMap<>();
            Map<String, List<HouseholdMember>> membersByHouseholdClientReferenceId = new LinkedHashMap<>();
            // Per householdId group, the client-reference keys folded into it through an alias; empty
            // unless the batch mixes both shapes.
            Map<String, Set<String>> mergedClientReferenceKeysByHouseholdId = new LinkedHashMap<>();
            for (HouseholdMember householdMember : householdMembers) {
                if (StringUtils.isNotBlank(householdMember.getHouseholdId())) {
                    membersByHouseholdId
                            .computeIfAbsent(householdMember.getHouseholdId(), k -> new ArrayList<>())
                            .add(householdMember);
                } else if (StringUtils.isNotBlank(householdMember.getHouseholdClientReferenceId())) {
                    String householdClientReferenceId = householdMember.getHouseholdClientReferenceId();
                    String aliasedHouseholdId =
                            householdIdByHouseholdClientReferenceId.get(householdClientReferenceId);
                    if (aliasedHouseholdId != null) {
                        // A sibling of a member that carries both keys: same household, one group.
                        membersByHouseholdId
                                .computeIfAbsent(aliasedHouseholdId, k -> new ArrayList<>())
                                .add(householdMember);
                        mergedClientReferenceKeysByHouseholdId
                                .computeIfAbsent(aliasedHouseholdId, k -> new LinkedHashSet<>())
                                .add(householdClientReferenceId);
                    } else {
                        membersByHouseholdClientReferenceId
                                .computeIfAbsent(householdClientReferenceId, k -> new ArrayList<>())
                                .add(householdMember);
                    }
                } else {
                    // Neither key present - HmRequiredLinkValidator owns this case; skipping here keeps
                    // one malformed member from destroying its siblings.
                    log.error("skipping head-of-household validation for member with clientReferenceId {} "
                            + "- it carries neither householdId nor householdClientReferenceId",
                            householdMember.getClientReferenceId());
                }
            }
            // Blind spot: a two-head conflict spanning both key shapes is only detected when the batch
            // itself carries a bridging member; this validator never looks a household up.
            // The index below closes the STORED side of that when
            // household.member.duplicate.check.by.client.reference.enabled is on. A null index (flag off,
            // or lookup failed) falls each group back to the per-group query.
            ExistingMemberIndex existingMemberIndex =
                    householdMemberConfiguration.isDuplicateCheckByClientReferenceEnabled()
                            ? buildExistingMemberIndex(tenantId, householdMembers)
                            : null;
            membersByHouseholdId.forEach((householdId, householdMembersInHousehold) ->
                    validateHeadOfHousehold(tenantId, householdId, householdMemberidMethod,
                            HOUSEHOLD_ID_FIELD, errorDetailsMap, householdMembersInHousehold,
                            existingMemberIndex,
                            mergedClientReferenceKeysByHouseholdId.getOrDefault(householdId,
                                    Collections.emptySet())));
            // Nothing is ever folded INTO a client-reference group: a bridging member carries a
            // householdId and therefore groups above.
            membersByHouseholdClientReferenceId.forEach((householdClientReferenceId, householdMembersInHousehold) ->
                    validateHeadOfHousehold(tenantId, householdClientReferenceId, householdMemberidMethod,
                            HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD, errorDetailsMap, householdMembersInHousehold,
                            existingMemberIndex, Collections.emptySet()));
        }
        log.debug("household member Head validation completed successfully, total errors: " + errorDetailsMap.size());
        return errorDetailsMap;
    }

    /**
     * Reads the batch's own statements about which {@code householdClientReferenceId} and which
     * {@code householdId} name the same household: every member carrying BOTH keys makes that statement.
     * On contradictory claims the first in request order wins, so grouping is deterministic per request.
     *
     * @return householdClientReferenceId to householdId, containing only the client references the batch
     *         actually bridges
     */
    private Map<String, String> buildHouseholdIdAliases(List<HouseholdMember> householdMembers) {
        Map<String, String> householdIdByHouseholdClientReferenceId = new LinkedHashMap<>();
        for (HouseholdMember householdMember : householdMembers) {
            String householdId = householdMember.getHouseholdId();
            String householdClientReferenceId = householdMember.getHouseholdClientReferenceId();
            if (StringUtils.isBlank(householdId) || StringUtils.isBlank(householdClientReferenceId)) {
                continue;
            }
            String alreadyClaimedHouseholdId = householdIdByHouseholdClientReferenceId
                    .putIfAbsent(householdClientReferenceId, householdId);
            if (alreadyClaimedHouseholdId != null && !alreadyClaimedHouseholdId.equals(householdId)) {
                log.error("conflicting householdIds claimed for householdClientReferenceId {} within one "
                        + "batch - keeping the first claim, the member with clientReferenceId {} groups by "
                        + "the householdId it carries itself", householdClientReferenceId,
                        householdMember.getClientReferenceId());
            }
        }
        return householdIdByHouseholdClientReferenceId;
    }

    /**
     * @param mergedClientReferenceKeys client-reference keys of members folded into this householdId group
     *                                  through an alias; empty when the group was not merged
     */
    private void validateHeadOfHousehold(String tenantId, String householdId, Method householdMemberidMethod, String householdColumnName,
                                         HashMap<HouseholdMember, List<Error>> errorDetailsMap, List<HouseholdMember> householdMembersRequest,
                                         ExistingMemberIndex existingMemberIndex,
                                         Set<String> mergedClientReferenceKeys) {
        log.debug("validating if household already has a head");
        // TRAP: Boolean.TRUE.equals, not the method reference - isHeadOfHousehold is a nullable Boolean
        // and a device omitting the flag NPEs here, discarding the whole batch.
        List<HouseholdMember> requestHouseholdHead = householdMembersRequest.stream()
                .filter(householdMember -> Boolean.TRUE.equals(householdMember.getIsHeadOfHousehold())).toList();

        // HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD: shipped NON_RECOVERABLE on an unmerged group. RECOVERABLE on a
        // merged one - the conflict is a property of how this batch was partitioned, not of the records,
        // and NON_RECOVERABLE is final for the device (lost, not delayed).
        Error.ErrorType moreThanOneHeadErrorType = mergedClientReferenceKeys.isEmpty()
                ? Error.ErrorType.NON_RECOVERABLE
                : Error.ErrorType.RECOVERABLE;

        // Validates if a household has more than 1 heads
        if(requestHouseholdHead.size() > 1) {
            householdMembersRequest.forEach(householdMember -> {
                Error error = Error.builder().errorMessage(HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD_MESSAGE)
                        .errorCode(HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD)
                        .type(moreThanOneHeadErrorType)
                        .exception(new CustomException(HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD,
                                HOUSEHOLD_HAS_MORE_THAN_ONE_HEAD_MESSAGE))
                        .build();
                populateErrorDetails(householdMember, error, errorDetailsMap);
            });
            log.error("More than one head of household found for household {}", householdId);
            return;
        }
        try {
            List<HouseholdMember> existingHouseholdMembers = existingMemberIndex != null
                    ? existingMembersFromIndex(existingMemberIndex, householdId, householdColumnName,
                            householdMembersRequest)
                    : findStoredMembers(tenantId, householdId, householdColumnName,
                            mergedClientReferenceKeys);
            List<HouseholdMember> existingHouseholdHead = existingHouseholdMembers
                    .stream().filter(householdMember -> Boolean.TRUE.equals(householdMember.getIsHeadOfHousehold())).toList();
            // HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED and HOUSEHOLD_ALREADY_HAS_HEAD: RECOVERABLE while the
            // lookup is widened or the group merged, because a legitimate head reassignment looks like a
            // conflict until the update clearing the previous head drains off the persister queue.
            // Otherwise the shipped NON_RECOVERABLE. HOUSEHOLD_DOES_NOT_HAVE_A_HEAD is left alone - more
            // visible rows can only make it fire less often.
            Error.ErrorType existingHeadErrorType =
                    existingMemberIndex != null || !mergedClientReferenceKeys.isEmpty()
                            ? Error.ErrorType.RECOVERABLE
                            : Error.ErrorType.NON_RECOVERABLE;

            // Validates if a household doesn't have a head
            if(requestHouseholdHead.isEmpty() && existingHouseholdHead.isEmpty()) {
                householdMembersRequest.forEach(householdMember -> {
                    Error error = Error.builder().errorMessage(HOUSEHOLD_DOES_NOT_HAVE_A_HEAD_MESSAGE)
                            .errorCode(HOUSEHOLD_DOES_NOT_HAVE_A_HEAD)
                            .type(Error.ErrorType.NON_RECOVERABLE)
                            .exception(new CustomException(HOUSEHOLD_DOES_NOT_HAVE_A_HEAD,
                                    HOUSEHOLD_DOES_NOT_HAVE_A_HEAD_MESSAGE))
                            .build();
                    populateErrorDetails(householdMember, error, errorDetailsMap);
                });
                log.error("No head of household found for household {}", householdId);
                return;
            }

            // Validate if household head is removed
            if(requestHouseholdHead.isEmpty()) {
                HouseholdMember existingHead = existingHouseholdHead.get(0);
                String existingHeadMemberId = (String) ReflectionUtils.invokeMethod(householdMemberidMethod, existingHead);
                Optional<HouseholdMember> unassignedHouseholdHead = householdMembersRequest.stream().filter(householdMember ->
                        Objects.equals(existingHeadMemberId, ReflectionUtils.invokeMethod(householdMemberidMethod, householdMember)))
                        .findFirst();
                if(unassignedHouseholdHead.isPresent()) {
                    Error error = Error.builder().errorMessage(HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED_MESSAGE)
                            .errorCode(HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED)
                            .type(existingHeadErrorType)
                            .exception(new CustomException(HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED,
                                    HOUSEHOLD_HEAD_CANNOT_BE_UNASSIGNED_MESSAGE))
                            .build();
                    populateErrorDetails(unassignedHouseholdHead.get(), error, errorDetailsMap);
                    log.error("household head cannot be unassigned, error: {}", error);
                    return;
                }
            }

            // Validates if a household head reassignment is valid
            if(!existingHouseholdHead.isEmpty() && !requestHouseholdHead.isEmpty()) {
                HouseholdMember existingHead = existingHouseholdHead.get(0);
                String existingHeadMemberId = (String) ReflectionUtils.invokeMethod(householdMemberidMethod, existingHead);
                String currentHeadMemberId = (String) ReflectionUtils.invokeMethod(householdMemberidMethod, requestHouseholdHead.get(0));
                boolean isReassigning = existingHeadMemberId != null && currentHeadMemberId != null
                        && !existingHeadMemberId.equals(currentHeadMemberId);
                boolean existingHeadInRequest = householdMembersRequest.stream()
                        .anyMatch(householdMember -> {
                            String existingHeadMemberIdInRequest = (String) ReflectionUtils.invokeMethod(householdMemberidMethod, householdMember);
                            return existingHeadMemberIdInRequest != null && existingHeadMemberIdInRequest.equals(existingHeadMemberId);
                        });

                // Validate if household head is being reassigned but existing head is not present
                // in the request with isHeadOfHousehold = false
                if(isReassigning && !existingHeadInRequest) {
                    Error error = Error.builder().errorMessage(HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE)
                            .errorCode(HOUSEHOLD_ALREADY_HAS_HEAD)
                            .type(existingHeadErrorType)
                            .exception(new CustomException(HOUSEHOLD_ALREADY_HAS_HEAD,
                                    HOUSEHOLD_ALREADY_HAS_HEAD_MESSAGE))
                            .build();
                    log.error("household already has a head, error: {}", error);
                    populateErrorDetails(requestHouseholdHead.get(0), error, errorDetailsMap);
                }
            }
        } catch (InvalidTenantIdException exception) {
            log.error("Invalid tenantId found for household members {}", householdMembersRequest, exception);
            householdMembersRequest.forEach(householdMember -> {
                Error error = getErrorForInvalidTenantId(tenantId, exception);
                populateErrorDetails(householdMember, error, errorDetailsMap);
            });
        }
    }

    /**
     * Stored members of the household this group points at, on the path where the batch index is off or
     * unavailable.
     *
     * <p>An unmerged group issues exactly the one query that ships today. A merged group additionally
     * queries each folded-in client-reference key with its own column and unions the rows in, so no member
     * sees fewer rows than it would have before the merge. Query count per bulk request does not grow.</p>
     */
    private List<HouseholdMember> findStoredMembers(String tenantId, String householdKey,
                                                    String householdColumnName,
                                                    Set<String> mergedClientReferenceKeys)
            throws InvalidTenantIdException {
        List<HouseholdMember> storedRows = householdMemberRepository
                .findIndividualByHousehold(tenantId, householdKey, householdColumnName).getResponse();
        if (mergedClientReferenceKeys.isEmpty()) {
            return storedRows;
        }
        Map<String, HouseholdMember> distinctStoredRows = new LinkedHashMap<>();
        storedRows.forEach(storedRow -> distinctStoredRows.putIfAbsent(storedRowKey(storedRow), storedRow));
        for (String mergedClientReferenceKey : mergedClientReferenceKeys) {
            householdMemberRepository.findIndividualByHousehold(tenantId, mergedClientReferenceKey,
                            HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD).getResponse()
                    .forEach(storedRow -> distinctStoredRows.putIfAbsent(storedRowKey(storedRow), storedRow));
        }
        return new ArrayList<>(distinctStoredRows.values());
    }

    /**
     * Deduplication key for a stored row. A row carrying neither identifier falls back to
     * {@link System#identityHashCode}, which is NOT unique, so two such rows can collide and one be
     * dropped - harmless here, the rules only ask whether a stored head exists and take the first.
     */
    private static String storedRowKey(HouseholdMember storedRow) {
        if (StringUtils.isNotBlank(storedRow.getId())) {
            return ID_FIELD + ":" + storedRow.getId();
        }
        if (StringUtils.isNotBlank(storedRow.getClientReferenceId())) {
            return CLIENT_REFERENCE_ID_FIELD + ":" + storedRow.getClientReferenceId();
        }
        return "identity:" + System.identityHashCode(storedRow);
    }

    /**
     * Stored members of the household this group points at, resolved from BOTH parent-key shapes.
     *
     * <p>Reach limit: a stored row is found only when it shares a parent key with one of the requesting
     * members. A row carrying ONLY a {@code householdClientReferenceId} they do not carry stays invisible -
     * resolving it would need a household lookup, which this validator does not do.</p>
     */
    private static List<HouseholdMember> existingMembersFromIndex(ExistingMemberIndex existingMemberIndex,
                                                                 String householdKey,
                                                                 String householdColumnName,
                                                                 List<HouseholdMember> householdMembersRequest) {
        if (HOUSEHOLD_ID_FIELD.equals(householdColumnName)) {
            List<String> householdClientReferenceIds = ExistingMemberIndex.distinctNonBlank(
                    householdMembersRequest.stream().map(HouseholdMember::getHouseholdClientReferenceId)
                            .collect(Collectors.toList()));
            return existingMemberIndex.rowsFor(Collections.singletonList(householdKey),
                    householdClientReferenceIds);
        }
        // This group's members carry no householdId today; collected anyway rather than hard-coded empty,
        // so the call stays correct if the grouping above changes.
        List<String> householdIds = ExistingMemberIndex.distinctNonBlank(householdMembersRequest.stream()
                .map(HouseholdMember::getHouseholdId).collect(Collectors.toList()));
        return existingMemberIndex.rowsFor(householdIds, Collections.singletonList(householdKey));
    }

    /**
     * Loads every stored non-deleted household_member row reachable from the batch's household keys, in one
     * batch query per key column, and indexes it by both keys.
     *
     * @return the index, or {@code null} if the lookup could not be completed - each group then falls back
     *         to the per-group query that ships today instead of the batch failing.
     */
    private ExistingMemberIndex buildExistingMemberIndex(String tenantId, List<HouseholdMember> householdMembers) {
        try {
            List<String> householdIds = ExistingMemberIndex.distinctNonBlank(householdMembers.stream()
                    .map(HouseholdMember::getHouseholdId).collect(Collectors.toList()));
            List<String> householdClientReferenceIds = ExistingMemberIndex.distinctNonBlank(householdMembers
                    .stream().map(HouseholdMember::getHouseholdClientReferenceId)
                    .collect(Collectors.toList()));

            List<HouseholdMember> storedRows = new ArrayList<>();
            // One batch query per key column: the repository primitives filter on a single column.
            // TRAP: findById mutates the id list it is handed, hence the defensive copies.
            if (!householdIds.isEmpty()) {
                storedRows.addAll(householdMemberRepository.findById(tenantId, new ArrayList<>(householdIds),
                        HOUSEHOLD_ID_FIELD, Boolean.FALSE).getResponse());
            }
            if (!householdClientReferenceIds.isEmpty()) {
                storedRows.addAll(householdMemberRepository.findById(tenantId,
                        new ArrayList<>(householdClientReferenceIds), HOUSEHOLD_CLIENT_REFERENCE_ID_FIELD,
                        Boolean.FALSE).getResponse());
            }
            return new ExistingMemberIndex(storedRows, HouseholdMember::getHouseholdId,
                    HouseholdMember::getHouseholdClientReferenceId);
        } catch (Exception exception) {
            log.warn("error while loading stored household member rows for the widened head-of-household "
                    + "check, falling back to the per-household lookup: {}",
                    ExceptionUtils.getStackTrace(exception));
            return null;
        }
    }
}
