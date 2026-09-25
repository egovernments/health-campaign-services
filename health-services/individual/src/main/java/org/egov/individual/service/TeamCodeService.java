package org.egov.individual.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.individual.Individual;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.common.utils.CommonUtils;
import org.egov.individual.config.IndividualProperties;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import static org.egov.individual.Constants.AMBIGUOUS_SELECTOR;
import static org.egov.individual.Constants.INDIVIDUAL_NOT_FOUND;
import static org.egov.individual.Constants.INDIVIDUAL_SCHEMA;
import static org.egov.individual.Constants.INVALID_SELECTOR;
import static org.egov.individual.Constants.INVALID_TEAM_CODE_COUNT;
import static org.egov.individual.Constants.INVALID_TENANT_ID;
import static org.egov.individual.Constants.INVALID_TENANT_ID_MSG;
import static org.egov.individual.Constants.TEAM_ASSIGNMENT_BATCH_TOO_LARGE;
import static org.egov.individual.Constants.TEAM_CODE_FIELD_KEY;
import static org.egov.individual.Constants.TEAM_CODE_GENERATION_FAILED;
import static org.egov.individual.Constants.TEAM_CODE_INACTIVE;
import static org.egov.individual.Constants.TEAM_CODE_NOT_FOUND;

/**
 * Mints team codes and maintains the team code carried by an individual in
 * additionalFields under the key {@code team_code}.
 *
 * Every membership change is also recorded synchronously in individual_team_code_mapping, which
 * is what /team/v1/_search reports and what assignedCount is recomputed from.
 *
 * The individual is written asynchronously - save() publishes to update-individual-topic and
 * egov-persister applies the UPDATE some time later. Two consequences are accepted by design:
 *
 * - a repeat of the same assignment issued before the persister has drained is not recognised by
 *   the no-op short circuit, so it republishes the individual and bumps rowVersion a second time.
 *   The mapping table absorbs this: the row is keyed by (tenantId, teamCode, individualId), so the
 *   replay keeps the original assignedBy / assignedTime and assignedCount stays correct.
 * - the republished individual is a full row rewrite with no optimistic lock predicate, so a
 *   concurrent edit to the same individual that the persister has not yet applied can be lost.
 * - addresses with no captured coordinates get their NULL latitude / longitude / locationAccuracy
 *   rewritten to 0. The record republished here is the one AddressRowMapper produced, and it reads
 *   those columns with resultSet.getDouble(), which yields 0.0 for SQL NULL; the persister's update
 *   handler then writes all three back for every address in the payload. Assigning a team code to
 *   an individual whose address never had a GPS fix therefore moves that address to 0,0. Accepted
 *   rather than fixed because the root cause is in the shared AddressRowMapper and correcting it
 *   would change what /individual/v1/_search and beneficiary downsync return to the APK. Check
 *   whether any address rows in the target tenant hold NULL coordinates before running a backfill.
 */
@Service
@Slf4j
public class TeamCodeService {

    private final IdGenService idGenService;

    private final IndividualRepository individualRepository;

    private final TeamCodeRepository teamCodeRepository;

    private final IndividualProperties properties;

    private final Producer producer;

    @Autowired
    public TeamCodeService(IdGenService idGenService,
                           IndividualRepository individualRepository,
                           TeamCodeRepository teamCodeRepository,
                           IndividualProperties properties,
                           @Qualifier("individualProducer") Producer producer) {
        this.idGenService = idGenService;
        this.individualRepository = individualRepository;
        this.teamCodeRepository = teamCodeRepository;
        this.properties = properties;
        this.producer = producer;
    }

