package org.egov.referralmanagement.validator;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.Error;
import org.egov.common.models.project.BeneficiaryBulkResponse;
import org.egov.common.models.project.BeneficiarySearchRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.ProjectBeneficiarySearch;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.ProjectStaffBulkResponse;
import org.egov.common.models.project.ProjectStaffSearch;
import org.egov.common.models.project.ProjectStaffSearchRequest;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralBulkRequest;
import org.egov.common.validator.Validator;
import org.egov.referralmanagement.config.ReferralManagementConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.referralmanagement.Constants.PROJECT_STAFF;


@Component
@Order(value = 3)
@Slf4j
public class RmProjectEntitiesIdValidator implements Validator<ReferralBulkRequest, Referral> {
    private final ServiceRequestClient serviceRequestClient;
    private final ReferralManagementConfiguration referralManagementConfiguration;

    @Autowired
    public RmProjectEntitiesIdValidator(ServiceRequestClient serviceRequestClient, ReferralManagementConfiguration referralManagementConfiguration) {
        this.serviceRequestClient = serviceRequestClient;
        this.referralManagementConfiguration = referralManagementConfiguration;
    }


    @Override
    public Map<Referral, List<Error>> validate(ReferralBulkRequest request) {
        log.info("validating project beneficiary id, project staff id");
        Map<Referral, List<Error>> errorDetailsMap = new HashMap<>();
        List<Referral> entities = request.getReferrals();
        Map<String, List<Referral>> tenantIdReferralMap = entities.stream().collect(Collectors.groupingBy(Referral::getTenantId));
        List<String> tenantIds = new ArrayList<>(tenantIdReferralMap.keySet());
        tenantIds.forEach(tenantId -> {
            List<Referral> referralList = tenantIdReferralMap.get(tenantId);
            if (!referralList.isEmpty()) {
                List<ProjectBeneficiary> existingProjectBeneficiaries = null;
                List<ProjectStaff> existingProjectStaffList = null;
                final List<String> projectBeneficiaryIdList = new ArrayList<>();
                final List<String> projectBeneficiaryClientReferenceIdList = new ArrayList<>();
                final List<String> projectStaffIdList = new ArrayList<>();
                try {
                    referralList.forEach(referral -> {
                        addIgnoreNull(projectBeneficiaryIdList, referral.getProjectBeneficiaryId());
                        addIgnoreNull(projectBeneficiaryClientReferenceIdList, referral.getProjectBeneficiaryClientReferenceId());
                        addIgnoreNull(projectStaffIdList, referral.getReferredById());
                        if(referral.getReferredToType().equals(PROJECT_STAFF)){
                            addIgnoreNull(projectStaffIdList, referral.getReferredToId());
                        }
                    });
                    ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder()
                            .id(projectBeneficiaryIdList.isEmpty() ? null : projectBeneficiaryIdList)
                            .clientReferenceId(projectBeneficiaryClientReferenceIdList.isEmpty() ? null : projectBeneficiaryClientReferenceIdList)
                            .build();
                    BeneficiaryBulkResponse beneficiaryBulkResponse = serviceRequestClient.fetchResult(
                            new StringBuilder(referralManagementConfiguration.getProjectHost()
                                    + referralManagementConfiguration.getProjectBeneficiarySearchUrl()
                                    +"?limit=" + entities.size()
                                    + "&offset=0&tenantId=" + tenantId),
                            BeneficiarySearchRequest.builder().requestInfo(request.getRequestInfo()).projectBeneficiary(projectBeneficiarySearch).build(),
                            BeneficiaryBulkResponse.class
                    );
                    existingProjectBeneficiaries = beneficiaryBulkResponse.getProjectBeneficiaries();
                    ProjectStaffSearch projectStaffSearch = ProjectStaffSearch.builder()
                            .id(projectStaffIdList.isEmpty() ? null : projectStaffIdList)
                            .build();
                    ProjectStaffBulkResponse projectStaffBulkResponse = serviceRequestClient.fetchResult(
                            new StringBuilder(referralManagementConfiguration.getProjectHost()
                                    + referralManagementConfiguration.getProjectStaffSearchUrl()
                                    +"?limit=" + entities.size()
                                    + "&offset=0&tenantId=" + tenantId),
                            ProjectStaffSearchRequest.builder().requestInfo(request.getRequestInfo()).projectStaff(projectStaffSearch).build(),
                            ProjectStaffBulkResponse.class
                    );
                    existingProjectStaffList = projectStaffBulkResponse.getProjectStaff();

                } catch (QueryBuilderException e) {
                    if(existingProjectBeneficiaries == null) existingProjectBeneficiaries = Collections.emptyList();
                    existingProjectStaffList = Collections.emptyList();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                final List<String> existingProjectBeneficiaryIds = new ArrayList<>();
                final List<String> existingProjectBeneficiaryClientReferenceIds = new ArrayList<>();
                existingProjectBeneficiaries.forEach(projectBeneficiary -> {
                    existingProjectBeneficiaryIds.add(projectBeneficiary.getId());
                    existingProjectBeneficiaryClientReferenceIds.add(projectBeneficiary.getClientReferenceId());
                });
                final List<String> existingProjectStaffIds = new ArrayList<>();
                existingProjectStaffList.forEach(projectStaff -> existingProjectStaffIds.add(projectStaff.getId()));
                List<Referral> invalidEntities = entities.stream().filter(notHavingErrors()).filter(entity ->
                                !existingProjectStaffIds.contains(entity.getReferredById())
                                        && !existingProjectBeneficiaryIds.contains(entity.getProjectBeneficiaryId())
                                        && !existingProjectBeneficiaryClientReferenceIds.contains(entity.getProjectBeneficiaryClientReferenceId())
                                        && (!entity.getReferredToType().equals(PROJECT_STAFF) || !existingProjectStaffIds.contains(entity.getReferredToId()))
                        ).collect(Collectors.toList());
                invalidEntities.forEach(referral -> {
                    Error error = getErrorForNonExistentEntity();
                    populateErrorDetails(referral, error, errorDetailsMap);
                });

            }
        });
        return errorDetailsMap;
    }

    private void addIgnoreNull(List<String> list, String item) {
        if(Objects.nonNull(item)) list.add(item);
    }
}
