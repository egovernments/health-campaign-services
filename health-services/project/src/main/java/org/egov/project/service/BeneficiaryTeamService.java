package org.egov.project.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.producer.Producer;
import org.egov.common.utils.CommonUtils;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.IndividualTeamCodeRepository;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.repository.ProjectRepository;
import org.egov.project.util.AdditionalFieldsUtil;
import org.egov.project.web.models.team.BeneficiaryTeamAssignment;
import org.egov.project.web.models.team.BeneficiaryTeamAssignmentRequest;
import org.egov.project.web.models.team.BeneficiaryTeamAssignmentResult;
import org.egov.project.web.models.team.BeneficiaryTeamMapping;
import org.egov.project.web.models.team.BeneficiaryTeamRelocation;
import org.egov.project.web.models.team.BeneficiaryTeamRelocationRequest;
import org.egov.project.web.models.team.BeneficiaryTeamRelocationResult;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import static org.egov.project.Constants.INVALID_PROJECT_ID;
import static org.egov.project.Constants.INVALID_RELOCATION;
import static org.egov.project.Constants.INVALID_TEAM_SELECTOR;
import static org.egov.project.Constants.PROJECT_BENEFICIARY_NOT_FOUND;
import static org.egov.project.Constants.PROJECT_BENEFICIARY_SCHEMA;
import static org.egov.project.Constants.RELOCATION_TOO_LARGE;
import static org.egov.project.Constants.REGISTERED_BY_TEAM;
import static org.egov.project.Constants.USERNAME_NOT_FOUND;
import static org.egov.project.Constants.USER_NOT_IN_TEAM;

/**
 * Attributes project beneficiaries to a team code under additionalFields.registered_by_team, and
 * appends the change to the PROJECT_BENEFICIARY_TEAM_MAPPING history.
 *
 * Two addressing modes, mutually exclusive:
 * - clientReferenceIds names the beneficiaries. Looked up and published in configured batches so a
 *   list of any length is accepted without holding it all in memory at once.
 * - usernames / userUuids name the field staff, and every beneficiary they registered is
 *   attributed. Processed one user at a time and keyset paged, because a single distributor's
 *   registrations can run to tens of thousands.
 *
 * Writes are asynchronous in both directions: the beneficiary goes to
 * update-project-beneficiary-topic and the history row to
 * save-project-beneficiary-team-mapping-topic, and egov-persister applies both later. Nothing here
 * re-reads what it wrote. Paging by createdBy while publishing is safe precisely because createdBy
 * is never modified - only additionalDetails is.
 */
@Service
@Slf4j
public class BeneficiaryTeamService {

    private final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    private final IndividualTeamCodeRepository individualTeamCodeRepository;

    private final ProjectRepository projectRepository;

    private final ProjectConfiguration projectConfiguration;

    private final Producer producer;

    public BeneficiaryTeamService(ProjectBeneficiaryRepository projectBeneficiaryRepository,
                                  IndividualTeamCodeRepository individualTeamCodeRepository,
                                  ProjectRepository projectRepository,
                                  ProjectConfiguration projectConfiguration,
                                  Producer producer) {
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
        this.individualTeamCodeRepository = individualTeamCodeRepository;
        this.projectRepository = projectRepository;
        this.projectConfiguration = projectConfiguration;
        this.producer = producer;
    }

