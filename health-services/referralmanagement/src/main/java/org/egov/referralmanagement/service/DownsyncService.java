package org.egov.referralmanagement.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.core.Field;
import org.egov.common.models.core.Pagination;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkResponse;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberBulkResponse;
import org.egov.common.models.household.HouseholdMemberSearch;
import org.egov.common.models.household.HouseholdMemberSearchRequest;
import org.egov.common.models.household.HouseholdSearch;
import org.egov.common.models.household.HouseholdSearchRequest;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkResponse;
import org.egov.common.models.individual.IndividualSearch;
import org.egov.common.models.individual.IndividualSearchRequest;
import org.egov.common.models.project.BeneficiaryBulkResponse;
import org.egov.common.models.project.BeneficiarySearchRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.ProjectBeneficiarySearch;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskBulkResponse;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.models.project.TaskSearchRequest;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralSearch;
import org.egov.common.models.referralmanagement.ReferralSearchRequest;
import org.egov.common.models.referralmanagement.beneficiarydownsync.BeneficiaryInfo;
import org.egov.common.models.referralmanagement.beneficiarydownsync.Downsync;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncCriteria;
import org.egov.common.models.referralmanagement.beneficiarydownsync.DownsyncRequest;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralSearch;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralSearchRequest;
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearch;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectSearchRequest;
import org.egov.common.models.service.ServiceCriteria;
import org.egov.common.models.service.ServiceResponse;
import org.egov.common.models.service.ServiceSearchRequest;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.referralmanagement.Constants;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.referralmanagement.repository.rowmapper.BeneficiaryInfoRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;
import static org.egov.referralmanagement.Constants.HOUSEHOLD;

@Service
@Slf4j
public class DownsyncService {

    private static final String BENEFICIARY_REFERRED_STATUS = "BeneficiaryReferred";

    private ServiceRequestClient restClient;

    private ReferralManagementConfiguration configs;

    private NamedParameterJdbcTemplate jdbcTemplate;

    private SideEffectService sideEffectService;

    private ReferralManagementService referralService;

    private HFReferralService hfReferralService;

    private MasterDataService masterDataService;

    private final BeneficiaryInfoRowMapper beneficiaryInfoRowMapper;

    private final MultiStateInstanceUtil multiStateInstanceUtil;

    @Autowired
    public DownsyncService( ServiceRequestClient serviceRequestClient,
                            ReferralManagementConfiguration referralManagementConfiguration,
                            NamedParameterJdbcTemplate jdbcTemplate,
                            SideEffectService sideEffectService,
                            ReferralManagementService referralService,
                            HFReferralService hfReferralService,
                            MasterDataService masterDataService,
                            BeneficiaryInfoRowMapper beneficiaryInfoRowMapper,
                            MultiStateInstanceUtil multiStateInstanceUtil) {

        this.restClient = serviceRequestClient;
        this.configs = referralManagementConfiguration;
        this.jdbcTemplate = jdbcTemplate;
        this.sideEffectService = sideEffectService;
        this.referralService = referralService;
        this.hfReferralService = hfReferralService;
        this.masterDataService = masterDataService;
        this.beneficiaryInfoRowMapper = beneficiaryInfoRowMapper;
        this.multiStateInstanceUtil = multiStateInstanceUtil;

    }

    /**
     *
     * @param downsyncRequest
     * @return Downsync
     */
    public Downsync prepareDownsyncData(DownsyncRequest downsyncRequest) throws InvalidTenantIdException {

        Downsync downsync = new Downsync();
        DownsyncCriteria downsyncCriteria = downsyncRequest.getDownsyncCriteria();

        List<String> individualClientRefIds = null;
        List<String> beneficiaryClientRefIds = null;
        List<String> taskClientRefIds = null;
        List<String> householdClientRefIds = null;
        List<HFReferral> hfReferrals = Collections.emptyList();


        downsync.setDownsyncCriteria(downsyncCriteria);
        boolean isSyncTimeAvailable = null != downsyncCriteria.getLastSyncedTime();

        //Project project = getProjectType(downsyncRequest);
        LinkedHashMap<String, Object> projectType = masterDataService.getProjectType(downsyncRequest);

        /* search household */
        householdClientRefIds = searchHouseholds(downsyncRequest, downsync);

        /* search household member using household client reference ids */
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(householdClientRefIds)) {
            individualClientRefIds = searchMembers(downsyncRequest, downsync, householdClientRefIds);
        }

