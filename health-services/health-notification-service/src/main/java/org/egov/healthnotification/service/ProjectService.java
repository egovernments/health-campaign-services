package org.egov.healthnotification.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.project.*;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.util.RequestInfoUtil;
import org.egov.healthnotification.web.models.ProjectSearchRequestWrapper;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Service for interacting with the Project Service.
 * Used to fetch project details, project type, and project beneficiary information.
 */
@Service
@Slf4j
public class ProjectService {

    private final ServiceRequestClient serviceRequestClient;
    private final HealthNotificationProperties properties;

    @Autowired
    public ProjectService(ServiceRequestClient serviceRequestClient,
                          HealthNotificationProperties properties) {
        this.serviceRequestClient = serviceRequestClient;
        this.properties = properties;
    }

    /**
     * Searches for a project beneficiary by ID.
     *
     * @param beneficiaryId The project beneficiary ID
     * @param tenantId The tenant ID
     * @return ProjectBeneficiary object
     */
    public ProjectBeneficiary searchProjectBeneficiaryById(String beneficiaryId, String tenantId) {
        log.info("Searching project beneficiary by ID: {} for tenant: {}", beneficiaryId, tenantId);

        BeneficiarySearchRequest request = BeneficiarySearchRequest.builder()
                .requestInfo(RequestInfoUtil.buildSystemRequestInfo())
                .projectBeneficiary(ProjectBeneficiarySearch.builder()
                        .id(Collections.singletonList(beneficiaryId))
                        .build())
                .build();

        try {
            StringBuilder uri = new StringBuilder();
            uri.append(properties.getProjectHost())
                    .append(properties.getProjectBeneficiarySearchUrl())
                    .append("?limit=1")
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);

            BeneficiaryBulkResponse response = serviceRequestClient.fetchResult(
                    uri,
                    request,
                    BeneficiaryBulkResponse.class);

            if (response != null && response.getProjectBeneficiaries() != null
                    && !response.getProjectBeneficiaries().isEmpty()) {
                ProjectBeneficiary projectBeneficiary = response.getProjectBeneficiaries().get(0);
                log.info("Successfully fetched project beneficiary: {}", beneficiaryId);
                return projectBeneficiary;
            }

            log.error("Project beneficiary not found for id: {}", beneficiaryId);
            throw new CustomException("PROJECT_BENEFICIARY_NOT_FOUND",
                    String.format("Project beneficiary not found for id: %s, tenantId: %s", beneficiaryId, tenantId));

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching project beneficiary: {}", beneficiaryId, e);
            throw new CustomException("PROJECT_BENEFICIARY_FETCH_ERROR",
                    "Error while fetching project beneficiary details for id: " + beneficiaryId);
        }
    }

    /**
     * Searches for a project beneficiary by client reference ID.
     * The client reference ID corresponds to the household or individual ID.
     *
     * @param clientReferenceId The project beneficiary client reference ID
     * @param tenantId The tenant ID
     * @return ProjectBeneficiary object
     */
    public ProjectBeneficiary searchProjectBeneficiaryByClientRefId(String clientReferenceId, String tenantId) {
        log.info("Searching project beneficiary by clientReferenceId: {} for tenant: {}",
                clientReferenceId, tenantId);

        BeneficiarySearchRequest request = BeneficiarySearchRequest.builder()
                .requestInfo(RequestInfoUtil.buildSystemRequestInfo())
                .projectBeneficiary(ProjectBeneficiarySearch.builder()
                        .clientReferenceId(Collections.singletonList(clientReferenceId))
                        .build())
                .build();

        try {
            StringBuilder uri = new StringBuilder();
            uri.append(properties.getProjectHost())
                    .append(properties.getProjectBeneficiarySearchUrl())
                    .append("?limit=1000")
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);

            BeneficiaryBulkResponse response = serviceRequestClient.fetchResult(
                    uri,
                    request,
                    BeneficiaryBulkResponse.class);

            if (response != null && response.getProjectBeneficiaries() != null
                    && !response.getProjectBeneficiaries().isEmpty()) {
                ProjectBeneficiary projectBeneficiary = response.getProjectBeneficiaries().get(0);
                log.info("Successfully fetched project beneficiary: {}, beneficiaryClientRefId: {}",
                        projectBeneficiary.getId(), projectBeneficiary.getBeneficiaryClientReferenceId());
                return projectBeneficiary;
            }

            log.error("Project beneficiary not found for clientRefId: {}", clientReferenceId);
            throw new CustomException("PROJECT_BENEFICIARY_NOT_FOUND",
                    String.format("Project beneficiary not found for clientReferenceId: %s, tenantId: %s",
                            clientReferenceId, tenantId));

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching project beneficiary by clientReferenceId: {}", clientReferenceId, e);
            throw new CustomException("PROJECT_BENEFICIARY_FETCH_ERROR",
                    "Error while fetching project beneficiary details for clientReferenceId: " + clientReferenceId);
        }
    }

    /**
     * Gets the beneficiary client reference ID (individual/household ID).
     *
     * @param beneficiary The ProjectBeneficiary object
     * @return The beneficiary client reference ID
     */
    public String getBeneficiaryClientReferenceId(ProjectBeneficiary beneficiary) {
        if (beneficiary == null) {
            return null;
        }
        return beneficiary.getBeneficiaryClientReferenceId();
    }

    /**
     * Searches for a project by ID.
     *
     * @param projectId The project ID to search for
     * @param tenantId The tenant ID
     * @return Project object
     */
    public Project searchProjectById(String projectId, String tenantId) {
        log.info("Searching project by ID: {} for tenant: {}", projectId, tenantId);

        Project searchCriteria = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .build();

        ProjectSearchRequestWrapper request = ProjectSearchRequestWrapper.builder()
                .requestInfo(RequestInfoUtil.buildSystemRequestInfo())
                .projects(Collections.singletonList(searchCriteria))
                .apiOperation("SEARCH")
                .build();

        try {
            StringBuilder uri = new StringBuilder();
            uri.append(properties.getProjectHost())
                    .append(properties.getProjectSearchUrl())
                    .append("?limit=1")
                    .append("&offset=0")
                    .append("&tenantId=").append(tenantId);

            ProjectResponse response = serviceRequestClient.fetchResult(
                    uri,
                    request,
                    ProjectResponse.class);

            if (response != null && response.getProject() != null
                    && !response.getProject().isEmpty()) {
                Project project = response.getProject().get(0);
                log.info("Successfully fetched project: {}, projectType: {}",
                        projectId, project.getProjectType());
                return project;
            }

            log.error("Project not found for id: {}", projectId);
            throw new CustomException("PROJECT_NOT_FOUND",
                    String.format("Project not found for id: %s, tenantId: %s", projectId, tenantId));

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching project: {}", projectId, e);
            throw new CustomException("PROJECT_FETCH_ERROR",
                    "Error while fetching project details for id: " + projectId);
        }
    }

    /**
     * Gets the project type for a project.
     *
     * @param projectId The project ID
     * @param tenantId The tenant ID
     * @return The project type string, or null if not found
     */
    public String getProjectType(String projectId, String tenantId) {
        Project project = searchProjectById(projectId, tenantId);
        if (project == null) {
            return null;
        }
        return project.getProjectType();
    }
}