    public BeneficiaryTeamAssignmentResult updateTeamCode(BeneficiaryTeamAssignmentRequest request) {
        BeneficiaryTeamAssignment assignment = request.getBeneficiaryTeamAssignment();
        RequestInfo requestInfo = request.getRequestInfo();
        String tenantId = assignment.getTenantId();
        String projectId = StringUtils.trimToNull(assignment.getProjectId());
        String requested = StringUtils.trimToNull(assignment.getTeamCode());

        List<String> clientReferenceIds = normalise(assignment.getClientReferenceIds());
        List<String> usernames = normalise(assignment.getUsernames());
        List<String> userUuids = normalise(assignment.getUserUuids());

        validateProjectId(tenantId, projectId);

        boolean byBeneficiary = !clientReferenceIds.isEmpty();
        boolean byUser = !usernames.isEmpty() || !userUuids.isEmpty();
        if (byBeneficiary == byUser) {
            throw new CustomException(INVALID_TEAM_SELECTOR, "populate either clientReferenceIds, or "
                    + "usernames / userUuids - exactly one of the two modes");
        }

        // the team code must exist and be live before anything is written, in both modes
        Set<String> teamMemberUserUuids = Collections.emptySet();
        if (requested != null) {
            teamMemberUserUuids = individualTeamCodeRepository
                    .fetchAssignableTeamMemberUserUuids(tenantId, requested, requestInfo);
        }

        Context context = new Context(tenantId, projectId, requested, null, requestInfo,
                System.currentTimeMillis());

        if (byBeneficiary) {
            // all-or-nothing: every clientReferenceId has to exist before the first publish
            validateClientReferenceIdsExist(context, clientReferenceIds);
            assignByClientReferenceIds(context, clientReferenceIds);
        } else {
            List<String> resolvedUserUuids = resolveUsers(tenantId, usernames, userUuids, requestInfo);
            // on assignment every named user has to already be on the team; on removal there is no
            // team to be a member of, so the check does not apply
            if (requested != null) {
                validateUsersAreTeamMembers(resolvedUserUuids, teamMemberUserUuids, requested);
            }
            assignByUsers(context, resolvedUserUuids);
        }

        return BeneficiaryTeamAssignmentResult.builder()
                .teamCode(requested)
                .matchedCount(context.matched)
                .updatedCount(context.updated)
                .skippedCount(context.skipped)
                .build();
    }

    /**
     * Moves beneficiaries onto toTeamCode and records the reason in the history.
     *
     * Addressed either by fromTeamCode (the whole team moves) or by clientReferenceIds (exactly
     * those beneficiaries move, whatever team they are on).
     *
     * The fromTeamCode flow is split into a read phase and a write phase, and the split is not
     * stylistic. The predicate being read - registered_by_team - is the very field being written,
     * and it is only reachable through a GIN index, which has no ordering and so cannot serve a
     * keyset cursor. Offset paging over a predicate you are concurrently mutating skips rows. So
     * the read phase completes first, collecting ids, and the write phase then addresses rows by
     * immutable id and never consults the team predicate again.
     *
     * "The beneficiary's current team is fromTeamCode" needs no per-row check: it is the selection
     * predicate, so every row reached satisfies it by construction.
     */
    public BeneficiaryTeamRelocationResult relocateTeamCode(BeneficiaryTeamRelocationRequest request) {
        BeneficiaryTeamRelocation relocation = request.getBeneficiaryTeamRelocation();
        RequestInfo requestInfo = request.getRequestInfo();
        String tenantId = relocation.getTenantId();
        String projectId = StringUtils.trimToNull(relocation.getProjectId());
        String fromTeamCode = StringUtils.trimToNull(relocation.getFromTeamCode());
        String toTeamCode = StringUtils.trimToNull(relocation.getToTeamCode());
        String reason = StringUtils.trimToNull(relocation.getRelocationReason());
        List<String> clientReferenceIds = normalise(relocation.getClientReferenceIds());

        if (StringUtils.isBlank(tenantId)) {
            throw new CustomException("INVALID_TENANT_ID", "tenantId is required");
        }
        if (toTeamCode == null) {
            throw new CustomException(INVALID_RELOCATION, "toTeamCode is required");
        }
        if (reason == null) {
            throw new CustomException(INVALID_RELOCATION, "relocationReason is required");
        }
        validateProjectId(tenantId, projectId);

        boolean byTeam = fromTeamCode != null;
        boolean byBeneficiary = !clientReferenceIds.isEmpty();
        if (byTeam == byBeneficiary) {
            throw new CustomException(INVALID_TEAM_SELECTOR,
                    "populate either fromTeamCode, or clientReferenceIds - exactly one of the two");
        }
        if (byTeam && toTeamCode.equals(fromTeamCode)) {
            throw new CustomException(INVALID_RELOCATION, "fromTeamCode and toTeamCode are the same");
        }

        // both ends of the move have to be real, live codes; one call covers them
        individualTeamCodeRepository.validateTeamCodesAssignable(tenantId,
                byTeam ? List.of(fromTeamCode, toTeamCode) : List.of(toTeamCode), requestInfo);

        Context context = new Context(tenantId, projectId, toTeamCode, reason, requestInfo,
                System.currentTimeMillis());

        if (byBeneficiary) {
            validateClientReferenceIdsExist(context, clientReferenceIds);
            assignByClientReferenceIds(context, clientReferenceIds);
        } else {
            List<String> ids = collectIdsForTeam(context, fromTeamCode);
            relocateByIds(context, ids);
        }

        return BeneficiaryTeamRelocationResult.builder()
                .fromTeamCode(fromTeamCode)
                .toTeamCode(toTeamCode)
                .matchedCount(context.matched)
                .relocatedCount(context.updated)
                .skippedCount(context.skipped)
                .build();
    }

