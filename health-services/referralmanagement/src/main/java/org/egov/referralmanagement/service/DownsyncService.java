package org.egov.referralmanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
import org.egov.common.models.individual.Identifier;
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

    private ObjectMapper objectMapper;

    private final MultiStateInstanceUtil multiStateInstanceUtil;

    @Autowired
    public DownsyncService( ServiceRequestClient serviceRequestClient,
                            ReferralManagementConfiguration referralManagementConfiguration,
                            NamedParameterJdbcTemplate jdbcTemplate,
                            SideEffectService sideEffectService,
                            ReferralManagementService referralService,
                            HFReferralService hfReferralService,
                            MasterDataService masterDataService,
                            ObjectMapper objectMapper,
                            MultiStateInstanceUtil multiStateInstanceUtil) {

        this.restClient = serviceRequestClient;
        this.configs = referralManagementConfiguration;
        this.jdbcTemplate = jdbcTemplate;
        this.sideEffectService = sideEffectService;
        this.referralService = referralService;
        this.hfReferralService = hfReferralService;
        this.masterDataService = masterDataService;
        this.objectMapper = objectMapper;
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
                .map(householdMember -> toBeneficiaryInfo(
                        householdMember,
                        individualByClientReferenceId.get(householdMember.getIndividualClientReferenceId()),
                        householdByClientReferenceId.get(householdMember.getHouseholdClientReferenceId()),
                        taskStatusByKey,
                        referredBeneficiaryKeys,
                        projectBeneficiaryByKey
                ))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        downsync.setBeneficiaryInfo(beneficiaryInfo);
    }

    private BeneficiaryInfo toBeneficiaryInfo(HouseholdMember householdMember,
                                              Individual individual,
                                              Household household,
                                              Map<String, LatestTaskStatus> taskStatusByKey,
                                              Map<String, LatestReferral> referredBeneficiaryKeys,
                                              Map<String, ProjectBeneficiary> projectBeneficiaryByKey) {
        if (individual == null) {
            return null;
        }

        Identifier identifier = getPrimaryIdentifier(individual);
        AuditDetails auditDetails = individual.getAuditDetails();
        AuditDetails clientAuditDetails = individual.getClientAuditDetails();

        org.egov.common.models.household.Address householdAddress = household == null ? null : household.getAddress();
        org.egov.common.models.individual.Address individualAddress = getPrimaryAddress(individual);

        Double latitude = householdAddress == null ? null : householdAddress.getLatitude();
        Double longitude = householdAddress == null ? null : householdAddress.getLongitude();
        if (latitude == null) {
            latitude = getLatitude(individualAddress);
        }
        if (longitude == null) {
            longitude = getLongitude(individualAddress);
        }

        return BeneficiaryInfo.builder()
                .id(individual.getId())
                .householdClientReferenceId(householdMember.getHouseholdClientReferenceId())
                .givenName(individual.getName() == null ? null : individual.getName().getGivenName())
                .identifierType(identifier == null ? null : identifier.getIdentifierType())
                .identifierId(identifier == null ? null : identifier.getIdentifierId())
                .isHead(householdMember.getIsHeadOfHousehold())
                .taskStatus(resolveTaskStatus(individual, householdMember, household, taskStatusByKey,
                        referredBeneficiaryKeys, projectBeneficiaryByKey))
                .mobileNumber(individual.getMobileNumber())
                .latitude(latitude)
                .longitude(longitude)
                .auditCreatedBy(auditDetails == null ? null : auditDetails.getCreatedBy())
                .auditCreatedTime(auditDetails == null ? null : auditDetails.getCreatedTime())
                .clientCreatedBy(clientAuditDetails == null ? null : clientAuditDetails.getCreatedBy())
                .clientCreatedTime(clientAuditDetails == null ? null : clientAuditDetails.getCreatedTime())
                .clientModifiedBy(clientAuditDetails == null ? null : clientAuditDetails.getLastModifiedBy())
                .clientModifiedTime(clientAuditDetails == null ? null : clientAuditDetails.getLastModifiedTime())
                .auditModifiedBy(auditDetails == null ? null : auditDetails.getLastModifiedBy())
                .auditModifiedTime(auditDetails == null ? null : auditDetails.getLastModifiedTime())
                .clientReferenceId(individual.getClientReferenceId())
                .tenantId(individual.getTenantId())
                .isDeleted(isDeleted(individual, householdMember, household))
                .rowVersion(individual.getRowVersion())
                .additionalFields(serializeAdditionalFields(individual.getAdditionalFields()))
                .build();
    }

    // Indexes each ProjectBeneficiary by all 4 identifiers so tasks and referrals can be
    // looked up regardless of which identifier a given service used when creating them.
    private Map<String, ProjectBeneficiary> buildProjectBeneficiaryByKey(List<ProjectBeneficiary> projectBeneficiaries) {
        Map<String, ProjectBeneficiary> projectBeneficiaryByKey = new HashMap<>();
        if (CollectionUtils.isEmpty(projectBeneficiaries)) {
            return projectBeneficiaryByKey;
        }

        projectBeneficiaries.forEach(projectBeneficiary -> {
            putProjectBeneficiaryIfNotNull(projectBeneficiaryByKey, projectBeneficiary.getId(), projectBeneficiary);
            putProjectBeneficiaryIfNotNull(projectBeneficiaryByKey, projectBeneficiary.getClientReferenceId(), projectBeneficiary);
            putProjectBeneficiaryIfNotNull(projectBeneficiaryByKey, projectBeneficiary.getBeneficiaryId(), projectBeneficiary);
            putProjectBeneficiaryIfNotNull(projectBeneficiaryByKey, projectBeneficiary.getBeneficiaryClientReferenceId(), projectBeneficiary);
        });
        return projectBeneficiaryByKey;
    }

    private void putProjectBeneficiaryIfNotNull(Map<String, ProjectBeneficiary> projectBeneficiaryByKey,
                                                String key,
                                                ProjectBeneficiary projectBeneficiary) {
        if (key != null) {
            projectBeneficiaryByKey.putIfAbsent(key, projectBeneficiary);
        }
    }

    // Builds a map from every beneficiary identifier to its latest task status.
    // Expanding to all ProjectBeneficiary keys ensures the status can be found by individual/household ID
    // during candidate-key resolution, regardless of which identifier the task references.
    // merge() with LatestTaskStatus::latest retains only the most recently created task per key,
    // collapsing multi-cycle/dose history to a single status for display.
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
                    putTaskStatusIfNotNull(taskStatusByKey, projectBeneficiaryClientReferenceId, taskStatus);

                    ProjectBeneficiary projectBeneficiary =
                            projectBeneficiaryByKey.get(projectBeneficiaryClientReferenceId);
                    if (projectBeneficiary != null) {
                        putTaskStatusIfNotNull(taskStatusByKey, projectBeneficiary.getId(), taskStatus);
                        putTaskStatusIfNotNull(taskStatusByKey, projectBeneficiary.getClientReferenceId(), taskStatus);
                        putTaskStatusIfNotNull(taskStatusByKey, projectBeneficiary.getBeneficiaryId(), taskStatus);
                        putTaskStatusIfNotNull(taskStatusByKey, projectBeneficiary.getBeneficiaryClientReferenceId(), taskStatus);
                    }
                });
        return taskStatusByKey;
    }

    private void putTaskStatusIfNotNull(Map<String, LatestTaskStatus> taskStatusByKey,
                                        String key,
                                        LatestTaskStatus taskStatus) {
        if (key != null) {
            taskStatusByKey.merge(key, taskStatus, LatestTaskStatus::latest);
        }
    }

    // Builds a map from every beneficiary identifier to its latest non-deleted HFReferral.
    // HFReferrals store beneficiaryId which may be a PB id, so we expand to all PB keys
    // to allow referral lookup by individual/household ID during candidate-key resolution.
    private Map<String, LatestReferral> buildReferredBeneficiaryKeys(List<HFReferral> hfReferrals,
                                                                    Map<String, ProjectBeneficiary> projectBeneficiaryByKey) {
        if (CollectionUtils.isEmpty(hfReferrals)) {
            return Collections.emptyMap();
        }

        Map<String, LatestReferral> referredBeneficiaryKeys = new HashMap<>();
        hfReferrals.stream()
                .filter(hfReferral -> !Boolean.TRUE.equals(hfReferral.getIsDeleted()))
                .forEach(hfReferral -> putReferralTimeIfNotNull(
                        referredBeneficiaryKeys,
                        hfReferral.getBeneficiaryId(),
                        LatestReferral.from(hfReferral)
                ));

        // Snapshot before expanding so newly added keys don't trigger further expansion.
        new HashMap<>(referredBeneficiaryKeys).forEach((beneficiaryId, referral) -> {
            ProjectBeneficiary projectBeneficiary = projectBeneficiaryByKey.get(beneficiaryId);
            if (projectBeneficiary != null) {
                putReferralTimeIfNotNull(referredBeneficiaryKeys, projectBeneficiary.getId(), referral);
                putReferralTimeIfNotNull(referredBeneficiaryKeys, projectBeneficiary.getClientReferenceId(), referral);
                putReferralTimeIfNotNull(referredBeneficiaryKeys, projectBeneficiary.getBeneficiaryId(), referral);
                putReferralTimeIfNotNull(referredBeneficiaryKeys, projectBeneficiary.getBeneficiaryClientReferenceId(), referral);
            }
        });

        return referredBeneficiaryKeys;
    }

    private void putReferralTimeIfNotNull(Map<String, LatestReferral> referredBeneficiaryKeys,
                                          String key,
                                          LatestReferral referral) {
        if (key != null) {
            referredBeneficiaryKeys.merge(key, referral, LatestReferral::latest);
        }
    }

    // Resolves the display status for a beneficiary by combining task history and referral state.
    // BeneficiaryReferred takes priority over any task status when a referral matches the
    // current cycle/dose — or when there are no tasks at all (referral without prior delivery).
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

    private List<String> getBeneficiaryCandidateKeys(Individual individual,
                                                     HouseholdMember householdMember,
                                                     Household household,
                                                     Map<String, ProjectBeneficiary> projectBeneficiaryByKey) {
        List<String> candidateKeys = new ArrayList<>();
        addIfNotNull(candidateKeys, individual.getId());
        addIfNotNull(candidateKeys, individual.getClientReferenceId());
        addIfNotNull(candidateKeys, householdMember.getIndividualId());
        addIfNotNull(candidateKeys, householdMember.getIndividualClientReferenceId());
        addIfNotNull(candidateKeys, householdMember.getHouseholdId());
        addIfNotNull(candidateKeys, householdMember.getHouseholdClientReferenceId());
        if (household != null) {
            addIfNotNull(candidateKeys, household.getId());
            addIfNotNull(candidateKeys, household.getClientReferenceId());
        }

        // Snapshot before expanding so keys added during this loop don't cause further iterations.
        new ArrayList<>(candidateKeys).forEach(candidateKey -> {
            ProjectBeneficiary projectBeneficiary = projectBeneficiaryByKey.get(candidateKey);
            if (projectBeneficiary != null) {
                addIfNotNull(candidateKeys, projectBeneficiary.getId());
                addIfNotNull(candidateKeys, projectBeneficiary.getClientReferenceId());
                addIfNotNull(candidateKeys, projectBeneficiary.getBeneficiaryId());
                addIfNotNull(candidateKeys, projectBeneficiary.getBeneficiaryClientReferenceId());
            }
        });

        return candidateKeys.stream().distinct().collect(Collectors.toList());
    }

    private void addIfNotNull(List<String> keys, String key) {
        if (key != null) {
            keys.add(key);
        }
    }

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

    private static class LatestReferral {
        private final Long referralTime;
        private final String cycleId;
        private final String dose;

        private LatestReferral(Long referralTime, String cycleId, String dose) {
            this.referralTime = referralTime;
            this.cycleId = cycleId;
            this.dose = dose;
        }

        private static LatestReferral from(HFReferral hfReferral) {
            AuditDetails auditDetails = hfReferral.getAuditDetails();
            return new LatestReferral(
                    getAuditTime(auditDetails),
                    getAdditionalFieldValue(hfReferral.getAdditionalFields(), "cycleId"),
                    getAdditionalFieldValue(hfReferral.getAdditionalFields(), "dose")
            );
        }

        private static LatestReferral latest(LatestReferral existing, LatestReferral replacement) {
            return replacement.referralTime() >= existing.referralTime() ? replacement : existing;
        }

        // Determines whether this referral overrides the given task status.
        // When cycleId/dose are present on the referral, match by cycle+dose (null on referral = wildcard).
        // When absent, fall back to time: referral must have occurred after the task was created.
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

    // null on `first` (the referral field) acts as a wildcard — matches any task value.
    private static boolean isSameValue(String first, String second) {
        return first == null || first.equals(second);
    }

    private static Long getAuditTime(AuditDetails auditDetails) {
        if (auditDetails == null) {
            return 0L;
        }
        return firstTime(auditDetails.getLastModifiedTime(), auditDetails.getCreatedTime(), 0L);
    }

    private static Long getCreatedTime(AuditDetails auditDetails) {
        if (auditDetails == null) {
            return 0L;
        }
        return firstTime(auditDetails.getCreatedTime(), auditDetails.getLastModifiedTime(), 0L);
    }

    private static Long firstTime(Long... times) {
        for (Long time : times) {
            if (time != null && time > 0L) {
                return time;
            }
        }
        return 0L;
    }

    private Identifier getPrimaryIdentifier(Individual individual) {
        if (CollectionUtils.isEmpty(individual.getIdentifiers())) {
            return null;
        }

        return individual.getIdentifiers().stream()
                .filter(identifier -> !Boolean.TRUE.equals(identifier.getIsDeleted()))
                .findFirst()
                .orElse(individual.getIdentifiers().get(0));
    }

    private org.egov.common.models.individual.Address getPrimaryAddress(Individual individual) {
        if (CollectionUtils.isEmpty(individual.getAddress())) {
            return null;
        }
        return individual.getAddress().get(0);
    }

    private Double getLatitude(org.egov.common.models.individual.Address address) {
        return address == null ? null : address.getLatitude();
    }

    private Double getLongitude(org.egov.common.models.individual.Address address) {
        return address == null ? null : address.getLongitude();
    }

    private Boolean isDeleted(Individual individual, HouseholdMember householdMember, Household household) {
        return Boolean.TRUE.equals(individual.getIsDeleted())
                || Boolean.TRUE.equals(householdMember.getIsDeleted())
                || (household != null && Boolean.TRUE.equals(household.getIsDeleted()));
    }

    private String serializeAdditionalFields(Object additionalFields) {
        if (additionalFields == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(additionalFields);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize beneficiary info additionalFields", e);
            return null;
        }
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

    private void referralSearch(DownsyncRequest downsyncRequest, Downsync downsync,
                                List<String> beneficiaryClientRefIds) throws InvalidTenantIdException {

        DownsyncCriteria criteria = downsyncRequest.getDownsyncCriteria();
        RequestInfo requestInfo = downsyncRequest.getRequestInfo();

        if(CollectionUtils.isEmpty(beneficiaryClientRefIds)) {
            return;
        }

        List<Referral> allReferrals = new ArrayList<>();

        /* get batch size to fetch project tasks from environment */
        int batchSize = configs.getReferralSearchBatchSize();

        int fetched = 0;
        Long totalCount;

        do {
            ReferralSearch search = ReferralSearch.builder()
                    .projectBeneficiaryClientReferenceId(beneficiaryClientRefIds)
                    .build();

            ReferralSearchRequest searchRequest = ReferralSearchRequest.builder()
                    .referral(search)
                    .requestInfo(requestInfo)
                    .build();

            SearchResponse<Referral> searchResponse = referralService.search(
                    searchRequest,
                    batchSize,
                    fetched,
                    criteria.getTenantId(),
                    criteria.getLastSyncedTime(),
                    criteria.getIncludeDeleted()
            );

            totalCount = searchResponse.getTotalCount();
            List<Referral> referrals = searchResponse.getResponse();
            allReferrals.addAll(referrals);

            fetched += batchSize;
        } while (fetched < totalCount);

        downsync.setReferrals(allReferrals);
    }

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

    private List<String> getHFReferralBeneficiaryIds(Downsync downsync, List<String> beneficiaryClientRefIds) {
        List<String> beneficiaryIds = new ArrayList<>();
        if (!CollectionUtils.isEmpty(beneficiaryClientRefIds)) {
            beneficiaryIds.addAll(beneficiaryClientRefIds);
        }

        if (!CollectionUtils.isEmpty(downsync.getProjectBeneficiaries())) {
            downsync.getProjectBeneficiaries().forEach(projectBeneficiary -> {
                addIfNotNull(beneficiaryIds, projectBeneficiary.getId());
                addIfNotNull(beneficiaryIds, projectBeneficiary.getClientReferenceId());
                addIfNotNull(beneficiaryIds, projectBeneficiary.getBeneficiaryId());
                addIfNotNull(beneficiaryIds, projectBeneficiary.getBeneficiaryClientReferenceId());
            });
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
