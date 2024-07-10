package org.egov.project.service;

import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.TrackActivityTaskRepository;
import org.egov.project.service.enrichment.ProjectTaskEnrichmentService;

public class TrackActivityTaskService {

    private final IdGenService idGenService;

    private final TrackActivityTaskRepository closedHouseholdTaskRepository;

    private final ServiceRequestClient serviceRequestClient;

    private final TrackActivityTaskService trackActivityTaskService;

    private final ProjectConfiguration projectConfiguration;

    private final ProjectTaskEnrichmentService enrichmentService;

    public TrackActivityTaskService(IdGenService idGenService, TrackActivityTaskRepository closedHouseholdTaskRepository, ServiceRequestClient serviceRequestClient, TrackActivityTaskService trackActivityTaskService, ProjectConfiguration projectConfiguration, ProjectTaskEnrichmentService enrichmentService) {
        this.idGenService = idGenService;
        this.closedHouseholdTaskRepository = closedHouseholdTaskRepository;
        this.serviceRequestClient = serviceRequestClient;
        this.trackActivityTaskService = trackActivityTaskService;
        this.projectConfiguration = projectConfiguration;
        this.enrichmentService = enrichmentService;
    }
}
