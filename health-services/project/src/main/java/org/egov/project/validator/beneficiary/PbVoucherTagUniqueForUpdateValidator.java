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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;

/**
 * @author kanishq-egov
 * This class, PbVoucherTagUniqueValidator, is a Spring component that serves as a validator for ensuring the uniqueness
 * of voucher tags within a list of project beneficiaries. It implements the Validator interface, which allows it to
 * validate a BeneficiaryBulkRequest containing a list of ProjectBeneficiary objects. Any duplicate voucher tags within
 * the list result in error details being populated for the respective ProjectBeneficiary objects.
 */
@Component
@Order(value = 2)
@Slf4j
public class PbVoucherTagUniqueForUpdateValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {
    final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Autowired
    public PbVoucherTagUniqueForUpdateValidator(ProjectBeneficiaryRepository projectBeneficiaryRepository) {
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
            // Get a list of existing ProjectBeneficiaries based on IDs
            List<ProjectBeneficiary> existingProjectBeneficiaries = getExistingProjectBeneficiaries(validProjectBeneficiaries);

            // Validate and populate errors for invalid voucher tags
            if(!CollectionUtils.isEmpty(existingProjectBeneficiaries))
                validateAndPopulateErrors(validProjectBeneficiaries, existingProjectBeneficiaries, errorDetailsMap);
        }

        return errorDetailsMap;
    }

    /**
     * This method retrieves existing ProjectBeneficiary entities based on their IDs.
     *
     * @param validProjectBeneficiaries List of valid ProjectBeneficiary entities.
     * @return A list of existing ProjectBeneficiary entities.
     */
    private List<ProjectBeneficiary> getExistingProjectBeneficiaries(List<ProjectBeneficiary> validProjectBeneficiaries) {
        List<ProjectBeneficiary> existingProjectBeneficiaries = null;

        // Build a search request to find existing voucher tags
        ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder()
                .id(validProjectBeneficiaries.stream().map(ProjectBeneficiary::getId).collect(Collectors.toList()))
                .build();

        try {
            log.info("Fetching project beneficiary based on voucher tags");
            existingProjectBeneficiaries = projectBeneficiaryRepository.find(
                    projectBeneficiarySearch,
                    validProjectBeneficiaries.size(), 0, validProjectBeneficiaries.get(0).getTenantId(), null, false
            );
        } catch (Exception e) {
            log.error("Exception while fetching project beneficiary service : ", e);
            throw new CustomException("PROJECT_BENEFICIARY_SEARCH_FAILED","Error occurred while fetching project beneficiary based on ids. "+e);
        }
        return existingProjectBeneficiaries;
    }

    /**
     * This method validates and populates errors for ProjectBeneficiary entities with duplicate voucher tags.
     *
     * @param validProjectBeneficiaries     List of valid ProjectBeneficiary entities.
     * @param existingProjectBeneficiaries  List of existing ProjectBeneficiary entities based on IDs.
     * @param errorDetailsMap               A map to store error details for duplicate voucher tags.
     */
    private void validateAndPopulateErrors(List<ProjectBeneficiary> validProjectBeneficiaries, List<ProjectBeneficiary> existingProjectBeneficiaries, Map<ProjectBeneficiary, List<Error>> errorDetailsMap) {
        Map<String, ProjectBeneficiary> existingProjectBeneficiaryMap = existingProjectBeneficiaries.stream().collect(Collectors.toMap(ProjectBeneficiary::getId, projectBeneficiary -> projectBeneficiary));
        // Filter project beneficiaries that are valid and have invalid voucher tags
        List<ProjectBeneficiary> invalidEntities = validProjectBeneficiaries.stream().filter(notHavingErrors())
                .filter(entity -> !existingProjectBeneficiaryMap.containsKey(entity.getId()))
                .collect(Collectors.toList());

        populateErrors(invalidEntities, errorDetailsMap);

        Map<String, ProjectBeneficiary> existingProjectBeneficiaryVoucherTagMap = existingProjectBeneficiaries.stream().filter(projectBeneficiary -> projectBeneficiary.getTag() != null).collect(Collectors.toMap(ProjectBeneficiary::getTag, projectBeneficiary -> projectBeneficiary));
        invalidEntities = validProjectBeneficiaries.stream()
                .filter(notHavingErrors())
                .filter(projectBeneficiary -> isUpdated(projectBeneficiary, existingProjectBeneficiaryMap))
                .filter(projectBeneficiary -> isInvalid(projectBeneficiary, existingProjectBeneficiaryVoucherTagMap))
                .collect(Collectors.toList());

        populateErrors(invalidEntities, errorDetailsMap);
    }

    /**
     * This method populates error details for a list of ProjectBeneficiary entities with duplicate voucher tags.
     *
     * @param invalidEntities   List of ProjectBeneficiary entities with duplicate voucher tags.
     * @param errorDetailsMap   A map to store error details.
     */
    private void populateErrors(List<ProjectBeneficiary> invalidEntities, Map<ProjectBeneficiary, List<Error>> errorDetailsMap) {
        // For each invalid entity, create an error and populate error details
        invalidEntities.forEach(projectBeneficiary -> {
            Error error = Error.builder().errorMessage("Project Beneficiary Tag Validation Failed").errorCode("INVALID_TAG").type(Error.ErrorType.NON_RECOVERABLE).exception(new CustomException("INVALID_TAG", "Project Beneficiary Tag Validation Failed")).build();
            populateErrorDetails(projectBeneficiary, error, errorDetailsMap);
        });
    }

    /**
     * This method checks if a ProjectBeneficiary entity is invalid based on its voucher tag.
     *
     * @param entity                              The ProjectBeneficiary entity to check.
     * @param existingProjectBeneficiaryVoucherTagMap The map containing existing ProjectBeneficiary entities
     *                                                indexed by their voucher tags.
     * @return true if the entity is invalid, false otherwise.
     */
    private boolean isInvalid(ProjectBeneficiary entity, Map<String, ProjectBeneficiary> existingProjectBeneficiaryVoucherTagMap) {
        String id = entity.getId();
        String tag = entity.getTag();

        // Check if an entity with the same ID exists in the map and has a different tag
        return existingProjectBeneficiaryVoucherTagMap.keySet().contains(tag) && !existingProjectBeneficiaryVoucherTagMap.get(tag).getId().equals(id);
    }

    /**
     * Checks if a ProjectBeneficiary entity is considered as updated based on its tag.
     *
     * @param entity                  The ProjectBeneficiary entity to check.
     * @param existingProjectBeneficiaryMap A map containing existing ProjectBeneficiary entities based on their IDs.
     * @return true if the entity is updated, false otherwise.
     */
    private boolean isUpdated(ProjectBeneficiary entity, Map<String, ProjectBeneficiary> existingProjectBeneficiaryMap) {
        String id = entity.getId();
        String tag = entity.getTag();

        // Retrieve the existing ProjectBeneficiary object to compare
        ProjectBeneficiary projectBeneficiaryFromSearch = existingProjectBeneficiaryMap.get(id);

        // check if existing ProjectBeneficiary Tag is null or not and if it is null whether it is updated or not
        if(projectBeneficiaryFromSearch.getTag() == null) return tag != null;

        // Check if the tag of the current entity is equal to the tag of the existing entity
        return ( !projectBeneficiaryFromSearch.getTag().equals(tag)
                    || ( tag != null && !tag.equals(projectBeneficiaryFromSearch.getTag()) ));
    }
}
