package org.egov.referralmanagement.validator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.project.BeneficiaryBulkResponse;
import org.egov.common.models.project.BeneficiarySearchRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.ProjectBeneficiarySearch;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;


/**
 * Validate whether project beneficiary exist in db or not using project beneficiary id and project beneficiary client beneficiary id for Referral object
 */
@Component
@Order(value = 3)
@Slf4j
public class RmProjectBeneficiaryIdValidator implements Validator<ReferralBulkRequest, Referral> {
    private final ServiceRequestClient serviceRequestClient;
    private final ReferralManagementConfiguration referralManagementConfiguration;

    public RmProjectBeneficiaryIdValidator(ServiceRequestClient serviceRequestClient, ReferralManagementConfiguration referralManagementConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.referralManagementConfiguration = referralManagementConfiguration;
    }

    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        log.info("validating project beneficiary id");
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        List<Referral> entities = request.getReferrals();
        Map<String, List<Referral>> tenantIdReferralMap = entities.stream().collect(Collectors.groupingBy(Referral::getTenantId));
        tenantIdReferralMap.forEach((tenantId, referralList) -> {
            /** Get all the existing project beneficiaries in the referral list from Project Service
             */
            List<ProjectBeneficiary> existingProjectBeneficiaries = getExistingProjectBeneficiaries(tenantId, referralList, request);
            /** Validate project beneficiaries and populate error map if invalid entities are found
             */
            validateAndPopulateErrors(existingProjectBeneficiaries, entities, errorDetailsMap);
        });
        return errorDetailsMap;
    }
    private void addIgnoreNull(List<String> list, String item) {
        if(Objects.nonNull(item)) list.add(item);
    }

    private List<ProjectBeneficiary> getExistingProjectBeneficiaries(String tenantId, List<Referral> referrals, ReferralBulkRequest request) {
        List<ProjectBeneficiary> existingProjectBeneficiaries = null;
        final List<String> projectBeneficiaryIdList = new ArrayList<>();
        final List<String> projectBeneficiaryClientReferenceIdList = new ArrayList<>();
        referrals.forEach(referral -> {
            addIgnoreNull(projectBeneficiaryIdList, referral.getProjectBeneficiaryId());
            addIgnoreNull(projectBeneficiaryClientReferenceIdList, referral.getProjectBeneficiaryClientReferenceId());
        });
        ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder()
                .id(projectBeneficiaryIdList.isEmpty() ? null : projectBeneficiaryIdList)
                .clientReferenceId(projectBeneficiaryClientReferenceIdList.isEmpty() ? null : projectBeneficiaryClientReferenceIdList)
                .build();
        try {
            // using project beneficiary search and fetching the valid ids.
            BeneficiaryBulkResponse beneficiaryBulkResponse = serviceRequestClient.fetchResult(
                    new StringBuilder(referralManagementConfiguration.getProjectHost()
                            + referralManagementConfiguration.getProjectBeneficiarySearchUrl()
                            +"?limit=" + referrals.size()
                            + "&offset=0&tenantId=" + tenantId),
                    BeneficiarySearchRequest.builder().requestInfo(request.getRequestInfo()).projectBeneficiary(projectBeneficiarySearch).build(),
                    BeneficiaryBulkResponse.class
            );
            existingProjectBeneficiaries = beneficiaryBulkResponse.getProjectBeneficiaries();
        } catch (Exception e) {
            throw new CustomException("Project Beneficiaries failed to fetch", "Exception : "+e.getMessage());
        }
        return existingProjectBeneficiaries;
    }

    private void validateAndPopulateErrors(List<ProjectBeneficiary> existingProjectBeneficiaries, List<Referral> entities, Map<Referral, List<Error>> errorDetailsMap) {
        final List<String> existingProjectBeneficiaryIds = new ArrayList<>();
        final List<String> existingProjectBeneficiaryClientReferenceIds = new ArrayList<>();
        existingProjectBeneficiaries.forEach(projectBeneficiary -> {
            existingProjectBeneficiaryIds.add(projectBeneficiary.getId());
            existingProjectBeneficiaryClientReferenceIds.add(projectBeneficiary.getClientReferenceId());
        });
        List<Referral> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                (Objects.nonNull(entity.getProjectBeneficiaryClientReferenceId()) && !existingProjectBeneficiaryClientReferenceIds.contains(entity.getProjectBeneficiaryClientReferenceId()) )
                        || (Objects.nonNull(entity.getProjectBeneficiaryId()) && !existingProjectBeneficiaryIds.contains(entity.getProjectBeneficiaryId()))
        ).collect(Collectors.toList());
        invalidEntities.forEach(referral -> {
            Error error = getErrorForNonExistentEntity();
            populateErrorDetails(referral, error, errorDetailsMap);
        });
    }
}
