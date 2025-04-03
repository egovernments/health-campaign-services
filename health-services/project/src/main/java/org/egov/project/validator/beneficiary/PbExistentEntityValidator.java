package org.egov.project.validator.beneficiary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.ProjectBeneficiarySearch;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of ProjectBeneficiary entities with the given client reference IDs.
 * This validator checks if the provided ProjectBeneficiary entities already exist in the database based on their client reference IDs.
 *
 * The validation ensures that each ProjectBeneficiary entity in the bulk request has a unique client reference ID,
 * and if an entity with the same client reference ID already exists, an error is recorded.
 *
 * @author kanishq-egov
 */
@Component
@Order(value = 1)
@Slf4j
public class PbExistentEntityValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {

    private final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    /**
     * Constructor to initialize the ProjectBeneficiaryRepository dependency.
     *
     * @param projectBeneficiaryRepository The repository for ProjectBeneficiary entities.
     *                                      It is used to query the database to check if entities with given client reference IDs exist.
     */
    public PbExistentEntityValidator(ProjectBeneficiaryRepository projectBeneficiaryRepository) {
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
    }

    /**
     * Validates the existence of ProjectBeneficiary entities with the given client reference IDs.
     *
     * This method checks if any of the ProjectBeneficiary entities in the provided bulk request already exist in the database.
     * If an entity with a client reference ID already exists, an error is added to the map with the entity.
     *
     * @param request The bulk request containing ProjectBeneficiary entities to be validated.
     * @return A map where the key is a ProjectBeneficiary entity and the value is a list of associated Error details.
     *         The map contains entries only for entities that have errors (i.e., those whose client reference IDs already exist in the database).
     */
    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest request) {
        // Initialize a map to hold ProjectBeneficiary entities and their associated error details.
        Map<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();

        // Extract the list of ProjectBeneficiary entities from the request.
        List<ProjectBeneficiary> entities = request.getProjectBeneficiaries();

        // Extract the client reference IDs from ProjectBeneficiary entities that do not have existing errors.
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors()) // Filter out entities that already have errors.
                .map(ProjectBeneficiary::getClientReferenceId) // Map to extract client reference IDs.
                .collect(Collectors.toList()); // Collect the IDs into a list.

        // Create a map of client reference ID to ProjectBeneficiary entity for quick lookup.
        Map<String, ProjectBeneficiary> map = entities.stream()
                .filter(entity -> StringUtils.hasText(entity.getClientReferenceId())) // Ensure client reference ID is not empty.
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity)); // Collect to a map.

        // Create a search object to query entities by client reference IDs.
        ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder()
                .clientReferenceId(clientReferenceIdList) // Set the client reference IDs for the search.
                .build();

        // Check if the client reference ID list is not empty before querying the database.
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities with the given client reference IDs.
            List<String> existingClientReferenceIds =
                    projectBeneficiaryRepository.validateClientReferenceIdsFromDB(clientReferenceIdList, Boolean.TRUE);

            // For each existing client reference ID, populate error details for the corresponding ProjectBeneficiary entity.
            existingClientReferenceIds.forEach(clientReferenceId -> {
                // Get a predefined error object for unique entity validation.
                Error error = getErrorForUniqueEntity();
                // Populate error details for the individual entity associated with the client reference ID.
                populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
            });
        }

        // Return the map containing ProjectBeneficiary entities and their associated error details.
        return errorDetailsMap;
    }

}
