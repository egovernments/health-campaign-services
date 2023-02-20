package org.egov.project.service.enrichment;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.util.ProjectServiceUtil;
import org.egov.project.web.models.Document;
import org.egov.project.web.models.Project;
import org.egov.project.web.models.ProjectRequest;
import org.egov.project.web.models.Target;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static org.egov.project.util.ProjectConstants.PROJECT_PARENT_HIERARCHY_SEPERATOR;

@Service
@Slf4j
public class ProjectEnrichment {

    @Autowired
    private ProjectServiceUtil projectServiceUtil;

    @Autowired
    private IdGenService idGenService;

    @Autowired
    private ProjectConfiguration config;

    /* Enrich Project on Create Request */
    public void enrichProjectOnCreate(ProjectRequest request, List<Project> parentProjects) {
        RequestInfo requestInfo = request.getRequestInfo();
        List<Project> projects = request.getProjects();

        String rootTenantId = projects.get(0).getTenantId().split("\\.")[0];

        //Get Project Ids from Idgen Service for Number of projects present in Projects
        List<String> projectNumbers = getIdList(requestInfo, rootTenantId
                , config.getIdgenProjectNumberName(), "", projects.size());

        for (int i = 0; i < projects.size(); i++) {

            if (projectNumbers != null && !projectNumbers.isEmpty()) {
                projects.get(i).setProjectNumber(projectNumbers.get(i));
                log.info("Project numbers set for projects");
            } else {
                log.error("Error occurred while generating project numbers from IdGen service");
                throw new CustomException("PROJECT_NUMBER_NOT_GENERATED","Error occurred while generating project numbers from IdGen service");
            }

            //Enrich Project id and audit details
            enrichProjectRequest(projects.get(i), null, requestInfo, parentProjects);
            log.info("Enriched project request with id and Audit details");

            //Enrich Address id and audit details
            enrichProjectAddress(projects.get(i), null, requestInfo);
            log.info("Enriched project Address with id and Audit details");

            //Enrich target id and audit details
            enrichProjectTarget(projects.get(i), null, requestInfo);
            log.info("Enriched target with id and Audit details");

            //Enrich document id and audit details
            enrichProjectDocument(projects.get(i), null, requestInfo);
            log.info("Enriched documents with id and Audit details");
        }

    }

    /* Enrich Project on Update Request */
    public void enrichProjectOnUpdate(ProjectRequest request, List<Project> projectsFromDB) {
        RequestInfo requestInfo = request.getRequestInfo();
        List<Project> projectsFromRequest = request.getProjects();

        for (Project project : projectsFromRequest) {
            String projectId = String.valueOf(project.getId());
            Project projectFromDB = projectsFromDB.stream().filter(p -> projectId.equals(String.valueOf(p.getId()))).findFirst().orElse(null);

            //Updating lastModifiedTime and lastModifiedBy for Project
            enrichProjectRequest(project, projectFromDB, requestInfo, null);
            log.info("Enriched project in update project request");

            //Add address if id is empty or update lastModifiedTime and lastModifiedBy if id exists
            enrichProjectAddress(project, projectFromDB, requestInfo);
            log.info("Enriched address in update project request");

            //Add new target if id is empty or update lastModifiedTime and lastModifiedBy if id exists
            enrichProjectTarget(project, projectFromDB, requestInfo);
            log.info("Enriched target in update project request");

            //Add new document if id is empty or update lastModifiedTime and lastModifiedBy if id exists
            enrichProjectDocument(project, projectFromDB, requestInfo);
            log.info("Enriched document in update project request");
        }

    }

