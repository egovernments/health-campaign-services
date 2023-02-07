package org.egov.project.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectBeneficiaryRepository;
import org.egov.project.web.models.BeneficiaryBulkRequest;
import org.egov.project.web.models.ProjectBeneficiary;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.enrichForDelete;
import static org.egov.common.utils.CommonUtils.enrichForUpdate;
import static org.egov.common.utils.CommonUtils.enrichIdsFromExistingEntities;
import static org.egov.common.utils.CommonUtils.getIdFieldName;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getIdToObjMap;
import static org.egov.common.utils.CommonUtils.getTenantId;

@Service
@Slf4j
public class ProjectBeneficiaryEnrichmentService {

    private final IdGenService idGenService;

    private final ProjectConfiguration projectConfiguration;

    private final ProjectBeneficiaryRepository projectBeneficiaryRepository;

    public ProjectBeneficiaryEnrichmentService(IdGenService idGenService,
                                               ProjectConfiguration projectConfiguration,
                                               ProjectBeneficiaryRepository projectBeneficiaryRepository) {
        this.idGenService = idGenService;
        this.projectConfiguration = projectConfiguration;
        this.projectBeneficiaryRepository = projectBeneficiaryRepository;
    }


    public void create(List<ProjectBeneficiary> validProjectBeneficiaries,
                       BeneficiaryBulkRequest beneficiaryRequest) throws Exception {
        String tenantId = getTenantId(validProjectBeneficiaries);

        log.info("Generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(beneficiaryRequest.getRequestInfo(),
                tenantId,
                projectConfiguration.getProjectBeneficiaryIdFormat(),
                "",
                validProjectBeneficiaries.size());
        log.info("IDs generated");

        enrichForCreate(validProjectBeneficiaries, idList, beneficiaryRequest.getRequestInfo());
        log.info("Enrichment done");
    }

    public void update(List<ProjectBeneficiary> validProjectBeneficiaries,
                       BeneficiaryBulkRequest beneficiaryRequest) {
        Method idMethod = getIdMethod(validProjectBeneficiaries);
        Map<String, ProjectBeneficiary> projectBeneficiaryMap  = getIdToObjMap(validProjectBeneficiaries, idMethod);
        List<String> projectBeneficiaryIds = new ArrayList<>(projectBeneficiaryMap.keySet());
        List<ProjectBeneficiary> existingProjectBeneficiaryIds = projectBeneficiaryRepository.findById(
                projectBeneficiaryIds,
                false,
                getIdFieldName(idMethod)
        );

        log.info("Updating Ids from existing entities");
        enrichIdsFromExistingEntities(projectBeneficiaryMap, existingProjectBeneficiaryIds, idMethod);

        log.info("Updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(projectBeneficiaryMap, existingProjectBeneficiaryIds, beneficiaryRequest, idMethod);
    }

    public void delete(List<ProjectBeneficiary> validProjectBeneficiaries,
                       BeneficiaryBulkRequest beneficiaryRequest) {
        enrichForDelete(validProjectBeneficiaries, beneficiaryRequest.getRequestInfo(), true);
    }
}
