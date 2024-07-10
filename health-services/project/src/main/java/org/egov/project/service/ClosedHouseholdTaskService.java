package org.egov.project.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.repository.ClosedHouseholdTaskRepository;
import org.egov.project.service.enrichment.ProjectTaskEnrichmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ClosedHouseholdTaskService {
    private final IdGenService idGenService;

    private final ClosedHouseholdTaskRepository closedHouseholdTaskRepository;

    private final ServiceRequestClient serviceRequestClient;

    private final ClosedHouseholdTaskService closedHouseholdTaskService;

    private final ProjectConfiguration projectConfiguration;

    private final ProjectTaskEnrichmentService enrichmentService;


    @Autowired
    public ClosedHouseholdTaskService(IdGenService idGenService, ClosedHouseholdTaskRepository closedHouseholdTaskRepository, ServiceRequestClient serviceRequestClient, ClosedHouseholdTaskService closedHouseholdTaskService, ProjectConfiguration projectConfiguration, ProjectTaskEnrichmentService enrichmentService) {
        this.idGenService = idGenService;
        this.closedHouseholdTaskRepository = closedHouseholdTaskRepository;
        this.serviceRequestClient = serviceRequestClient;
        this.closedHouseholdTaskService = closedHouseholdTaskService;
        this.projectConfiguration = projectConfiguration;
        this.enrichmentService = enrichmentService;
    }
}