        /* search individuals using individual client reference ids */
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(individualClientRefIds) ) {
            searchIndividuals(downsyncRequest, downsync, individualClientRefIds);
        }

        /* search beneficiary using individual ids OR household ids */

        String beneficiaryType = (String) projectType.get("beneficiaryType");

        beneficiaryClientRefIds = individualClientRefIds;

        if(HOUSEHOLD.equalsIgnoreCase(beneficiaryType))
            beneficiaryClientRefIds = householdClientRefIds;

        //fetch beneficiary in the db
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(beneficiaryClientRefIds)) {
            beneficiaryClientRefIds = searchBeneficiaries(downsyncRequest, downsync, beneficiaryClientRefIds);
        }

        /* search tasks using beneficiary uuids */
        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(beneficiaryClientRefIds)) {

            taskClientRefIds = searchTasks(downsyncRequest, downsync, beneficiaryClientRefIds, projectType);

            /* hf referral search */
            hfReferrals = searchHFReferrals(downsyncRequest, downsync, beneficiaryClientRefIds);

            /* ref search */
            referralSearch(downsyncRequest, downsync, beneficiaryClientRefIds);
        }


        if (isSyncTimeAvailable || !CollectionUtils.isEmpty(taskClientRefIds)) {
            searchSideEffect(downsyncRequest, downsync, taskClientRefIds);
        }

        /* search service request services when enabled based on individual client reference ids
        *  and household client reference ids as reference ids for services */
        if (configs.getServiceRequestDownsyncEnabled()) {
            searchServices(downsyncRequest, downsync, individualClientRefIds, householdClientRefIds);
        }

        constructBeneficiaryInfo(downsync, hfReferrals);

        return downsync;
    }


    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @return household client reference ids list
     */
    private List<String> searchHouseholds(DownsyncRequest downsyncRequest, Downsync downsync) {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();

        StringBuilder householdUrl = new StringBuilder(configs.getHouseholdHost())
                .append(configs.getHouseholdSearchUrl());
        appendUrlParams(householdUrl, criteria, null, null, true);

        HouseholdSearch householdSearch = HouseholdSearch.builder()
                .localityCode(criteria.getLocality())
                .build();

        HouseholdSearchRequest searchRequest = HouseholdSearchRequest.builder()
                .household(householdSearch)
                .requestInfo(requestInfo)
                .build();

        HouseholdBulkResponse res = restClient.fetchResult(householdUrl, searchRequest, HouseholdBulkResponse.class);
        List<Household> households = res.getHouseholds();
        downsync.setHouseholds(households);
        downsync.getDownsyncCriteria().setTotalCount(res.getTotalCount());

        if(CollectionUtils.isEmpty(households))
            return Collections.emptyList();

        return households.stream().map(Household::getClientReferenceId).collect(Collectors.toList());
    }

    /** Fetches individuals based on individual client reference ids and sets in downsync object
     *
     * @param downsyncRequest
     * @param downsync
     * @param individualClientRefIds individual client reference ids
     * @return individual ClientReferenceIds
     */
    private List<String> searchIndividuals(DownsyncRequest downsyncRequest, Downsync downsync,
                                           List<String> individualClientRefIds) throws InvalidTenantIdException {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        String tenantId = criteria.getTenantId();

        List<String> individualIds = getPrimaryIds(tenantId, individualClientRefIds, "clientReferenceId", "INDIVIDUAL", criteria.getLastSyncedTime());

        if (CollectionUtils.isEmpty(individualIds))
            return Collections.emptyList();

        /* builds url for individual search */
        StringBuilder url = new StringBuilder(configs.getIndividualHost())
                .append(configs.getIndividualSearchUrl());

        List<Individual> allIndividuals = new ArrayList<>();

        /* get batch size to fetch individuals from environment */
        int batchSize = configs.getIndividualSearchBatchSize();

        appendUrlParams(url, criteria, 0, batchSize, true);

        /* fetches the data in the batches of batch size */
        for (int i = 0; i < individualIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, individualIds);

            IndividualSearch individualSearch = IndividualSearch.builder()
                    .id(batch)
                    .build();

            IndividualSearchRequest searchRequest = IndividualSearchRequest.builder()
                    .individual(individualSearch)
                    .requestInfo(requestInfo)
                    .build();

            List<Individual> individuals = restClient.fetchResult(url, searchRequest, IndividualBulkResponse.class).getIndividual();
            allIndividuals.addAll(individuals);
        }
        downsync.setIndividuals(allIndividuals);

        return allIndividuals.stream().map(Individual::getClientReferenceId).collect(Collectors.toList());
    }

    /** Builds and sets the BeneficiaryInfo list on the downsync object by joining household members
     * with their individual, household, task status and referral data.
     *
     * @param downsync    downsync object holding all fetched entities
     * @param hfReferrals HF referrals used to determine BeneficiaryReferred status
     */
    private void constructBeneficiaryInfo(Downsync downsync, List<HFReferral> hfReferrals) {
        if (CollectionUtils.isEmpty(downsync.getHouseholdMembers()) || CollectionUtils.isEmpty(downsync.getIndividuals())) {
            downsync.setBeneficiaryInfo(Collections.emptyList());
            return;
        }

        Map<String, Individual> individualByClientReferenceId = downsync.getIndividuals().stream()
                .filter(individual -> individual.getClientReferenceId() != null)
                .collect(Collectors.toMap(
                        Individual::getClientReferenceId,
                        individual -> individual,
                        (existing, replacement) -> replacement
                ));

        Map<String, Household> householdByClientReferenceId = CollectionUtils.isEmpty(downsync.getHouseholds())
                ? Collections.emptyMap()
                : downsync.getHouseholds().stream()
                .filter(household -> household.getClientReferenceId() != null)
                .collect(Collectors.toMap(
                        Household::getClientReferenceId,
                        household -> household,
                        (existing, replacement) -> replacement
                ));

        Map<String, ProjectBeneficiary> projectBeneficiaryByKey =
                buildProjectBeneficiaryByKey(downsync.getProjectBeneficiaries());
        Map<String, LatestTaskStatus> taskStatusByKey = buildTaskStatusByKey(downsync.getTasks(), projectBeneficiaryByKey);
        Map<String, LatestReferral> referredBeneficiaryKeys = buildReferredBeneficiaryKeys(hfReferrals, projectBeneficiaryByKey);

        List<BeneficiaryInfo> beneficiaryInfo = downsync.getHouseholdMembers().stream()
                .map(householdMember -> {
                    Individual individual = individualByClientReferenceId.get(householdMember.getIndividualClientReferenceId());
                    Household household = householdByClientReferenceId.get(householdMember.getHouseholdClientReferenceId());
                    String taskStatus = individual == null ? null
                            : resolveTaskStatus(individual, householdMember, household,
                                    taskStatusByKey, referredBeneficiaryKeys, projectBeneficiaryByKey);
                    return beneficiaryInfoRowMapper.toBeneficiaryInfo(householdMember, individual, household, taskStatus);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        downsync.setBeneficiaryInfo(beneficiaryInfo);
    }

    /** Builds a lookup map of ProjectBeneficiary indexed by all 4 of its identifiers
     * (id, clientReferenceId, beneficiaryId, beneficiaryClientReferenceId) so that tasks
     * and referrals can be resolved regardless of which identifier a given service used.
     *
     * @param projectBeneficiaries list of project beneficiaries to index
     * @return map from any beneficiary identifier to its ProjectBeneficiary
     */
    private Map<String, ProjectBeneficiary> buildProjectBeneficiaryByKey(List<ProjectBeneficiary> projectBeneficiaries) {
        Map<String, ProjectBeneficiary> projectBeneficiaryByKey = new HashMap<>();
        if (CollectionUtils.isEmpty(projectBeneficiaries)) {
            return projectBeneficiaryByKey;
        }

        projectBeneficiaries.forEach(pb ->
                Stream.of(pb.getId(), pb.getClientReferenceId(), pb.getBeneficiaryId(), pb.getBeneficiaryClientReferenceId())
                        .filter(Objects::nonNull)
                        .forEach(key -> projectBeneficiaryByKey.putIfAbsent(key, pb)));
        return projectBeneficiaryByKey;
    }

    /** Builds a lookup map from every beneficiary identifier to its latest task status.
     * Each task is also indexed by the underlying individual/household identifiers via
     * the ProjectBeneficiary lookup so that resolution works by any candidate key.
     * When a beneficiary has tasks across multiple cycles or doses, merge retains only
     * the most recently created task per key.
     *
     * @param tasks                   list of tasks to index
     * @param projectBeneficiaryByKey lookup map used to expand task keys to individual/household IDs
     * @return map from any beneficiary identifier to its latest LatestTaskStatus
     */
    private Map<String, LatestTaskStatus> buildTaskStatusByKey(List<Task> tasks,
                                                               Map<String, ProjectBeneficiary> projectBeneficiaryByKey) {
        Map<String, LatestTaskStatus> taskStatusByKey = new HashMap<>();
        if (CollectionUtils.isEmpty(tasks)) {
            return taskStatusByKey;
        }

        tasks.stream()
                .filter(task -> task.getStatus() != null)
                .forEach(task -> {
                    LatestTaskStatus taskStatus = LatestTaskStatus.from(task);
                    String projectBeneficiaryClientReferenceId = task.getProjectBeneficiaryClientReferenceId();
                    if (projectBeneficiaryClientReferenceId != null) {
                        taskStatusByKey.merge(projectBeneficiaryClientReferenceId, taskStatus, LatestTaskStatus::latest);
                    }

                    ProjectBeneficiary projectBeneficiary =
                            projectBeneficiaryByKey.get(projectBeneficiaryClientReferenceId);
                    if (projectBeneficiary != null) {
                        Stream.of(projectBeneficiary.getId(), projectBeneficiary.getClientReferenceId(),
                                  projectBeneficiary.getBeneficiaryId(), projectBeneficiary.getBeneficiaryClientReferenceId())
                                .filter(Objects::nonNull)
                                .forEach(key -> taskStatusByKey.merge(key, taskStatus, LatestTaskStatus::latest));
                    }
                });
        return taskStatusByKey;
    }

    /** Builds a lookup map from every beneficiary identifier to its latest non-deleted HFReferral.
     * Since HFReferrals store a beneficiaryId that may be any ProjectBeneficiary identifier,
     * the map is expanded to all PB keys so referrals can be found during candidate-key resolution.
     *
     * @param hfReferrals             list of HF referrals to index
     * @param projectBeneficiaryByKey lookup map used to expand referral keys to individual/household IDs
     * @return map from any beneficiary identifier to its latest LatestReferral
     */
    private Map<String, LatestReferral> buildReferredBeneficiaryKeys(List<HFReferral> hfReferrals,
                                                                    Map<String, ProjectBeneficiary> projectBeneficiaryByKey) {
        if (CollectionUtils.isEmpty(hfReferrals)) {
            return Collections.emptyMap();
        }

        Map<String, LatestReferral> referredBeneficiaryKeys = new HashMap<>();
        hfReferrals.stream()
                .filter(hfReferral -> !Boolean.TRUE.equals(hfReferral.getIsDeleted()))
                .forEach(hfReferral -> {
                    String key = hfReferral.getBeneficiaryId();
                    if (key != null) referredBeneficiaryKeys.merge(key, LatestReferral.from(hfReferral), LatestReferral::latest);
                });

        // Snapshot before expanding so newly added keys don't trigger further expansion.
        new HashMap<>(referredBeneficiaryKeys).forEach((beneficiaryId, referral) -> {
            ProjectBeneficiary projectBeneficiary = projectBeneficiaryByKey.get(beneficiaryId);
            if (projectBeneficiary != null) {
                Stream.of(projectBeneficiary.getId(), projectBeneficiary.getClientReferenceId(),
                          projectBeneficiary.getBeneficiaryId(), projectBeneficiary.getBeneficiaryClientReferenceId())
                        .filter(Objects::nonNull)
                        .forEach(key -> referredBeneficiaryKeys.merge(key, referral, LatestReferral::latest));
            }
        });

        return referredBeneficiaryKeys;
    }

    /** Resolves the display taskStatus string for a beneficiary by combining task history
     * and referral state across all possible beneficiary identifier keys.
     * Returns "BeneficiaryReferred" when an active referral matches the current cycle/dose,
     * or when a referral exists and no tasks have been recorded yet.
     *
     * @param individual              the individual to resolve status for
     * @param householdMember         the household member record
     * @param household               the household; may be null
     * @param taskStatusByKey         lookup map of latest task status by beneficiary identifier
     * @param referredBeneficiaryKeys lookup map of latest referral by beneficiary identifier
     * @param projectBeneficiaryByKey lookup map used to expand candidate keys
     * @return resolved status string, or null if no tasks and no referrals exist
     */
    private String resolveTaskStatus(Individual individual,
                                     HouseholdMember householdMember,
                                     Household household,
                                     Map<String, LatestTaskStatus> taskStatusByKey,
                                     Map<String, LatestReferral> referredBeneficiaryKeys,
                                     Map<String, ProjectBeneficiary> projectBeneficiaryByKey) {
        List<String> candidateKeys = getBeneficiaryCandidateKeys(individual, householdMember, household,
                projectBeneficiaryByKey);

        LatestTaskStatus latestTaskStatus = candidateKeys.stream()
                .map(taskStatusByKey::get)
                .filter(Objects::nonNull)
                .max(LatestTaskStatus::compare)
                .orElse(null);

        boolean hasCurrentCycleReferral = candidateKeys.stream()
                .map(referredBeneficiaryKeys::get)
                .filter(Objects::nonNull)
                .anyMatch(referral -> latestTaskStatus == null || referral.matches(latestTaskStatus));
        if (hasCurrentCycleReferral) {
            return BENEFICIARY_REFERRED_STATUS;
        }

        return latestTaskStatus == null ? null : latestTaskStatus.status();
    }

    /** Collects all possible lookup keys for a beneficiary — individual IDs, household IDs,
     * and all ProjectBeneficiary identifiers — used to look up task status and referrals
     * from their respective maps.
     *
     * @param individual              source individual
     * @param householdMember         source household member
     * @param household               source household; may be null
     * @param projectBeneficiaryByKey used to expand initial keys to all ProjectBeneficiary identifiers
     * @return deduplicated list of all identifier keys for this beneficiary
     */
    private List<String> getBeneficiaryCandidateKeys(Individual individual,
                                                     HouseholdMember householdMember,
                                                     Household household,
                                                     Map<String, ProjectBeneficiary> projectBeneficiaryByKey) {
        List<String> candidateKeys = new ArrayList<>();
        Stream.of(individual.getId(), individual.getClientReferenceId(),
                  householdMember.getIndividualId(), householdMember.getIndividualClientReferenceId(),
                  householdMember.getHouseholdId(), householdMember.getHouseholdClientReferenceId())
                .filter(Objects::nonNull)
                .forEach(candidateKeys::add);

        if (household != null) {
            Stream.of(household.getId(), household.getClientReferenceId())
                    .filter(Objects::nonNull)
                    .forEach(candidateKeys::add);
        }

        // Snapshot before expanding so keys added during this loop don't cause further iterations.
        new ArrayList<>(candidateKeys).forEach(candidateKey -> {
            ProjectBeneficiary projectBeneficiary = projectBeneficiaryByKey.get(candidateKey);
            if (projectBeneficiary != null) {
                Stream.of(projectBeneficiary.getId(), projectBeneficiary.getClientReferenceId(),
                          projectBeneficiary.getBeneficiaryId(), projectBeneficiary.getBeneficiaryClientReferenceId())
                        .filter(Objects::nonNull)
                        .forEach(candidateKeys::add);
            }
        });

        return candidateKeys.stream().distinct().collect(Collectors.toList());
    }

    /** Holds the status and timing metadata for the most recent task in a given beneficiary scope. */
    private static class LatestTaskStatus {
        private final String status;
        private final Long resetTime;
        private final String cycleId;
        private final String dose;
        private final Long auditTime;

        private LatestTaskStatus(String status, Long resetTime, String cycleId, String dose, Long auditTime) {
            this.status = status;
            this.resetTime = resetTime;
            this.cycleId = cycleId;
            this.dose = dose;
            this.auditTime = auditTime;
        }

        /** Creates a LatestTaskStatus snapshot from a Task.
         *
         * @param task the task to extract status, cycle, dose and timing from
         * @return new LatestTaskStatus
         */
        private static LatestTaskStatus from(Task task) {
            Long auditTime = getCreatedTime(task.getAuditDetails());
            String cycleId = getAdditionalFieldValue(task.getAdditionalFields(), "cycleId");
            String dose = getAdditionalFieldValue(task.getAdditionalFields(), "dose");

            // resetTime = auditTime = createdTime: each task creation represents a delivery event,
            // and createdTime is the boundary used in time-based referral matching.
            return new LatestTaskStatus(
                    task.getStatus().toString(),
                    auditTime,
                    cycleId,
                    dose,
                    auditTime
            );
        }

        /** Returns the more recent of two instances by auditTime.
         *
         * @param existing    current value
         * @param replacement incoming value
         * @return the instance with the higher auditTime
         */
        private static LatestTaskStatus latest(LatestTaskStatus existing, LatestTaskStatus replacement) {
            return compare(replacement, existing) >= 0 ? replacement : existing;
        }

        private static int compare(LatestTaskStatus first, LatestTaskStatus second) {
            return Long.compare(first.auditTime(), second.auditTime());
        }

        private String status() {
            return status;
        }

        private Long resetTime() {
            return resetTime == null ? 0L : resetTime;
        }

        private Long auditTime() {
            return auditTime == null ? 0L : auditTime;
        }
    }

    /** Holds the timing and cycle/dose metadata for the most recent HF referral in a given beneficiary scope. */
    private static class LatestReferral {
        private final Long referralTime;
        private final String cycleId;
        private final String dose;

        private LatestReferral(Long referralTime, String cycleId, String dose) {
            this.referralTime = referralTime;
            this.cycleId = cycleId;
            this.dose = dose;
        }

        /** Creates a LatestReferral snapshot from an HFReferral.
         *
         * @param hfReferral the referral to extract timing, cycle and dose from
         * @return new LatestReferral
         */
        private static LatestReferral from(HFReferral hfReferral) {
            AuditDetails auditDetails = hfReferral.getAuditDetails();
            return new LatestReferral(
                    getAuditTime(auditDetails),
                    getAdditionalFieldValue(hfReferral.getAdditionalFields(), "cycleId"),
                    getAdditionalFieldValue(hfReferral.getAdditionalFields(), "dose")
            );
        }

        /** Returns the more recent of two instances by referralTime.
         *
         * @param existing    current value
         * @param replacement incoming value
         * @return the instance with the higher referralTime
         */
        private static LatestReferral latest(LatestReferral existing, LatestReferral replacement) {
            return replacement.referralTime() >= existing.referralTime() ? replacement : existing;
        }

        /** Returns true when this referral should override the given task status.
         * Matches by cycle+dose when either is present on the referral (null = wildcard).
         * Falls back to time-based matching when neither cycle nor dose is recorded.
         *
         * @param taskStatus the task status to compare against
         * @return true if this referral supersedes the task status
         */
        private boolean matches(LatestTaskStatus taskStatus) {
            boolean hasReferralCycleDose = cycleId != null || dose != null;
            if (hasReferralCycleDose) {
                return isSameValue(cycleId, taskStatus.cycleId) && isSameValue(dose, taskStatus.dose);
            }
            return referralTime() >= taskStatus.resetTime();
        }

        private Long referralTime() {
            return referralTime == null ? 0L : referralTime;
        }
    }

    /** Looks up a value from AdditionalFields by key using case-insensitive matching.
     *
     * @param additionalFields the fields to search; may be null
     * @param key              the field key to find
     * @return the value for the first matching key, or null if not found
     */
    private static String getAdditionalFieldValue(AdditionalFields additionalFields, String key) {
        if (additionalFields == null || CollectionUtils.isEmpty(additionalFields.getFields())) {
            return null;
        }

        return additionalFields.getFields().stream()
                .filter(field -> key.equalsIgnoreCase(field.getKey()))
                .map(Field::getValue)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    /** Returns true when first equals second, treating null first as a wildcard that matches any second.
     *
     * @param first  the referral field; null means match any value
     * @param second the task field to compare against
     * @return true if first is null or equal to second
     */
    private static boolean isSameValue(String first, String second) {
        return first == null || first.equals(second);
    }

    /** Returns the most recent audit timestamp, preferring lastModifiedTime over createdTime.
     *
     * @param auditDetails audit details to read from; may be null
     * @return best available audit timestamp, or 0 if none found
     */
    private static Long getAuditTime(AuditDetails auditDetails) {
        if (auditDetails == null) {
            return 0L;
        }
        return firstTime(auditDetails.getLastModifiedTime(), auditDetails.getCreatedTime(), 0L);
    }

    /** Returns the creation timestamp from audit details, falling back to lastModifiedTime if absent.
     *
     * @param auditDetails audit details to read from; may be null
     * @return best available creation timestamp, or 0 if none found
     */
    private static Long getCreatedTime(AuditDetails auditDetails) {
        if (auditDetails == null) {
            return 0L;
        }
        return firstTime(auditDetails.getCreatedTime(), auditDetails.getLastModifiedTime(), 0L);
    }

    /** Returns the first time value that is non-null and positive from the ordered candidates, or 0.
     *
     * @param times ordered candidates to try
     * @return first valid timestamp, or 0 if none found
     */
    private static Long firstTime(Long... times) {
        for (Long time : times) {
            if (time != null && time > 0L) {
                return time;
            }
        }
        return 0L;
    }

    /** Fetches service request services in batch of configured size based on household and individual
     * client reference ids and sets in downsync object
     *
     * @param downsyncRequest
     * @param downsync
     * @param individualIds individual client reference ids
     * @param householdIds household client reference ids
     */
    private void searchServices(DownsyncRequest downsyncRequest, Downsync downsync,
                                           List<String> individualIds, List<String> householdIds) {
        if (CollectionUtils.isEmpty(householdIds) && CollectionUtils.isEmpty(individualIds)) {
            return;
        }

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();

        /* builds url for service request service search */
        StringBuilder url = new StringBuilder(configs.getServiceRequestHost())
                .append(configs.getServiceRequestServiceSearchUrl());

        List<String> referenceIds = new ArrayList<>();
        if (!CollectionUtils.isEmpty(householdIds)) referenceIds.addAll(householdIds);
        if (!CollectionUtils.isEmpty(individualIds)) referenceIds.addAll(individualIds);

        List<org.egov.common.models.service.Service> allServices = new ArrayList<>();

        /* get batch size to fetch services from environment */
        int batchSize = configs.getServiceRequestSearchBatchSize();

        /* fetches the data in the batches of batch size */
        for (int i = 0; i < referenceIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, referenceIds);

            ServiceCriteria serviceCriteria = ServiceCriteria.builder()
                    .tenantId(criteria.getTenantId())
                    .referenceIds(batch)
                    .build();

            Pagination pagination = Pagination.builder()
                    .offset(0)
                    .limit(batch.size())
                    .build();

            ServiceSearchRequest searchRequest = ServiceSearchRequest.builder()
                    .serviceCriteria(serviceCriteria)
                    .pagination(pagination)
                    .requestInfo(requestInfo)
                    .build();

            List<org.egov.common.models.service.Service> services =
                    restClient.fetchResult(url, searchRequest, ServiceResponse.class).getServices();

            if (services != null) {
                allServices.addAll(services);
            }
        }

        downsync.setServices(allServices);
    }

    /** Searches for household members of the given household client reference ids
     * updating in the downsync object
     *
     * @param downsyncRequest
     * @param householdClientReferenceIds
     * @return household member's individual client reference ids list
     */
    private List<String> searchMembers(DownsyncRequest downsyncRequest, Downsync downsync,
                                                      List<String> householdClientReferenceIds) throws InvalidTenantIdException {

        Long lastChangedSince = downsyncRequest.getDownsyncCriteria().getLastSyncedTime();
        String tenantId = downsyncRequest.getDownsyncCriteria().getTenantId();

        List<String> memberIds = getPrimaryIds(tenantId, householdClientReferenceIds, "householdClientReferenceId","HOUSEHOLD_MEMBER",lastChangedSince);

        if (CollectionUtils.isEmpty(memberIds))
            return Collections.emptyList();

        /* builds url for household member search */
        StringBuilder memberUrl = new StringBuilder(configs.getHouseholdHost())
                .append(configs.getHouseholdMemberSearchUrl());

        List<HouseholdMember> allMembers = new ArrayList<>();

        /* get batch size to fetch household members from environment */
        int batchSize = configs.getHouseholdMemberSearchBatchSize();

        appendUrlParams(memberUrl, downsyncRequest.getDownsyncCriteria(), 0, batchSize, false);

        /* fetches the data in the batches of batch size */
        for (int i = 0; i < memberIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, memberIds);

            HouseholdMemberSearch memberSearch = HouseholdMemberSearch.builder()
                    .id(batch)
                    .build();

            HouseholdMemberSearchRequest searchRequest = HouseholdMemberSearchRequest.builder()
                    .householdMemberSearch(memberSearch)
                    .requestInfo(downsyncRequest.getRequestInfo())
                    .build();

            List<HouseholdMember> members = restClient.fetchResult(memberUrl, searchRequest, HouseholdMemberBulkResponse.class).getHouseholdMembers();
            allMembers.addAll(members);
        }
        downsync.setHouseholdMembers(allMembers);

        return allMembers.stream().map(HouseholdMember::getIndividualClientReferenceId).collect(Collectors.toList());
    }

    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param beneficiaryClientRefIds
     * @return clientreferenceid of beneficiary object
     */
    private List<String> searchBeneficiaries(DownsyncRequest downsyncRequest, Downsync downsync,
                                             List<String> beneficiaryClientRefIds) throws InvalidTenantIdException {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        Long lastChangedSince =criteria.getLastSyncedTime();
        String tenantId = criteria.getTenantId();

        List<String> beneficiaryIds = getPrimaryIds(
                tenantId,
                beneficiaryClientRefIds,
                "beneficiaryclientreferenceid",
                "PROJECT_BENEFICIARY",
                lastChangedSince
        );

        if(CollectionUtils.isEmpty(beneficiaryIds))
            return Collections.emptyList();

        StringBuilder url = new StringBuilder(configs.getProjectHost())
                .append(configs.getProjectBeneficiarySearchUrl());

        List<ProjectBeneficiary> allBeneficiaries = new ArrayList<>();

        /* get batch size to fetch project beneficiaries from environment */
        int batchSize = configs.getProjectBeneficiarySearchBatchSize();

        appendUrlParams(url, criteria, 0, batchSize, false);

        /* fetches the data in the batches of batch size */
        for (int i = 0; i < beneficiaryIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, beneficiaryIds);

            ProjectBeneficiarySearch search = ProjectBeneficiarySearch.builder()
                    .id(batch)
                    .projectId(Collections.singletonList(downsyncRequest.getDownsyncCriteria().getProjectId()))
                    .build();

            BeneficiarySearchRequest searchRequest = BeneficiarySearchRequest.builder()
                    .projectBeneficiary(search)
                    .requestInfo(requestInfo)
                    .build();

            List<ProjectBeneficiary> beneficiaries = restClient.fetchResult(url, searchRequest, BeneficiaryBulkResponse.class).getProjectBeneficiaries();
            allBeneficiaries.addAll(beneficiaries);
        }
        downsync.setProjectBeneficiaries(allBeneficiaries);

        return allBeneficiaries.stream().map(ProjectBeneficiary::getClientReferenceId).collect(Collectors.toList());
    }



    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param beneficiaryClientRefIds
     * @param projectType
     * @return
     */
    private List<String> searchTasks(DownsyncRequest downsyncRequest, Downsync downsync,
                                     List<String> beneficiaryClientRefIds, LinkedHashMap<String, Object> projectType) throws InvalidTenantIdException {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        String tenantId = criteria.getTenantId();

        List<String> taskIds = getPrimaryIds(tenantId, beneficiaryClientRefIds, "projectBeneficiaryClientReferenceId", "PROJECT_TASK",
                criteria.getLastSyncedTime());

        if(CollectionUtils.isEmpty(taskIds))
            return Collections.emptyList();

        StringBuilder url = new StringBuilder(configs.getProjectHost())
                 .append(configs.getProjectTaskSearchUrl());

        List<Task> allTasks = new ArrayList<>();

        /* get batch size to fetch project tasks from environment */
        int batchSize = configs.getProjectTaskSearchBatchSize();

        appendUrlParams(url, criteria, 0, batchSize, false);

        /* fetches the data in the batches of batch size */
        for (int i = 0; i < taskIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, taskIds);

            TaskSearch search = TaskSearch.builder()
                    .id(batch)
                    .projectId(Collections.singletonList(downsyncRequest.getDownsyncCriteria().getProjectId()))
                    .build();

            TaskSearchRequest searchRequest = TaskSearchRequest.builder()
                    .task(search)
                    .requestInfo(requestInfo)
                    .build();

            List<Task> tasks = restClient.fetchResult(url, searchRequest, TaskBulkResponse.class).getTasks();
            allTasks.addAll(tasks);
        }
        downsync.setTasks(allTasks);

        return allTasks.stream().map(Task::getClientReferenceId).collect(Collectors.toList());
    }

    /**
     *
     * @param downsyncRequest
     * @param downsync
     * @param taskClientRefIds
     */
    private void searchSideEffect(DownsyncRequest downsyncRequest, Downsync downsync,
                                  List<String> taskClientRefIds) throws InvalidTenantIdException {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        String tenantId = criteria.getTenantId();

        /* FIXME SHOULD BE REMOVED AND TASK SEARCH SHOULD BE enhanced with list of client-ref-beneficiary ids*/
        List<String> SEIds = getPrimaryIds(tenantId, taskClientRefIds, "taskClientReferenceId", "SIDE_EFFECT", criteria.getLastSyncedTime());

        if(CollectionUtils.isEmpty(SEIds))
            return;

        List<SideEffect> allSideEffects = new ArrayList<>();

        /* get batch size to fetch side effects from environment */
        int batchSize = configs.getSideEffectSearchBatchSize();

        /* fetches the data in the batches of batch size */
        for (int i = 0; i < SEIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, SEIds);

            SideEffectSearch search = SideEffectSearch.builder()
                    .id(batch)
                    .build();

            SideEffectSearchRequest effectSearchRequest = SideEffectSearchRequest.builder()
                    .sideEffect(search)
                    .requestInfo(requestInfo)
                    .build();

            List<SideEffect> effects = sideEffectService.search(
                    effectSearchRequest,
                    batchSize,
                    0,
                    criteria.getTenantId(),
                    criteria.getLastSyncedTime(),
                    criteria.getIncludeDeleted()
            ).getResponse();
            allSideEffects.addAll(effects);
        }

        downsync.setSideEffects(allSideEffects);
    }

    /** Fetches all Referrals for the given beneficiary client reference ids using pagination
     * and sets them on the downsync object.
     *
     * @param downsyncRequest         downsync request containing criteria and requestInfo
     * @param downsync                downsync object to populate with referrals
     * @param beneficiaryClientRefIds beneficiary client reference ids to search by
     */
    private void referralSearch(DownsyncRequest downsyncRequest, Downsync downsync,
                                List<String> beneficiaryClientRefIds) throws InvalidTenantIdException {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();

        if(CollectionUtils.isEmpty(beneficiaryClientRefIds)) {
            return;
        }

        List<Referral> allReferrals = new ArrayList<>();

        int batchSize = configs.getReferralSearchBatchSize();

        ReferralSearchRequest searchRequest = ReferralSearchRequest.builder()
                .referral(ReferralSearch.builder()
                        .projectBeneficiaryClientReferenceId(beneficiaryClientRefIds)
                        .build())
                .requestInfo(requestInfo)
                .build();

        int fetched = 0;
        Long totalCount;

        do {
            SearchResponse<Referral> searchResponse = referralService.search(
                    searchRequest,
                    batchSize,
                    fetched,
                    criteria.getTenantId(),
                    criteria.getLastSyncedTime(),
                    criteria.getIncludeDeleted()
            );

            totalCount = searchResponse.getTotalCount();
            allReferrals.addAll(searchResponse.getResponse());
            fetched += batchSize;
        } while (fetched < totalCount);

        downsync.setReferrals(allReferrals);
    }

    /** Fetches HF referrals for all beneficiary identifier variants in batch and returns them.
     * Expands the input client reference ids with all ProjectBeneficiary identifier variants
     * so that referrals stored by any of the four PB IDs are captured.
     *
     * @param downsyncRequest         downsync request containing criteria and requestInfo
     * @param downsync                downsync object to read fetched project beneficiaries from
     * @param beneficiaryClientRefIds beneficiary client reference ids
     * @return list of all fetched HF referrals (used for taskStatus resolution; not set on downsync)
     */
    private List<HFReferral> searchHFReferrals(DownsyncRequest downsyncRequest,
                                               Downsync downsync,
                                               List<String> beneficiaryClientRefIds) throws InvalidTenantIdException {

        List<String> beneficiaryIds = getHFReferralBeneficiaryIds(downsync, beneficiaryClientRefIds);
        if (CollectionUtils.isEmpty(beneficiaryIds)) {
            return Collections.emptyList();
        }

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();
        List<HFReferral> allHFReferrals = new ArrayList<>();

        int batchSize = configs.getReferralSearchBatchSize();
        for (int i = 0; i < beneficiaryIds.size(); i += batchSize) {
            List<String> batch = getIdsForBatch(batchSize, i, beneficiaryIds);
            int fetched = 0;
            Long totalCount;

            HFReferralSearch search = HFReferralSearch.builder()
                    .beneficiaryId(batch)
                    .projectId(criteria.getProjectId())
                    .build();

            HFReferralSearchRequest searchRequest = HFReferralSearchRequest.builder()
                    .hfReferral(search)
                    .requestInfo(requestInfo)
                    .build();

            do {
                SearchResponse<HFReferral> searchResponse = hfReferralService.search(
                        searchRequest,
                        batchSize,
                        fetched,
                        criteria.getTenantId(),
                        criteria.getLastSyncedTime(),
                        criteria.getIncludeDeleted()
                );

                totalCount = searchResponse.getTotalCount() == null ? 0L : searchResponse.getTotalCount();
                if (!CollectionUtils.isEmpty(searchResponse.getResponse())) {
                    allHFReferrals.addAll(searchResponse.getResponse());
                }
                fetched += batchSize;
            } while (fetched < totalCount);
        }

        return allHFReferrals;
    }

    /** Builds the combined list of beneficiary IDs for HF referral lookup by merging the input
     * client reference ids with all four identifier variants from each ProjectBeneficiary,
     * so that referrals recorded by any ID variant are captured.
     *
     * @param downsync                downsync object holding fetched project beneficiaries
     * @param beneficiaryClientRefIds input client reference ids from the caller
     * @return deduplicated list of all beneficiary identifiers to use for HF referral lookup
     */
    private List<String> getHFReferralBeneficiaryIds(Downsync downsync, List<String> beneficiaryClientRefIds) {
        List<String> beneficiaryIds = new ArrayList<>();
        if (!CollectionUtils.isEmpty(beneficiaryClientRefIds)) {
            beneficiaryIds.addAll(beneficiaryClientRefIds);
        }

        if (!CollectionUtils.isEmpty(downsync.getProjectBeneficiaries())) {
            downsync.getProjectBeneficiaries().forEach(pb ->
                    Stream.of(pb.getId(), pb.getClientReferenceId(), pb.getBeneficiaryId(), pb.getBeneficiaryClientReferenceId())
                            .filter(Objects::nonNull)
                            .forEach(beneficiaryIds::add));
        }

        return beneficiaryIds.stream().distinct().collect(Collectors.toList());
    }


    /**
     * common method to fetch Ids with list of relation Ids like id of member with householdIds
     * @param idList
     * @param idListFieldName
     * @param tableName
     * @param lastChangedSince
     * @return
     */
    private List<String> getPrimaryIds(String tenantId, List<String> idList, String idListFieldName, String tableName, Long lastChangedSince) throws InvalidTenantIdException {

        /**
         * Adding lastShangedSince to id query to avoid load on API search for members
         */
        boolean isAndRequired = false;
        Map<String, Object> paramMap = new HashMap<>();

        if (CollectionUtils.isEmpty(idList))
            return Collections.emptyList();

        StringBuilder memberIdsquery = new StringBuilder("SELECT id from %s.%s WHERE ");


        if (!CollectionUtils.isEmpty(idList)) {

            memberIdsquery.append("%s IN (:%s)");
            paramMap.put(idListFieldName, idList);
            isAndRequired = true;
        }

        if (null != lastChangedSince) {
            if(isAndRequired)
                memberIdsquery.append(" AND ");
            memberIdsquery.append(" lastModifiedTime >= (:lastChangedSince)");
            paramMap.put("lastChangedSince", lastChangedSince);
        }

        String finalQuery = String.format(memberIdsquery.toString(), SCHEMA_REPLACE_STRING, tableName, idListFieldName, idListFieldName);
        finalQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(finalQuery, tenantId);
        List<String> memberids = jdbcTemplate.queryForList(finalQuery, paramMap, String.class);
        return memberids;
    }

    private List<String> getIdsForBatch(int batchSize, int offset, List<String> idList) {
        return idList.subList(offset, Math.min(offset + batchSize, idList.size()));
    }

    /**
     * append url params
     *
     * @param url
     * @param criteria
     * @param offset
     * @param limit
     * @param sendPrevSyncTime
     * @return
     */
    private StringBuilder appendUrlParams(StringBuilder url, DownsyncCriteria criteria, Integer offset, Integer limit, boolean sendPrevSyncTime) {

        url.append("?tenantId=")
                .append(criteria.getTenantId())
                .append("&includeDeleted=")
                .append(criteria.getIncludeDeleted())
                .append("&limit=");

        if (null != limit && limit != 0)
            url.append(limit);
        else
            url.append(criteria.getLimit());

        url.append("&offset=");

        if(null != offset)
            url.append(offset);
        else
            url.append(criteria.getOffset());

        if(sendPrevSyncTime && null != criteria.getLastSyncedTime())
            url.append("&lastChangedSince=").append(criteria.getLastSyncedTime());

        return url;
    }
}
