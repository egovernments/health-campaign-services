package org.egov.project.validator.beneficiary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.Error;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.ProjectBeneficiarySearch;
import org.egov.common.validator.Validator;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForUniqueEntity;

/**
 * Validator class for checking the existence of entities with the given client reference IDs.
 * This validator checks if the provided ProjectBeneficiary entities already exist in the database based on their client reference IDs.
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
     */
    public PbExistentEntityValidator(ProjectBeneficiaryRepository projectBeneficiaryRepository) {
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
    }

    /**
     * Validates the existence of entities with the given client reference IDs.
     *
     * @param request The bulk request containing ProjectBeneficiary entities.
     * @return A map containing ProjectBeneficiary entities and their associated error details.
     */
    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest request) {
        // Map to hold ProjectBeneficiary entities and their error details
        Map<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();
        // Get the list of ProjectBeneficiary entities from the request
        List<ProjectBeneficiary> entities = request.getProjectBeneficiaries();
        // Extract client reference IDs from ProjectBeneficiary entities without errors
        List<String> clientReferenceIdList = entities.stream()
                .filter(notHavingErrors())
                .map(ProjectBeneficiary::getClientReferenceId)
                .collect(Collectors.toList());
        // Create a search object for querying entities by client reference IDs
        ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder()
                .clientReferenceId(clientReferenceIdList)
                .build();
        Map<String, ProjectBeneficiary> map = entities.stream()
                .filter(individual -> StringUtils.isEmpty(individual.getClientReferenceId()))
                .collect(Collectors.toMap(entity -> entity.getClientReferenceId(), entity -> entity));
        // Check if the client reference ID list is not empty
        if (!CollectionUtils.isEmpty(clientReferenceIdList)) {
            // Query the repository to find existing entities by client reference IDs
            List<String> existingClientReferenceIds =
                    projectBeneficiaryRepository.validateClientReferenceIdsFromDB(clientReferenceIdList, Boolean.TRUE);
            // For each existing entity, populate error details for uniqueness
            existingClientReferenceIds.forEach(clientReferenceId -> {
                Error error = getErrorForUniqueEntity();
                populateErrorDetails(map.get(clientReferenceId), error, errorDetailsMap);
            });
        }
        return errorDetailsMap;
    }

}
