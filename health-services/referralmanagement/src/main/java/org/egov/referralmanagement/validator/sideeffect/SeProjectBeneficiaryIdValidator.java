package org.egov.referralmanagement.validator.sideeffect;

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
import org.egov.common.models.referralmanagement.sideeffect.SideEffect;
import org.egov.common.models.referralmanagement.sideeffect.SideEffectBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;

/**
 *  Validate whether project beneficiary exist in db or not using project beneficiary id and project beneficiary client beneficiary id for SideEffect object
 */
@Component
@Order(value = 3)
@Slf4j
public class SeProjectBeneficiaryIdValidator implements Validator<SideEffectBulkRequest, SideEffect> {
    private final ServiceRequestClient serviceRequestClient;
    private final ReferralManagementConfiguration referralManagementConfiguration;

    @Autowired
    public SeProjectBeneficiaryIdValidator(ServiceRequestClient serviceRequestClient, ReferralManagementConfiguration referralManagementConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.referralManagementConfiguration = referralManagementConfiguration;
    }

    /**
     *
     * @param request
     * @return
     */
    @Override
    public Map<SideEffect, List<Error>> validate(SideEffectBulkRequest request) {
        log.info("validating project task id");
        Map<SideEffect, List<Error>> errorDetailsMap = new HashMap<>();
        List<SideEffect> entities = request.getSideEffects();
        Map<String, List<SideEffect>> tenantIdSideEffectMap = entities.stream().collect(Collectors.groupingBy(SideEffect::getTenantId));
        tenantIdSideEffectMap.forEach((tenantId, sideEffects) -> {
            List<SideEffect> sideEffectList = tenantIdSideEffectMap.get(tenantId);
            if (!sideEffectList.isEmpty()) {
                List<ProjectBeneficiary> existingProjectBeneficiaries = null;
                final List<String> projectBeneficiaryIdList = new ArrayList<>();
                final List<String> projectBeneficiaryClientReferenceIdList = new ArrayList<>();
                sideEffectList.forEach(sideEffect -> {
                    addIgnoreNull(projectBeneficiaryIdList, sideEffect.getProjectBeneficiaryId());
                    addIgnoreNull(projectBeneficiaryClientReferenceIdList, sideEffect.getProjectBeneficiaryClientReferenceId());
                });
                ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder()
                        .id(projectBeneficiaryIdList.isEmpty() ? null : projectBeneficiaryIdList)
                        .clientReferenceId(projectBeneficiaryClientReferenceIdList.isEmpty() ? null : projectBeneficiaryClientReferenceIdList)
                        .build();
                try {
                    BeneficiaryBulkResponse beneficiaryBulkResponse = serviceRequestClient.fetchResult(
                            new StringBuilder(referralManagementConfiguration.getProjectHost()
                                    + referralManagementConfiguration.getProjectBeneficiarySearchUrl()
                                    +"?limit=" + entities.size()
                                    + "&offset=0&tenantId=" + tenantId),
                            BeneficiarySearchRequest.builder().requestInfo(request.getRequestInfo()).projectBeneficiary(projectBeneficiarySearch).build(),
                            BeneficiaryBulkResponse.class
                    );
                    existingProjectBeneficiaries = beneficiaryBulkResponse.getProjectBeneficiaries();
                } catch (Exception e) {
                    throw new CustomException("Project Beneficiaries failed to fetch", "Exception : "+e.getMessage());
                }
                final List<String> existingProjectBeneficiaryIds = new ArrayList<>();
                final List<String> existingProjectBeneficiaryClientReferenceIds = new ArrayList<>();
                existingProjectBeneficiaries.forEach(projectBeneficiary -> {
                    existingProjectBeneficiaryIds.add(projectBeneficiary.getId());
                    existingProjectBeneficiaryClientReferenceIds.add(projectBeneficiary.getClientReferenceId());
                });
                /**
                 * for all the entities that do not have any error in previous validations
                 * checking whether the project beneficiary client reference id is not null and exist in the db
                 */
                List<SideEffect> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                        ( Objects.nonNull(entity.getProjectBeneficiaryClientReferenceId())
                                && !existingProjectBeneficiaryClientReferenceIds.contains(entity.getProjectBeneficiaryClientReferenceId()) )
                        || ( Objects.nonNull(entity.getProjectBeneficiaryId())
                                && !existingProjectBeneficiaryIds.contains(entity.getProjectBeneficiaryId()) )
                ).collect(Collectors.toList());
                invalidEntities.forEach(sideEffect -> {
                    Error error = getErrorForNonExistentEntity();
                    populateErrorDetails(sideEffect, error, errorDetailsMap);
                });
            }
        });
        return errorDetailsMap;
    }
    private void addIgnoreNull(List<String> list, String item) {
        if(Objects.nonNull(item)) list.add(item);
    }
}
