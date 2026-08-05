package org.egov.household.household.member.validators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.household.HouseholdMember;

/**
 * In-memory index over already-stored household_member rows, keyed by BOTH shapes of a parent link: the
 * server id and the device-minted clientReferenceId. A stored row legitimately carries only one of the two
 * (unsynced individual or household leaves the server-id column NULL), so a server-id-only guard cannot see
 * it - which is how a second membership row and a second head got past validation.
 *
 * <p>Lookup is per record, from the keys that record itself carries - never from a key sampled off one
 * arbitrary element of the batch.</p>
 */
final class ExistingMemberIndex {

    private final Map<String, List<HouseholdMember>> byServerIdKey = new LinkedHashMap<>();

    private final Map<String, List<HouseholdMember>> byClientReferenceKey = new LinkedHashMap<>();

    /**
     * @param rows                stored rows loaded from the repository, in any order, possibly containing
     *                            the same row twice (one batch query per key column returns overlapping sets)
     * @param serverIdKey         reads the server-id parent key off a row, e.g. {@code getIndividualId}
     * @param clientReferenceKey  reads the device-minted parent key off a row, e.g.
     *                            {@code getIndividualClientReferenceId}
     */
    ExistingMemberIndex(List<HouseholdMember> rows,
                        Function<HouseholdMember, String> serverIdKey,
                        Function<HouseholdMember, String> clientReferenceKey) {
        for (HouseholdMember row : rows) {
            String id = serverIdKey.apply(row);
            if (StringUtils.isNotBlank(id)) {
                byServerIdKey.computeIfAbsent(id, k -> new ArrayList<>()).add(row);
            }
            String clientReferenceId = clientReferenceKey.apply(row);
            if (StringUtils.isNotBlank(clientReferenceId)) {
                byClientReferenceKey.computeIfAbsent(clientReferenceId, k -> new ArrayList<>()).add(row);
            }
        }
    }

    /**
     * Every stored row reachable from any of the given keys, de-duplicated: a row carrying both keys is
     * returned by both batch queries and would otherwise be counted twice.
     *
     * @param serverIds           server-id parent keys to resolve; blanks and nulls are ignored
     * @param clientReferenceIds  device-minted parent keys to resolve; blanks and nulls are ignored
     */
    List<HouseholdMember> rowsFor(Collection<String> serverIds, Collection<String> clientReferenceIds) {
        List<HouseholdMember> candidates = new ArrayList<>();
        collect(byServerIdKey, serverIds, candidates);
        collect(byClientReferenceKey, clientReferenceIds, candidates);

        List<HouseholdMember> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (HouseholdMember candidate : candidates) {
            if (seen.add(identityOf(candidate))) {
                rows.add(candidate);
            }
        }
        return rows;
    }

    private static void collect(Map<String, List<HouseholdMember>> index, Collection<String> keys,
                                List<HouseholdMember> into) {
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            if (StringUtils.isBlank(key)) {
                continue;
            }
            List<HouseholdMember> rows = index.get(key);
            if (rows != null) {
                into.addAll(rows);
            }
        }
    }

    /**
     * True when the stored row IS the record being validated. Matching on either of the record's own keys
     * keeps a guard from rejecting a record for finding itself.
     */
    static boolean isSameMember(HouseholdMember member, HouseholdMember storedRow) {
        return (StringUtils.isNotBlank(member.getId()) && member.getId().equals(storedRow.getId()))
                || (StringUtils.isNotBlank(member.getClientReferenceId())
                        && member.getClientReferenceId().equals(storedRow.getClientReferenceId()));
    }

    /**
     * De-duplication key for a stored row. The clientReferenceId fallback covers a row served from the
     * save-path cache before the persister has written it; the last fallback keeps a row with neither key
     * from silently collapsing into another such row.
     */
    static String identityOf(HouseholdMember storedRow) {
        if (StringUtils.isNotBlank(storedRow.getId())) {
            return "id:" + storedRow.getId();
        }
        if (StringUtils.isNotBlank(storedRow.getClientReferenceId())) {
            return "clientReferenceId:" + storedRow.getClientReferenceId();
        }
        return "identityHashCode:" + System.identityHashCode(storedRow);
    }

    /** Distinct, non-blank values, preserving encounter order. */
    static List<String> distinctNonBlank(Collection<String> values) {
        return values.stream().filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
    }
}
