package org.egov.project.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.web.models.ProjectResource;
import org.egov.project.web.models.ProjectResourceBulkRequest;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.egov.common.utils.CommonUtils.enrichForCreate;
import static org.egov.common.utils.CommonUtils.getTenantId;

@Component
@Slf4j
public class ProjectResourceEnrichmentService {

    private final IdGenService idGenService;

    private final ProjectConfiguration projectConfiguration;

    public ProjectResourceEnrichmentService(IdGenService idGenService, ProjectConfiguration projectConfiguration) {
        this.idGenService = idGenService;
        this.projectConfiguration = projectConfiguration;
    }

    public void create(List<ProjectResource> entities, ProjectResourceBulkRequest request) throws Exception {
        log.info("starting the enrichment for create project staff");

        log.info("generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(request.getRequestInfo(),
                getTenantId(entities),
                projectConfiguration.getProjectResourceIdFormat(), "", entities.size());

        enrichForCreate(entities, idList, request.getRequestInfo());
        log.info("enrichment done");
    }

    public void update(List<ProjectResource> validEntities, ProjectResourceBulkRequest request) {

    }

    public void delete(List<ProjectResource> validEntities, ProjectResourceBulkRequest request) {

    }
}
