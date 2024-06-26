package org.egov.project.validator.beneficiary;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static org.egov.common.utils.CommonUtils.checkNonExistentEntities;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;
import static org.egov.common.utils.CommonUtils.notHavingErrors;
import static org.egov.common.utils.CommonUtils.populateErrorDetails;
import static org.egov.common.utils.ValidatorUtils.getErrorForNonExistentEntity;
import static org.egov.project.Constants.GET_ID;

@Component
@Order(value = 4)
@Slf4j
public class PbNonExistentEntityValidator implements Validator<BeneficiaryBulkRequest, ProjectBeneficiary> {

    private final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    @Autowired
    public PbNonExistentEntityValidator(ProjectBeneficiaryRepository projectBeneficiaryRepository) {
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
    }


    @Override
    public Map<ProjectBeneficiary, List<Error>> validate(BeneficiaryBulkRequest request) {
        log.info("validating for existence of entity");
        Map<ProjectBeneficiary, List<Error>> errorDetailsMap = new HashMap<>();
        List<ProjectBeneficiary> projectBeneficiaries = request.getProjectBeneficiaries();
        Class<?> objClass = getObjClass(projectBeneficiaries);
        Method idMethod = getMethod(GET_ID, objClass);
        Map<String, ProjectBeneficiary> iMap = getIdToObjMap(projectBeneficiaries
                .stream().filter(notHavingErrors()).collect(Collectors.toList()), idMethod);
        // Lists to store IDs and client reference IDs
        List<String> idList = new ArrayList<>();
        List<String> clientReferenceIdList = new ArrayList<>();
        // Extract IDs and client reference IDs from Project Beneficiary entities
        projectBeneficiaries.forEach(entity -> {
            idList.add(entity.getId());
            clientReferenceIdList.add(entity.getClientReferenceId());
        });
        if (!iMap.isEmpty()) {
            ProjectBeneficiarySearch projectBeneficiarySearch = ProjectBeneficiarySearch.builder()
                    .clientReferenceId(clientReferenceIdList)
                    .id(idList)
                    .build();

            List<ProjectBeneficiary> existingProjectBeneficiaries;
            try {
                // Query the repository to find existing entities
                existingProjectBeneficiaries = projectBeneficiaryRepository.find(projectBeneficiarySearch, projectBeneficiaries.size(), 0,
                        projectBeneficiaries.get(0).getTenantId(), null, false).getResponse();
            } catch (Exception e) {
                // Handle query builder exception
                log.error("Search failed for ProjectBeneficiary with error: {}", e.getMessage(), e);
                throw new CustomException("PROJECT_BENEFICIARY_SEARCH_FAILED", "Search Failed for ProjectBeneficiary, " + e.getMessage()); 
            }

            List<ProjectBeneficiary> nonExistentIndividuals = checkNonExistentEntities(iMap,
                    existingProjectBeneficiaries, idMethod);
            nonExistentIndividuals.forEach(projectBeneficiary -> {
                Error error = getErrorForNonExistentEntity();
                populateErrorDetails(projectBeneficiary, error, errorDetailsMap);
            });
        }

        return errorDetailsMap;
    }
}
