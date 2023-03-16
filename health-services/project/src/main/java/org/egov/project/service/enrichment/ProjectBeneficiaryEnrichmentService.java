package org.egov.project.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.BeneficiaryBulkRequest;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ProjectBeneficiaryRepository;
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
        log.info("starting the enrichment for create project beneficiaries");

        log.info("get tenant id");
        String tenantId = getTenantId(validProjectBeneficiaries);

        log.info("generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(beneficiaryRequest.getRequestInfo(),
                tenantId,
                projectConfiguration.getProjectBeneficiaryIdFormat(),
                "",
                validProjectBeneficiaries.size());
        log.info("ids generated");

        enrichForCreate(validProjectBeneficiaries, idList, beneficiaryRequest.getRequestInfo());
        log.info("enrichment done");
    }

    public void update(List<ProjectBeneficiary> validProjectBeneficiaries,
                       BeneficiaryBulkRequest beneficiaryRequest) {
        log.info("starting the enrichment for update project beneficiaries");
        Method idMethod = getIdMethod(validProjectBeneficiaries);
        Map<String, ProjectBeneficiary> projectBeneficiaryMap  = getIdToObjMap(validProjectBeneficiaries, idMethod);
        List<String> projectBeneficiaryIds = new ArrayList<>(projectBeneficiaryMap.keySet());
        List<ProjectBeneficiary> existingProjectBeneficiaryIds = projectBeneficiaryRepository.findById(
                projectBeneficiaryIds,
                false,
                getIdFieldName(idMethod)
        );

        log.info("updating Ids from existing entities");
        enrichIdsFromExistingEntities(projectBeneficiaryMap, existingProjectBeneficiaryIds, idMethod);

        log.info("updating lastModifiedTime and lastModifiedBy");
        enrichForUpdate(projectBeneficiaryMap, existingProjectBeneficiaryIds, beneficiaryRequest, idMethod);

        log.info("enrichment done");
    }

    public void delete(List<ProjectBeneficiary> validProjectBeneficiaries,
                       BeneficiaryBulkRequest beneficiaryRequest) {
        log.info("starting the enrichment for delete project beneficiaries");
        enrichForDelete(validProjectBeneficiaries, beneficiaryRequest.getRequestInfo(), true);
        log.info("enrichment done");
    }
}
