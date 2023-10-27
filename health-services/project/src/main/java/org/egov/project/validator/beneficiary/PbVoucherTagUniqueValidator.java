package org.egov.project.validator.beneficiary;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.service.ProjectBeneficiaryService;
import org.egov.project.web.models.ProjectBeneficiarySearch;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;

/**
 * This class, PbVoucherTagUniqueValidator, is a Spring component that serves as a validator for ensuring the uniqueness
 * of voucher tags within a list of project beneficiaries. It implements the Validator interface, which allows it to
 * validate a BeneficiaryBulkRequest containing a list of ProjectBeneficiary objects. Any duplicate voucher tags within
 * the list result in error details being populated for the respective ProjectBeneficiary objects.
 */
@Component
@Order(value = 2)
@Slf4j
public class PbVoucherTagUniqueValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {
    final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Autowired
    public PbVoucherTagUniqueValidator(ProjectBeneficiaryRepository projectBeneficiaryRepository) {
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
    }

    /**
     *
     * @param beneficiaryBulkRequest
     * @return
     */
    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest beneficiaryBulkRequest) {
        log.info("validating unique tag");

        // Create a map to store error details for each ProjectBeneficiary
        Map<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();

        // Filter valid project beneficiaries (those without errors)
        List<ProjectBeneficiary> validProjectBeneficiaries = beneficiaryBulkRequest.getProjectBeneficiaries()
                .stream().filter(notHavingErrors()).collect(Collectors.toList());

        if(!validProjectBeneficiaries.isEmpty()) {
            // Get a list of invalid voucher tags
            List<String> invalidVoucherTags = getInvalidVoucherTags(validProjectBeneficiaries, beneficiaryBulkRequest);

            // Validate and populate errors for invalid voucher tags
            validateAndPopulateErrors(validProjectBeneficiaries, invalidVoucherTags, errorDetailsMap);
        }

        return errorDetailsMap;
    }

    // Helper method to validate and populate errors
    private void validateAndPopulateErrors(List<ProjectBeneficiary> validProjectBeneficiaries, List<String> invalidVoucherTags, Map<ProjectBeneficiary, List<Error>> errorDetailsMap) {

        // Filter project beneficiaries that are valid and have invalid voucher tags
        List<ProjectBeneficiary> invalidEntities = validProjectBeneficiaries.stream().filter(notHavingErrors())
                .filter(entity -> invalidVoucherTags.contains(entity.getVoucherTag()))
                .collect(Collectors.toList());

        // For each invalid entity, create an error and populate error details
        invalidEntities.forEach(projectBeneficiary -> {
            Error error = getErrorForNonExistentEntity();
            populateErrorDetails(projectBeneficiary, error, errorDetailsMap);
        });
    }

    // Helper method to get invalid voucher tags
    private List<String> getInvalidVoucherTags(List<ProjectBeneficiary> validProjectBeneficiaries, BeneficiaryBulkRequest beneficiaryBulkRequest) {
        // Extract voucher tags from valid project beneficiaries
        List<String> voucherTags = validProjectBeneficiaries.stream()
                .filter(Objects::nonNull)
                .map(ProjectBeneficiary::getVoucherTag)
                .collect(Collectors.toList());

        // Create a list to store invalid voucher tags
        List<String> invalidVoucherTags;

        // Build a search request to find existing voucher tags
        ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder().voucherTag(voucherTags).build();
        try {
            log.info("Fetching project beneficiary based on voucher tags");
            List<String> existingVoucherTags = projectBeneficiaryRepository.find(
                        projectBeneficiarySearch,
                        voucherTags.size(), 0, validProjectBeneficiaries.get(0).getTenantId(), null, false
                    )
                    .stream()
                    .map(ProjectBeneficiary::getVoucherTag)
                    .collect(Collectors.toList());

            // Remove existing voucher tags to get invalid ones
            voucherTags.removeAll(existingVoucherTags);
        } catch (Exception e) {
            log.error("Exception while fetching project beneficiary service : ", e);
            throw new CustomException("PROJECT_BENEFICIARY_VOUCHER_TAG_SEARCH_FAILED","Error occurred while fetching project beneficiary based on voucher tags. "+e);
        }

        // Copy the invalid voucher tags to the result list
        invalidVoucherTags = new ArrayList<>(voucherTags);
        return invalidVoucherTags;
    }
}