    public List<String> generateTeamCodes(TeamCodeGenerationRequest request) {
        if (StringUtils.isBlank(request.getTenantId())) {
            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
        }

        int count = request.getCount() == null ? 1 : request.getCount();
        int max = properties.getTeamCodeGenerateMax();
        if (count < 1 || count > max) {
            throw new CustomException(INVALID_TEAM_CODE_COUNT, "count must be between 1 and " + max);
        }

        //fetch the root tenantId if it is in state.city format
        final String rootTenantId = request.getTenantId().split("\\.")[0];
        log.info("generating {} team codes for tenant {}", count, request.getTenantId());
        List<String> codes = idGenService.getIdList(request.getRequestInfo(), rootTenantId,
                properties.getTeamCodeIdFormat(), null, count);
        if (CollectionUtils.isEmpty(codes) || codes.size() != count) {
            throw new CustomException(TEAM_CODE_GENERATION_FAILED,
                    "idgen returned " + (codes == null ? 0 : codes.size()) + " team codes for a request of " + count);
        }

        AuditDetails auditDetails = CommonUtils.getAuditDetailsForCreate(request.getRequestInfo());
        List<TeamCode> teamCodes = new ArrayList<>();
        for (String code : codes) {
            teamCodes.add(TeamCode.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(request.getTenantId())
                    .teamCode(code)
                    .rowVersion(1)
                    .isDeleted(Boolean.FALSE)
                    .auditDetails(auditDetails)
                    .build());
        }

        producer.push(request.getTenantId(), properties.getSaveTeamCodeTopic(), teamCodes);
        log.info("published {} minted team codes for tenant {}", teamCodes.size(), request.getTenantId());
        return codes;
    }

    /**
     * Assigns, replaces or removes a team code across every individual the selector resolves to.
     * A whole team shares one code, so this is a batch: pass the team's members in one selector
     * list and they all move together.
     *
     * All-or-nothing on resolution - if any selector value matches no system user, or matches
     * more than one, nothing is written at all.
     *
     * @return the resolved individual ids plus the updated / skipped split
     */
    public TeamAssignmentResult updateTeamCode(TeamAssignmentRequest request) {
        TeamAssignment assignment = request.getTeamAssignment();
        String tenantId = assignment.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
        }

        List<String> individualIds = normalise(assignment.getIndividualId());
        List<String> usernames = normalise(assignment.getUsername());
        List<String> userUuids = normalise(assignment.getUserUuid());
        int populated = (individualIds.isEmpty() ? 0 : 1) + (usernames.isEmpty() ? 0 : 1)
                + (userUuids.isEmpty() ? 0 : 1);
        if (populated != 1) {
            throw new CustomException(INVALID_SELECTOR,
                    "exactly one of individualId, username or userUuid must be populated");
        }

        SelectorColumn selectorColumn;
        List<String> selectorValues;
        if (!individualIds.isEmpty()) {
            selectorColumn = SelectorColumn.ID;
            selectorValues = individualIds;
        } else if (!usernames.isEmpty()) {
            selectorColumn = SelectorColumn.USERNAME;
            selectorValues = usernames;
        } else {
            selectorColumn = SelectorColumn.USER_UUID;
            selectorValues = userUuids;
        }

        Integer batchSize = properties.getTeamAssignBatchSize();
        if (selectorValues.size() > batchSize) {
            throw new CustomException(TEAM_ASSIGNMENT_BATCH_TOO_LARGE,
                    "at most " + batchSize + " individuals can be assigned in one call, got "
                            + selectorValues.size());
        }

        String requested = StringUtils.trimToNull(assignment.getTeamCode());
        if (requested != null) {
            validateTeamCodeIsAssignable(tenantId, requested);
        }

        List<String> resolvedIds = resolveSelector(tenantId, selectorColumn, selectorValues);

        List<Individual> individuals;
        try {
            // findById returns the individuals fully enriched, which is what the persister expects
            // back on the update topic
            individuals = individualRepository.findById(tenantId, new ArrayList<>(resolvedIds), "id", false)
                    .getResponse();
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
        }

        String requesterUuid = request.getRequestInfo().getUserInfo().getUuid();
        long now = System.currentTimeMillis();
        List<Individual> changed = new ArrayList<>();
        List<Individual> noOpButStillMapped = new ArrayList<>();

