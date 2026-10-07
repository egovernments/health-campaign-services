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
 * Re-indexes the household and household members with the project of their project beneficiary. Covers records
 * created before their beneficiary; records created after it resolve the project themselves in
 * {@link HouseholdTransformationService} and {@link HouseholdMemberTransformationService}.
 */
@Slf4j
@Component
public class ProjectBeneficiaryTransformationService {
    private final ObjectMapper objectMapper;
    private final HouseholdService householdService;
    private final HouseholdMemberTransformationService householdMemberTransformationService;
    private final HouseholdTransformationService householdTransformationService;
    private final TransformerErrorProducer errorProducer;

    public ProjectBeneficiaryTransformationService(@Qualifier("objectMapper") ObjectMapper objectMapper, HouseholdService householdService, HouseholdMemberTransformationService householdMemberTransformationService, HouseholdTransformationService householdTransformationService, TransformerErrorProducer errorProducer) {
        this.objectMapper = objectMapper;
        this.householdService = householdService;
        this.householdMemberTransformationService = householdMemberTransformationService;
        this.householdTransformationService = householdTransformationService;
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
            List<HouseholdMember> householdMembers = searchHouseholdMembers(projectBeneficiary).stream()
                    .filter(householdMember -> householdMember != null && !Boolean.TRUE.equals(householdMember.getIsDeleted()))
                    .collect(Collectors.toList());
            if (householdMembers.isEmpty()) {
                log.info("No household members yet for PROJECT BENEFICIARY {}", projectBeneficiary.getId());
            } else {
                householdMemberTransformationService.transform(householdMembers, projectBeneficiary.getProjectId());
            }
            reindexHouseholds(projectBeneficiary, householdMembers);
        } catch (Exception e) {
            log.error("TRANSFORMER error while transforming PROJECT BENEFICIARY {} {}", projectBeneficiary.getId(), ExceptionUtils.getStackTrace(e));
            errorProducer.sendToErrorTopic(toPayload(projectBeneficiary), null, e);
        }
    }

    // Households carry no project reference, so they are re-indexed with the beneficiary's project. The household is
    // the one the beneficiary's members belong to (household-based: the beneficiary itself, individual-based: the
    // individual's household); with no members yet, the beneficiary itself is tried as the household.
    private void reindexHouseholds(ProjectBeneficiary projectBeneficiary, List<HouseholdMember> householdMembers) {
        Set<String> householdClientReferenceIds = householdMembers.stream()
                .map(HouseholdMember::getHouseholdClientReferenceId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (householdClientReferenceIds.isEmpty() && StringUtils.isNotBlank(projectBeneficiary.getBeneficiaryClientReferenceId())) {
            householdClientReferenceIds.add(projectBeneficiary.getBeneficiaryClientReferenceId());
        }
        List<Household> households = householdClientReferenceIds.stream()
                .map(householdClientReferenceId -> householdService.searchHousehold(householdClientReferenceId, projectBeneficiary.getTenantId()))
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(household -> household != null && !Boolean.TRUE.equals(household.getIsDeleted()))
                .collect(Collectors.toList());
        if (households.isEmpty()) {
            log.info("No household to re-index for PROJECT BENEFICIARY {}", projectBeneficiary.getId());
            return;
        }
        householdTransformationService.transform(households, projectBeneficiary.getProjectId());
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

    private Object toPayload(ProjectBeneficiary projectBeneficiary) {
        try {
            return objectMapper.writeValueAsString(Collections.singletonList(projectBeneficiary));
        } catch (Exception e) {
            return projectBeneficiary;
        }
    }
}
