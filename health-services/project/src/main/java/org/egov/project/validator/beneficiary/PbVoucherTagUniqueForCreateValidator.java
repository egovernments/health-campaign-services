package org.egov.project.validator.beneficiary;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.web.models.ProjectBeneficiarySearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * This class, PbVoucherTagUniqueValidator, is a Spring component that serves as a validator for ensuring the uniqueness
 * of voucher tags within a list of project beneficiaries. It implements the Validator interface, which allows it to
 * validate a BeneficiaryBulkRequest containing a list of ProjectBeneficiary objects. Any duplicate voucher tags within
 * the list result in error details being populated for the respective ProjectBeneficiary objects.
 */
@Component
@Order(value = 2)
@Slf4j
public class PbVoucherTagUniqueForCreateValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {
    final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Autowired
    public PbVoucherTagUniqueForCreateValidator(ProjectBeneficiaryRepository projectBeneficiaryRepository) {
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
    }

    /**
     *
     * @param beneficiaryBulkRequest
     * @return
     */
    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest beneficiaryBulkRequest) {
        log.info("validating unique tag for create");

        // Create a map to store error details for each ProjectBeneficiary
        Map<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();

        // Filter valid project beneficiaries (those without errors)
        List<ProjectBeneficiary> validProjectBeneficiaries = beneficiaryBulkRequest.getProjectBeneficiaries()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());

        if(!validProjectBeneficiaries.isEmpty()) {
            // Get a list of invalid voucher tags
            List<ProjectBeneficiary> existingProjectBeneficiaries = getInvalidVoucherTags(validProjectBeneficiaries);

            // Validate and populate errors for invalid voucher tags
            if(!existingProjectBeneficiaries.isEmpty())
                validateAndPopulateErrors(validProjectBeneficiaries, existingProjectBeneficiaries, errorDetailsMap);
        }

        return errorDetailsMap;
    }

    // Helper method to validate and populate errors
    private void validateAndPopulateErrors(List<ProjectBeneficiary> validProjectBeneficiaries, List<ProjectBeneficiary> existingProjectBeneficiaries, Map<ProjectBeneficiary, List<Error>> errorDetailsMap) {
        List<String> existingVoucherTags = existingProjectBeneficiaries.stream().map(ProjectBeneficiary::getTag).collect(Collectors.toList());
        // Filter project beneficiaries that are valid and have invalid voucher tags
        List<ProjectBeneficiary> invalidEntities = validProjectBeneficiaries.stream().filter(notHavingErrors())
                .filter(entity -> existingVoucherTags.contains(entity.getTag()))
                .collect(Collectors.toList());

        // For each invalid entity, create an error and populate error details
        invalidEntities.forEach(projectBeneficiary -> {
            Error error = getErrorForUniqueEntity();
            populateErrorDetails(projectBeneficiary, error, errorDetailsMap);
        });
    }

    // Helper method to get invalid voucher tags
    private List<ProjectBeneficiary> getInvalidVoucherTags(List<ProjectBeneficiary> validProjectBeneficiaries) {
        // Extract voucher tags from valid project beneficiaries
        List<String> voucherTags = validProjectBeneficiaries.stream()
                .filter(Objects::nonNull)
                .map(ProjectBeneficiary::getTag)
                .collect(Collectors.toList());

        if(CollectionUtils.isEmpty(voucherTags))
            return new ArrayList<>();

        // Create a list to store existing project beneficiary with voucher tag
        List<ProjectBeneficiary> existingProjectBeneficiaries;

        // Build a search request to find existing voucher tags
        ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder().tag(voucherTags).build();

        try {
            log.info("Fetching project beneficiary based on voucher tags");
            existingProjectBeneficiaries = projectBeneficiaryRepository.find(
                    projectBeneficiarySearch,
                    voucherTags.size(), 0, validProjectBeneficiaries.get(0).getTenantId(), null, false
            );
        } catch (Exception e) {
            log.error("Exception while fetching project beneficiary service : ", e);
            throw new CustomException("PROJECT_BENEFICIARY_VOUCHER_TAG_SEARCH_FAILED","Error occurred while fetching project beneficiary based on voucher tags. "+e);
        }

        // return existing project beneficiaries
        return existingProjectBeneficiaries;
    }
}