        for (Individual individual : individuals) {
            String current = AdditionalFieldsUtil.getFieldValue(individual.getAdditionalFields(),
                    TEAM_CODE_FIELD_KEY);
            if (Objects.equals(current, requested)) {
                log.info("team code {} already set on individual {}, nothing to publish", current,
                        individual.getId());
                // the individual needs no republish, but the mapping trail still does: `current`
                // comes from the asynchronously persisted row, so it can be stale or null even
                // when the membership is already correct. Maintaining it here is idempotent.
                noOpButStillMapped.add(individual);
                continue;
            }
            if (requested == null) {
                individual.setAdditionalFields(
                        AdditionalFieldsUtil.removeField(individual.getAdditionalFields(), TEAM_CODE_FIELD_KEY));
            } else {
                individual.setAdditionalFields(AdditionalFieldsUtil.upsertField(individual.getAdditionalFields(),
                        INDIVIDUAL_SCHEMA, TEAM_CODE_FIELD_KEY, requested));
            }
            individual.setRowVersion(individual.getRowVersion() == null ? 1 : individual.getRowVersion() + 1);
            individual.setAuditDetails(CommonUtils.getAuditDetailsForUpdate(individual.getAuditDetails(),
                    requesterUuid));
            changed.add(individual);
        }

        // mapping maintenance covers everything the selector resolved to, published or not
        List<Individual> allResolved = new ArrayList<>(changed);
        allResolved.addAll(noOpButStillMapped);
        if (!allResolved.isEmpty()) {
            recordMembershipChanges(tenantId, requested, allResolved, requesterUuid, now);
        }

        if (!changed.isEmpty()) {
            // the individuals came straight out of the repository, so their PII (mobileNumber,
            // identifiers) is still in its stored encrypted form - republishing them as is round trips
            // those values untouched. They must NOT go through IndividualEncryptionService, that would
            // double encrypt. Publishing to update-individual-topic keeps postgres, redis and the
            // individual ES index in sync. One publish for the whole batch.
            individualRepository.save(changed, properties.getUpdateIndividualTopic());
            log.info("published team code {} for {} individuals", requested, changed.size());
        }

        List<TeamAssignmentEntry> assignments = new ArrayList<>();
        changed.forEach(individual -> assignments.add(entry(individual, "UPDATED")));
        noOpButStillMapped.forEach(individual -> assignments.add(entry(individual, "SKIPPED")));

