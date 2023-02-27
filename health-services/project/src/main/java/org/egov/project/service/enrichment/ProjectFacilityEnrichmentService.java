package org.egov.project.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.web.models.ProjectFacility;
import org.egov.project.web.models.ProjectFacilityBulkRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static org.egov.common.utils.CommonUtils.*;

@Service
@Slf4j
public class ProjectFacilityEnrichmentService {

    private final IdGenService idGenService;

    private final ProjectConfiguration projectConfiguration;

    public ProjectFacilityEnrichmentService(IdGenService idGenService, ProjectConfiguration projectConfiguration) {
        this.idGenService = idGenService;
        this.projectConfiguration = projectConfiguration;
    }

    public void create(List<ProjectFacility> entities, ProjectFacilityBulkRequest request) throws Exception {
        log.info("starting the enrichment for create project facility");

        log.info("generating IDs using IdGenService");
        List<String> idList = idGenService.getIdList(request.getRequestInfo(),
                getTenantId(entities),
                projectConfiguration.getProjectFacilityIdFormat(), "", entities.size());

        enrichForCreate(entities, idList, request.getRequestInfo());
        log.info("enrichment done");
    }

    public void update(List<ProjectFacility> entities, ProjectFacilityBulkRequest request) {
        log.info("starting the enrichment for update project facility");
        Map<String, ProjectFacility> projectFacilityMap = getIdToObjMap(entities);
        enrichForUpdate(projectFacilityMap, entities, request);
        log.info("enrichment done");
    }

    public void delete(List<ProjectFacility> entities, ProjectFacilityBulkRequest request) {
        log.info("starting the enrichment for delete project facility");
        enrichForDelete(entities, request.getRequestInfo(), true);
        log.info("enrichment done");
    }
}