    /* Enrich Project with id and audit details if project id is empty or if project already present, update last modified by and last modified time */
    private void enrichProjectRequest(Project projectRequest, Project projectFromDB, RequestInfo requestInfo, List<Project> parentProjects) {
        if (StringUtils.isBlank(projectRequest.getId())) {
            projectRequest.setId(UUID.randomUUID().toString());
            log.info("Project id set to " + projectRequest.getId());
            AuditDetails auditDetails = projectServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), null, true);
            projectRequest.setAuditDetails(auditDetails);
            if (parentProjects != null && StringUtils.isNotBlank(projectRequest.getParent())) {
                enrichProjectHierarchy(projectRequest, parentProjects);
            }
        } else if (projectFromDB != null) {
            projectRequest.setAuditDetails(projectFromDB.getAuditDetails());
            AuditDetails auditDetails = projectServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), projectFromDB.getAuditDetails(), false);
            projectRequest.setAuditDetails(auditDetails);
            log.info("Enriched project audit details for project " + projectRequest.getId());
        }
    }

    //Enrich Project with Parent Hierarchy. If parent Project hierarchy is not present then add parent locality at the beginning of project hierarchy, if present add Parent project's project hierarchy
    private void enrichProjectHierarchy(Project projectRequest, List<Project> parentProjects) {
        Project parentProject = parentProjects.stream().filter(p -> projectRequest.getParent().equals(p.getId())).findFirst().orElse(null);
        String parentProjectHierarchy = "";
        if (parentProject != null) {
            log.info("Parent project with id " + parentProject.getId() + " found for project id " + projectRequest.getId());
            if (StringUtils.isNotBlank(parentProject.getProjectHierarchy())) {
                log.info("Project hierarchy found for parent project " + parentProject.getId());
                parentProjectHierarchy = parentProject.getProjectHierarchy();
            } else {
                log.info("Project hierarchy is empty for project " + parentProject.getId());
                parentProjectHierarchy = parentProject.getId();
            }
        }
        projectRequest.setProjectHierarchy(parentProjectHierarchy + PROJECT_PARENT_HIERARCHY_SEPERATOR + projectRequest.getId());
        log.info("Project hierarchy set for project " + projectRequest.getId());
    }

    /* Enrich Address with id and audit details if address id is empty or if address already present, update last modified by and last modified time */
    private void enrichProjectAddress(Project projectFromRequest, Project projectFromDB, RequestInfo requestInfo) {
        if (projectFromRequest.getAddress() != null) {
            if (StringUtils.isBlank(projectFromRequest.getAddress().getId())) {
                projectFromRequest.getAddress().setId(UUID.randomUUID().toString());
                log.info("Added address with id " + projectFromRequest.getAddress().getId() + " for project " + projectFromRequest.getId());
            }
        }
    }

    /* Enrich Target with id and audit details if target id is empty or if target already present, update last modified by and last modified time */
    private void enrichProjectTarget(Project projectFromRequest, Project projectFromDB, RequestInfo requestInfo) {
        if (projectFromRequest.getTargets() != null) {
            for (Target target: projectFromRequest.getTargets()) {
                if (StringUtils.isBlank(target.getId())) {
                    target.setId(UUID.randomUUID().toString());
                    AuditDetails auditDetailsForAdd = projectServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), null, true);
                    target.setAuditDetails(auditDetailsForAdd);
                    log.info("Added target with id " + target.getId() + " for project " + projectFromRequest.getId());
                } else if (projectFromDB != null) {
                    String targetId = String.valueOf(target.getId());
                    Target targetFromDB = projectFromDB.getTargets().stream().filter(t -> targetId.equals(String.valueOf(t.getId()))).findFirst().orElse(null);
                    target.setAuditDetails(targetFromDB.getAuditDetails());
                    AuditDetails auditDetailsTarget = projectServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), targetFromDB.getAuditDetails(), false);
                    target.setAuditDetails(auditDetailsTarget);
                    log.info("Enriched target audit details for target " + targetId);
                }
            }
        }
    }

    /* Enrich Document with id and audit details if document id is empty or if document already present, update last modified by and last modified time */
    private void enrichProjectDocument(Project projectFromRequest, Project projectFromDB, RequestInfo requestInfo) {
        if (projectFromRequest.getDocuments() != null) {
            for (Document document: projectFromRequest.getDocuments()) {
                if (StringUtils.isBlank(document.getId())) {
                    document.setId(UUID.randomUUID().toString());
                    AuditDetails auditDetailsForAdd = projectServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), null, true);
                    document.setAuditDetails(auditDetailsForAdd);
                    log.info("Added document with id " + document.getId() + " for project " + projectFromRequest.getId());
                } else if (projectFromDB != null) {
                    String documentId = String.valueOf(document.getId());
                    Document documentFromDB = projectFromDB.getDocuments().stream().filter(d -> documentId.equals(String.valueOf(d.getId()))).findFirst().orElse(null);
                    document.setAuditDetails(documentFromDB.getAuditDetails());
                    AuditDetails auditDetailsDocument = projectServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), documentFromDB.getAuditDetails(), false);
                    document.setAuditDetails(auditDetailsDocument);
                    log.info("Enriched document audit details for document " + documentId);
                }
            }
        }
    }

    /* Get Project Number list from IdGen service */
    private List<String> getIdList(RequestInfo requestInfo, String tenantId, String idKey,
                                   String idformat, int count) {
        try {
            return idGenService.getIdList(requestInfo, tenantId, idKey, idformat, count);
        } catch (Exception exception) {
            log.error("error while calling id gen service", exception);
            throw new CustomException("IDGEN_ERROR",
                    String.format("error while calling id gen service for %s", idformat));
        }
    }
}