        return TeamAssignmentResult.builder()
                .tenantId(tenantId)
                .assignments(assignments)
                .teamCode(requested)
                .updatedCount(changed.size())
                .skippedCount(individuals.size() - changed.size())
                .build();
    }

    /**
     * Publishes the membership changes for the batch.
     *
     * An assignment is one message per individual: the persister ends that individual's other
     * live memberships and inserts the new ASSIGNED row inside a single transaction. The sweep is
     * blind on the previous code, so nothing here has to read back what the individual is
     * currently on - which is what makes it safe to do this asynchronously.
     *
     * A removal has no new row to record, so it goes to the update topic, which only ends the
     * live memberships.
     */
    private void recordMembershipChanges(String tenantId, String requested, List<Individual> individuals,
                                         String requesterUuid, long now) {
        List<TeamCodeMapping> events = individuals.stream()
                .map(individual -> mappingEvent(tenantId, requested, individual, requesterUuid, now))
                .toList();
        String topic = requested != null ? properties.getSaveTeamCodeMappingTopic()
                : properties.getUpdateTeamCodeMappingTopic();
        producer.push(tenantId, topic, events);
        log.info("published {} team code membership changes to {}", events.size(), topic);
    }

    private void validateTeamCodeIsAssignable(String tenantId, String teamCode) {
        List<TeamCode> teamCodes;
        try {
            teamCodes = teamCodeRepository.findByTeamCodes(tenantId, Collections.singletonList(teamCode));
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
        }
        if (CollectionUtils.isEmpty(teamCodes)) {
            throw new CustomException(TEAM_CODE_NOT_FOUND, teamCode);
        }
        if (TeamCodeStatus.INACTIVE.equals(teamCodes.get(0).getStatus())) {
            throw new CustomException(TEAM_CODE_INACTIVE, teamCode);
        }
    }

    /**
     * Every selector value has to name exactly one system user, otherwise the whole call fails
     * before anything is written.
     */
    private List<String> resolveSelector(String tenantId, SelectorColumn selectorColumn, List<String> values) {
        Map<String, List<String>> idsByValue;
        try {
            idsByValue = individualRepository.findSystemUserIdsBySelector(tenantId, selectorColumn, values);
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
        }

        List<String> missing = values.stream().filter(value -> CollectionUtils.isEmpty(idsByValue.get(value)))
                .toList();
        if (!missing.isEmpty()) {
            throw new CustomException(INDIVIDUAL_NOT_FOUND,
                    "no system user found for " + selectorColumn.getColumn() + " " + missing);
        }
        List<String> ambiguous = values.stream().filter(value -> idsByValue.get(value).size() > 1).toList();
        if (!ambiguous.isEmpty()) {
            throw new CustomException(AMBIGUOUS_SELECTOR,
                    "more than one system user found for " + selectorColumn.getColumn() + " " + ambiguous);
        }
        return values.stream().map(value -> idsByValue.get(value).get(0)).distinct().toList();
    }

    /**
     * Trims, drops blanks and de-duplicates while keeping the caller's ordering.
     */
    private List<String> normalise(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        return values.stream().map(StringUtils::trimToNull).filter(Objects::nonNull).distinct().toList();
    }

    /**
     * Pages the team codes matching the criteria. Pass status=[UNASSIGNED] to list the codes that
     * have been minted but handed to nobody yet.
     *
     * @param includeMembers true to attach the mapping rows for the page in one extra query
     */
    public SearchResponse<TeamCode> searchTeamCodes(TeamCodeSearch criteria, Integer limit, Integer offset,
                                                    String tenantId, Boolean includeDeleted,
                                                    Boolean includeMembers) {
        if (StringUtils.isBlank(tenantId)) {
            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
        }
        SearchResponse<TeamCode> searchResponse;
        try {
            searchResponse = teamCodeRepository.search(criteria, limit, offset, tenantId, includeDeleted);
        } catch (InvalidTenantIdException e) {
            throw new CustomException(INVALID_TENANT_ID, INVALID_TENANT_ID_MSG);
        }

        if (Boolean.TRUE.equals(includeMembers) && !CollectionUtils.isEmpty(searchResponse.getResponse())) {
            attachMembers(tenantId, searchResponse.getResponse());
        }
        return searchResponse;
    }

    /**
     * One query for the whole page rather than one per team code. Both current and past members
     * are returned so the caller can see who was removed and by whom.
     */
    private void attachMembers(String tenantId, List<TeamCode> teamCodes) {
        List<String> codes = teamCodes.stream().map(TeamCode::getTeamCode).filter(Objects::nonNull).toList();
        List<TeamCodeMapping> mappings;
        try {
            mappings = teamCodeRepository.findMappingsByTeamCodes(tenantId, codes, false);
        } catch (Exception e) {
            log.error("error loading team code members for tenant {}", tenantId, e);
            return;
        }
        Map<String, List<TeamCodeMapping>> byCode = mappings.stream()
                .collect(Collectors.groupingBy(TeamCodeMapping::getTeamCode));
        teamCodes.forEach(teamCode ->
                teamCode.setMembers(byCode.getOrDefault(teamCode.getTeamCode(), Collections.emptyList())));
    }

    /**
     * Echoes every identifier the individual has, so the caller finds whichever one it sent.
     */
    private TeamAssignmentEntry entry(Individual individual, String status) {
        return TeamAssignmentEntry.builder()
                .individualId(individual.getId())
                .userUuid(individual.getUserUuid())
                .username(individual.getUserDetails() == null ? null
                        : individual.getUserDetails().getUsername())
                .status(status)
                .build();
    }

    private TeamCodeMapping mappingEvent(String tenantId, String teamCode, Individual individual,
                                        String requesterUuid, long now) {
        return TeamCodeMapping.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .teamCode(teamCode)
                .individualId(individual.getId())
                .userUuid(individual.getUserUuid())
                .status(TeamCodeStatus.ASSIGNED)
                .assignedBy(requesterUuid)
                .assignedTime(now)
                .auditDetails(AuditDetails.builder()
                        .createdBy(requesterUuid)
                        .createdTime(now)
                        .lastModifiedBy(requesterUuid)
                        .lastModifiedTime(now)
                        .build())
                .rowVersion(1)
                .isDeleted(Boolean.FALSE)
                .build();
    }
}