    /**
     * Read phase. Pages the team predicate with a running offset, collecting ids only, and performs
     * no writes - so the result set cannot shift underneath it. Ids rather than rows because the
     * whole match set has to be resolved before the first publish, and ids are roughly 80 bytes
     * each against kilobytes for a beneficiary with its additionalDetails parsed.
     */
    private List<String> collectIdsForTeam(Context context, String fromTeamCode) {
        int pageSize = projectConfiguration.getBeneficiaryTeamRelocatePageSize();
        int maxRecords = projectConfiguration.getBeneficiaryTeamRelocateMaxRecords();
        List<String> ids = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<String> page;
            try {
                page = projectBeneficiaryRepository.findIdsByRegisteredByTeam(context.tenantId, fromTeamCode,
                        context.projectId, pageSize, offset);
            } catch (InvalidTenantIdException exception) {
                throw new CustomException("INVALID_TENANT_ID", exception.getMessage());
            }
            if (page.isEmpty()) {
                break;
            }
            ids.addAll(page);
            if (ids.size() > maxRecords) {
                throw new CustomException(RELOCATION_TOO_LARGE, "team " + fromTeamCode + " has more than "
                        + maxRecords + " beneficiaries; raise project.beneficiary.team.relocate.max.records "
                        + "or narrow the relocation with projectId");
            }
            if (page.size() < pageSize) {
                break;
            }
            offset += pageSize;
        }
        log.info("relocation read phase resolved {} beneficiaries on team {}", ids.size(), fromTeamCode);
        return ids;
    }

    /**
     * Write phase. Addressed by immutable id, so the team predicate is never re-queried and a row
     * the persister has already moved cannot cause a re-read or a spin.
     */
    private void relocateByIds(Context context, List<String> ids) {
        int batchSize = projectConfiguration.getBeneficiaryTeamSearchBatchSize();
        for (int from = 0; from < ids.size(); from += batchSize) {
            List<String> batch = ids.subList(from, Math.min(from + batchSize, ids.size()));
            List<ProjectBeneficiary> found;
            try {
                found = projectBeneficiaryRepository.findByIds(context.tenantId, batch);
            } catch (InvalidTenantIdException exception) {
                throw new CustomException("INVALID_TENANT_ID", exception.getMessage());
            }
            applyAndPublish(context, found);
        }
    }

    /**
     * Confirms every clientReferenceId resolves, in the same batches the write phase uses, and
     * fails the whole call if any does not.
     *
     * Deliberately a one column projection rather than a row fetch: this holds a set of id
     * strings the size of the request, not the beneficiaries themselves, so validating a very
     * large request costs about as much memory as receiving it did. The price is one extra pass
     * over the batches, which is what buys all-or-nothing - discovering a bad id during the write
     * phase would mean some beneficiaries had already been published.
     */
    private void validateClientReferenceIdsExist(Context context, List<String> clientReferenceIds) {
        int searchBatchSize = projectConfiguration.getBeneficiaryTeamSearchBatchSize();
        List<String> missing = new ArrayList<>();
        for (int from = 0; from < clientReferenceIds.size(); from += searchBatchSize) {
            List<String> batch = clientReferenceIds.subList(from,
                    Math.min(from + searchBatchSize, clientReferenceIds.size()));
            Set<String> found;
            try {
                found = projectBeneficiaryRepository.findExistingClientReferenceIds(context.tenantId, batch,
                        context.projectId);
            } catch (InvalidTenantIdException exception) {
                throw new CustomException("INVALID_TENANT_ID", exception.getMessage());
            }
            batch.stream().filter(id -> !found.contains(id)).forEach(missing::add);
        }
        if (!missing.isEmpty()) {
            throw new CustomException(PROJECT_BENEFICIARY_NOT_FOUND, missing.size()
                    + " clientReferenceIds do not exist"
                    + (context.projectId != null ? " in project " + context.projectId : "")
                    + ": " + describe(missing));
        }
    }

    /**
     * Keeps the error readable when someone submits a large batch of bad ids.
     */
    private String describe(List<String> missing) {
        int limit = 20;
        if (missing.size() <= limit) {
            return missing.toString();
        }
        return missing.subList(0, limit) + " and " + (missing.size() - limit) + " more";
    }

    /**
     * projectId is mandatory on both endpoints and has to name a real project.
     *
     * It is what bounds the write: without it a mode B assignment would reach every beneficiary
     * the user ever registered, including records from earlier campaigns under a different team,
     * and silently retag them.
     */
    private void validateProjectId(String tenantId, String projectId) {
        if (StringUtils.isBlank(projectId)) {
            throw new CustomException(INVALID_PROJECT_ID, "projectId is required");
        }
        List<String> existing;
        try {
            existing = projectRepository.validateIds(tenantId, new ArrayList<>(List.of(projectId)), "id");
        } catch (InvalidTenantIdException exception) {
            throw new CustomException("INVALID_TENANT_ID", exception.getMessage());
        }
        if (CollectionUtils.isEmpty(existing)) {
            throw new CustomException(INVALID_PROJECT_ID, "no project found for id " + projectId);
        }
    }

    /**
     * Mode A. The caller's list can be arbitrarily long, so it is looked up in
     * project.beneficiary.team.search.batch.size chunks and each chunk published in
     * project.beneficiary.team.kafka.batch.size chunks.
     */
    private void assignByClientReferenceIds(Context context, List<String> clientReferenceIds) {
        int searchBatchSize = projectConfiguration.getBeneficiaryTeamSearchBatchSize();
        for (int from = 0; from < clientReferenceIds.size(); from += searchBatchSize) {
            List<String> batch = clientReferenceIds.subList(from,
                    Math.min(from + searchBatchSize, clientReferenceIds.size()));
            List<ProjectBeneficiary> found;
            try {
                found = projectBeneficiaryRepository.findByClientReferenceIds(context.tenantId, batch,
                        context.projectId);
            } catch (InvalidTenantIdException exception) {
                throw new CustomException("INVALID_TENANT_ID", exception.getMessage());
            }
            log.info("resolved {} of {} clientReferenceIds in this batch", found.size(), batch.size());
            applyAndPublish(context, found);
        }
    }

    /**
     * Mode B. One user at a time, keyset paged, so memory stays flat no matter how many
     * registrations a user has.
     */
    private void assignByUsers(Context context, List<String> userUuids) {
        int pageSize = projectConfiguration.getBeneficiaryTeamPageSize();
        for (String userUuid : userUuids) {
            String afterId = null;
            int forThisUser = 0;
            while (true) {
                List<ProjectBeneficiary> page;
                try {
                    page = projectBeneficiaryRepository.findPageByCreatedBy(context.tenantId,
                            Collections.singletonList(userUuid), context.projectId, afterId, pageSize);
                } catch (InvalidTenantIdException exception) {
                    throw new CustomException("INVALID_TENANT_ID", exception.getMessage());
                }
                if (page.isEmpty()) {
                    break;
                }
                // the page is ordered by id, so the last id is the keyset cursor for the next one
                afterId = page.get(page.size() - 1).getId();
                forThisUser += page.size();
                applyAndPublish(context, page);
                if (page.size() < pageSize) {
                    break;
                }
            }
            log.info("attributed {} beneficiaries registered by user {} to team code {}", forThisUser, userUuid,
                    context.teamCode);
        }
    }

    /**
     * Mutates the batch, then publishes the changed beneficiaries and their history rows in
     * Kafka sized chunks. Beneficiaries already carrying the requested value are skipped
     * entirely - no republish, no history row - which is what keeps a re-run cheap.
     */
    private void applyAndPublish(Context context, List<ProjectBeneficiary> beneficiaries) {
        context.matched += beneficiaries.size();

        List<ProjectBeneficiary> changed = new ArrayList<>();
        List<BeneficiaryTeamMapping> history = new ArrayList<>();

        for (ProjectBeneficiary beneficiary : beneficiaries) {
            String current = AdditionalFieldsUtil.getFieldValue(beneficiary.getAdditionalFields(),
                    REGISTERED_BY_TEAM);
            if (Objects.equals(current, context.teamCode)) {
                context.skipped++;
                continue;
            }
            if (context.teamCode == null) {
                beneficiary.setAdditionalFields(
                        AdditionalFieldsUtil.removeField(beneficiary.getAdditionalFields(), REGISTERED_BY_TEAM));
            } else {
                beneficiary.setAdditionalFields(AdditionalFieldsUtil.upsertField(
                        beneficiary.getAdditionalFields(), PROJECT_BENEFICIARY_SCHEMA, REGISTERED_BY_TEAM,
                        context.teamCode));
            }
            beneficiary.setRowVersion(beneficiary.getRowVersion() == null ? 1 : beneficiary.getRowVersion() + 1);
            beneficiary.setAuditDetails(CommonUtils.getAuditDetailsForUpdate(beneficiary.getAuditDetails(),
                    context.requesterUuid()));
            changed.add(beneficiary);
            history.add(historyRow(context, beneficiary, current));
        }

        if (changed.isEmpty()) {
            return;
        }
        context.updated += changed.size();

        int kafkaBatchSize = projectConfiguration.getBeneficiaryTeamKafkaBatchSize();
        for (int from = 0; from < changed.size(); from += kafkaBatchSize) {
            int to = Math.min(from + kafkaBatchSize, changed.size());
            producer.push(context.tenantId, projectConfiguration.getUpdateProjectBeneficiaryTopic(),
                    new ArrayList<>(changed.subList(from, to)));
            producer.push(context.tenantId, projectConfiguration.getBeneficiaryTeamMappingTopic(),
                    new ArrayList<>(history.subList(from, to)));
        }
    }

    /**
     * relocationReason is null for an assignment and carries the operator's reason for a relocation.
     */
    private BeneficiaryTeamMapping historyRow(Context context, ProjectBeneficiary beneficiary, String previous) {
        return BeneficiaryTeamMapping.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(context.tenantId)
                .projectBeneficiaryId(beneficiary.getId())
                .projectBeneficiaryClientReferenceId(beneficiary.getClientReferenceId())
                .projectId(beneficiary.getProjectId())
                .teamCode(context.teamCode)
                .previousTeamCode(previous)
                .relocationReason(context.relocationReason)
                .auditDetails(AuditDetails.builder()
                        .createdBy(context.requesterUuid())
                        .createdTime(context.now)
                        .lastModifiedBy(context.requesterUuid())
                        .lastModifiedTime(context.now)
                        .build())
                .rowVersion(1)
                .isDeleted(Boolean.FALSE)
                .build();
    }

    /**
     * Usernames are resolved through the individual service; userUuids are already what
     * project_beneficiary.createdBy holds. An unknown username fails the whole call.
     */
    private List<String> resolveUsers(String tenantId, List<String> usernames, List<String> userUuids,
                                      RequestInfo requestInfo) {
        Set<String> resolved = new LinkedHashSet<>(userUuids);
        if (!usernames.isEmpty()) {
            Map<String, String> userUuidByUsername = individualTeamCodeRepository
                    .resolveUsernamesToUserUuids(tenantId, usernames, requestInfo);
            List<String> unknown = usernames.stream().filter(name -> !userUuidByUsername.containsKey(name)).toList();
            if (!unknown.isEmpty()) {
                throw new CustomException(USERNAME_NOT_FOUND, "no individual with a user account for " + unknown);
            }
            resolved.addAll(userUuidByUsername.values());
        }
        return new ArrayList<>(resolved);
    }

    private void validateUsersAreTeamMembers(List<String> userUuids, Set<String> teamMemberUserUuids,
                                             String teamCode) {
        List<String> notMembers = userUuids.stream().filter(uuid -> !teamMemberUserUuids.contains(uuid)).toList();
        if (!notMembers.isEmpty()) {
            throw new CustomException(USER_NOT_IN_TEAM,
                    "these users are not members of team code " + teamCode + ": " + notMembers);
        }
    }

    private List<String> normalise(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        return values.stream().map(StringUtils::trimToNull).filter(Objects::nonNull).distinct().toList();
    }

    /**
     * Carries the per-call constants plus the running counts, so the batch helpers do not need
     * six parameters each.
     */
    private static final class Context {
        private final String tenantId;
        private final String projectId;
        private final String teamCode;
        private final String relocationReason;
        private final RequestInfo requestInfo;
        private final long now;
        private int matched;
        private int updated;
        private int skipped;

        private Context(String tenantId, String projectId, String teamCode, String relocationReason,
                        RequestInfo requestInfo, long now) {
            this.tenantId = tenantId;
            this.projectId = projectId;
            this.teamCode = teamCode;
            this.relocationReason = relocationReason;
            this.requestInfo = requestInfo;
            this.now = now;
        }

        private String requesterUuid() {
            return requestInfo.getUserInfo().getUuid();
        }
    }
}
