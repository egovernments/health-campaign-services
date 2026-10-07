package org.egov.transformer.transformationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.household.HouseholdMemberSearch;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.transformer.producer.TransformerErrorProducer;
import org.egov.transformer.service.HouseholdService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Re-indexes households and household members with the project of their project beneficiary. Covers records created
 * before their beneficiary; records created after it resolve the project themselves in
 * {@link HouseholdTransformationService} and {@link HouseholdMemberTransformationService}.
 */
@Slf4j
@Component
public class ProjectBeneficiaryTransformationService {
    private final ObjectMapper objectMapper;
    private final HouseholdService householdService;
    private final HouseholdTransformationService householdTransformationService;
    private final HouseholdMemberTransformationService householdMemberTransformationService;
    private final TransformerErrorProducer errorProducer;

    public ProjectBeneficiaryTransformationService(@Qualifier("objectMapper") ObjectMapper objectMapper, HouseholdService householdService, HouseholdTransformationService householdTransformationService, HouseholdMemberTransformationService householdMemberTransformationService, TransformerErrorProducer errorProducer) {
        this.objectMapper = objectMapper;
        this.householdService = householdService;
        this.householdTransformationService = householdTransformationService;
        this.householdMemberTransformationService = householdMemberTransformationService;
        this.errorProducer = errorProducer;
    }

    public void transform(List<ProjectBeneficiary> projectBeneficiaryList) {
        if (CollectionUtils.isEmpty(projectBeneficiaryList)) {
            return;
        }
        log.info("transforming for PROJECT BENEFICIARY id's {}", projectBeneficiaryList.stream()
                .filter(Objects::nonNull)
                .map(ProjectBeneficiary::getId).collect(Collectors.toList()));
        projectBeneficiaryList.stream()
                .filter(Objects::nonNull)
                .forEach(this::transform);
    }

    private void transform(ProjectBeneficiary projectBeneficiary) {
        if (Boolean.TRUE.equals(projectBeneficiary.getIsDeleted()) || StringUtils.isBlank(projectBeneficiary.getProjectId())
                || StringUtils.isBlank(projectBeneficiary.getTenantId())) {
            log.info("Skipping PROJECT BENEFICIARY {}, deleted or projectId / tenantId missing", projectBeneficiary.getId());
            return;
        }
        // one beneficiary failing should not stop the rest of the batch; failed beneficiary is pushed to the error
        // topic in the consumer's payload format so it can be replayed
        try {
            String projectId = projectBeneficiary.getProjectId();
            List<HouseholdMember> householdMembers = searchHouseholdMembers(projectBeneficiary).stream()
                    .filter(householdMember -> householdMember != null && !Boolean.TRUE.equals(householdMember.getIsDeleted()))
                    .collect(Collectors.toList());
            if (!householdMembers.isEmpty()) {
                householdMemberTransformationService.transform(householdMembers, projectId);
            }
            List<Household> households = searchHouseholds(projectBeneficiary, householdMembers);
            if (!households.isEmpty()) {
                householdTransformationService.transform(households, projectId);
            }
            if (householdMembers.isEmpty() && households.isEmpty()) {
                log.info("No households / household members yet for PROJECT BENEFICIARY {}", projectBeneficiary.getId());
            }
        } catch (Exception e) {
            log.error("TRANSFORMER error while transforming PROJECT BENEFICIARY {} {}", projectBeneficiary.getId(), ExceptionUtils.getStackTrace(e));
            errorProducer.sendToErrorTopic(toPayload(projectBeneficiary), null, e);
        }
    }

    // beneficiary is the household for household-based projects and the individual for individual-based projects
    private List<HouseholdMember> searchHouseholdMembers(ProjectBeneficiary projectBeneficiary) {
        String tenantId = projectBeneficiary.getTenantId();
        String beneficiaryId = projectBeneficiary.getBeneficiaryId();
        String beneficiaryClientReferenceId = projectBeneficiary.getBeneficiaryClientReferenceId();
        List<HouseholdMember> householdMembers = Collections.emptyList();
        if (StringUtils.isNotBlank(beneficiaryId)) {
            householdMembers = householdService.searchHouseholdMembers(HouseholdMemberSearch.builder()
                    .householdId(beneficiaryId).build(), tenantId);
            if (householdMembers.isEmpty()) {
                householdMembers = householdService.searchHouseholdMembers(HouseholdMemberSearch.builder()
                        .individualId(beneficiaryId).build(), tenantId);
            }
        } else if (StringUtils.isNotBlank(beneficiaryClientReferenceId)) {
            householdMembers = householdService.searchHouseholdMembers(HouseholdMemberSearch.builder()
                    .householdClientReferenceId(beneficiaryClientReferenceId).build(), tenantId);
            if (householdMembers.isEmpty()) {
                householdMembers = householdService.searchHouseholdMembers(HouseholdMemberSearch.builder()
                        .individualClientReferenceId(beneficiaryClientReferenceId).build(), tenantId);
            }
        } else {
            log.warn("PROJECT BENEFICIARY {} has no beneficiaryId or beneficiaryClientReferenceId", projectBeneficiary.getId());
        }
        return householdMembers;
    }

    // households of the found members; for a household-based beneficiary with no members yet, the beneficiary household
    private List<Household> searchHouseholds(ProjectBeneficiary projectBeneficiary, List<HouseholdMember> householdMembers) {
        Set<String> householdClientReferenceIds = householdMembers.stream()
                .map(HouseholdMember::getHouseholdClientReferenceId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (householdClientReferenceIds.isEmpty() && StringUtils.isNotBlank(projectBeneficiary.getBeneficiaryClientReferenceId())) {
            householdClientReferenceIds.add(projectBeneficiary.getBeneficiaryClientReferenceId());
        }
        return householdClientReferenceIds.stream()
                .flatMap(clientReferenceId -> householdService.searchHousehold(clientReferenceId, projectBeneficiary.getTenantId()).stream())
                .filter(household -> household != null && !Boolean.TRUE.equals(household.getIsDeleted()))
                .collect(Collectors.toList());
    }

    private Object toPayload(ProjectBeneficiary projectBeneficiary) {
        try {
            return objectMapper.writeValueAsString(Collections.singletonList(projectBeneficiary));
        } catch (Exception e) {
            return projectBeneficiary;
        }
    }
}
