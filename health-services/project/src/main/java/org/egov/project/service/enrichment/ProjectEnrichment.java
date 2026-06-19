package org.egov.project.service.enrichment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.egov.common.contract.models.AuditDetails;
import java.util.Map;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.project.Document;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectRequest;
import org.egov.common.models.project.Target;
import org.egov.common.producer.Producer;
import org.egov.common.service.IdGenService;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.service.ProjectService;
import org.egov.project.util.ProjectServiceUtil;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getTenantId;
import static org.egov.project.util.ProjectConstants.PROJECT_PARENT_HIERARCHY_SEPERATOR;

@Service
@Slf4j
public class ProjectEnrichment {

    @Autowired
    private ProjectServiceUtil projectServiceUtil;

    @Autowired
    private Producer producer;
    @Autowired
    private ProjectConfiguration projectConfiguration;

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
            enrichProjectRequestOnCreate(projects.get(i), requestInfo, parentProjects);
            log.info("Enriched project request with id and Audit details");

            //Enrich Address id and audit details
            enrichProjectAddressOnCreate(projects.get(i), requestInfo);
            log.info("Enriched project Address with id and Audit details");

            //Enrich target id and audit details
            enrichProjectTargetOnCreate(projects.get(i), requestInfo);
            log.info("Enriched target with id and Audit details");

            //Enrich document id and audit details
            enrichProjectDocumentOnCreate(projects.get(i), requestInfo);
            log.info("Enriched documents with id and Audit details");

        }

    }

    /* Enrich Project on Update Request */
    public void enrichProjectOnUpdate(ProjectRequest request, Project project , Project  projectFromDB) {
        RequestInfo requestInfo = request.getRequestInfo();
                //Updating lastModifiedTime and lastModifiedBy for Project
                enrichProjectRequestOnUpdate(project, projectFromDB, requestInfo);
                log.info("Enriched project in update project request");

                //Add address if id is empty or update lastModifiedTime and lastModifiedBy if id exists
                enrichProjectAddressOnUpdate(project, projectFromDB, requestInfo);
                log.info("Enriched address in update project request");

                //Add new target if id is empty or update lastModifiedTime and lastModifiedBy if id exists
                enrichProjectTargetOnUpdate(project, projectFromDB, requestInfo);
                log.info("Enriched target in update project request");

                //Add new document if id is empty or update lastModifiedTime and lastModifiedBy if id exists
                enrichProjectDocumentOnUpdate(project, projectFromDB, requestInfo);
                log.info("Enriched document in update project request");
    }

    /* Enrich Project with id and audit details */
    private void enrichProjectRequestOnCreate(Project projectRequest, RequestInfo requestInfo, List<Project> parentProjects) {
        projectRequest.setId(UUID.randomUUID().toString());
        log.info("Project id set to " + projectRequest.getId());
        AuditDetails auditDetails = projectServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), null, true);
        projectRequest.setAuditDetails(auditDetails);
        if (parentProjects != null && StringUtils.isNotBlank(projectRequest.getParent())) {
            enrichProjectHierarchy(projectRequest, parentProjects);
        }
    }

    /* Enrich Project update request with last modified by and last modified time */
    public void enrichProjectRequestOnUpdate(Project projectRequest, Project projectFromDB, RequestInfo requestInfo) {
        projectRequest.setAuditDetails(projectFromDB.getAuditDetails());
        AuditDetails auditDetails = projectServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), projectFromDB.getAuditDetails(), false);
        projectRequest.setAuditDetails(auditDetails);
        log.info("Enriched project audit details for project " + projectRequest.getId());
    }
    public void enrichProjectCascadingDatesOnUpdate(Project project, Project projectFromDB)
    {
        // enrich project start and end dates along with ancestors and descendants
        enrichProjectStartAndEndDateOfBothAncestorsAndDescendantsIfFoundAccordingly(project,
            projectFromDB);
    }

    private void enrichProjectStartAndEndDateOfBothAncestorsAndDescendantsIfFoundAccordingly(
        Project projectRequest, Project projectFromDB) {
        long startDate = projectRequest.getStartDate();
        long endDate = projectRequest.getEndDate();

        /*
         * Update both cycle dates and project start and end dates of descendants
         */
        updateProjects(projectRequest, projectFromDB, startDate, endDate, true);

        /*
         * Update both cycle dates and project start and end dates of ancestors in a way like start date = min(current, existing)
         * and end date = max(current, existing)
         */
        updateProjects(projectRequest, projectFromDB, startDate, endDate, false);
    }


    private void updateProjects(Project projectRequest, Project projectFromDB, long startDate, long endDate, boolean isDescendant) {
        /*
         * Get the list of projects from the database that are either descendants or ancestors
         */
        List<Project> projectsFromDb = isDescendant ? projectFromDB.getDescendants() : projectFromDB.getAncestors();
        List<Project> modifiedProjectsFromDb = new ArrayList<>();

        if (projectsFromDb != null) {
            for (Project project : projectsFromDb) {
                /*
                 * Update the project dates based on whether it is a descendant or ancestor
                 */
                updateProjectDates(project, startDate, endDate, isDescendant);

                /*
                 * Update the project cycles based on the request and whether it is a descendant or ancestor
                 */
                updateCycles(project, projectRequest, isDescendant);

                /*
                 * Add the modified project to the list
                 */
                modifiedProjectsFromDb.add(project);
            }

            /*
             * Push the modified projects to Kafka
             */
            pushProjectsToKafka(modifiedProjectsFromDb);
        }
    }

    private void updateProjectDates(Project project, long startDate, long endDate, boolean isDescendant) {
        if (isDescendant) {
            /*
             * For descendant projects, directly set the start and end dates
             */
            project.setStartDate(startDate);
            project.setEndDate(endDate);
        } else {
            /*
             * For ancestor projects, set the start date to the minimum of the current and existing start dates,
             * and set the end date to the maximum of the current and existing end dates
             */
            project.setStartDate(Math.min(startDate, project.getStartDate()));
            project.setEndDate(Math.max(endDate, project.getEndDate()));
        }
    }


    private void updateCycles(Project descendantOrAncestor, Project projectRequest, boolean isDescendant) {
        if (descendantOrAncestor.getAdditionalDetails() == null) {
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();

        /*
         * Extract additional details from descendant and request projects
         */
        JsonNode descendantOrAncestorAdditionalDetails = objectMapper.valueToTree(
            descendantOrAncestor.getAdditionalDetails());
        JsonNode descendantOrAncestorProjectTypeNode = descendantOrAncestorAdditionalDetails.get("projectType");

        if (descendantOrAncestorProjectTypeNode != null) {
            JsonNode descendantOrAncestorCyclesNode = descendantOrAncestorProjectTypeNode.get("cycles");

            if (descendantOrAncestorCyclesNode != null && descendantOrAncestorCyclesNode.isArray()) {
                /*
                 * Extract cycles from the request project
                 */
                JsonNode requestAdditionalDetails = objectMapper.valueToTree(
                    projectRequest.getAdditionalDetails());
                JsonNode requestProjectTypeNode = requestAdditionalDetails.get("projectType");

                if (requestProjectTypeNode != null) {
                    JsonNode requestCyclesNode = requestProjectTypeNode.get("cycles");

                    if (requestCyclesNode != null && requestCyclesNode.isArray()) {
                        /*
                         * Iterate over descendant cycles and update as necessary
                         */
                        for (JsonNode descendantOrAncestorCycleNode : descendantOrAncestorCyclesNode) {
                            String descendantOrAncestorCycleId = descendantOrAncestorCycleNode.get("id").asText();

                            for (JsonNode requestCycleNode : requestCyclesNode) {
                                String requestCycleId = requestCycleNode.get("id").asText();

                                if (descendantOrAncestorCycleId.equals(requestCycleId)) {
                                    /*
                                     * Update start and end dates of descendant cycle node
                                     */
                                    long requestStartDate = requestCycleNode.get("startDate").asLong();
                                    long requestEndDate = requestCycleNode.get("endDate").asLong();
                                    long currentStartDate = descendantOrAncestorCycleNode.get("startDate").asLong();
                                    long currentEndDate = descendantOrAncestorCycleNode.get("endDate").asLong();
                                    if (isDescendant) {
                                        ((ObjectNode) descendantOrAncestorCycleNode).put("startDate", requestStartDate);
                                        ((ObjectNode) descendantOrAncestorCycleNode).put("endDate", requestEndDate);
                                    } else {
                                        ((ObjectNode) descendantOrAncestorCycleNode).put("startDate",
                                            Math.min(requestStartDate, currentStartDate));
                                        ((ObjectNode) descendantOrAncestorCycleNode).put("endDate",
                                            Math.max(requestEndDate, currentEndDate));
                                    }
                                    break; // Once updated, exit the loop for this descendantCycleNode
                                }
                            }
                        }

                        /*
                         * Convert updated additional details back to a map and set it on the descendant or ancestor project
                         */
                        Map<String, Object> updatedAdditionalDetails = objectMapper.convertValue(
                            descendantOrAncestorAdditionalDetails, new TypeReference<Map<String, Object>>() {});
                        descendantOrAncestor.setAdditionalDetails(updatedAdditionalDetails);
                    }
                }
            }
        }
    }


    private void pushProjectsToKafka(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            log.warn("No projects to push to Kafka");
            return;
        }

        String tenantId = getTenantId(projects);
        int batchSize = projectConfiguration.getKafkaBatchSize();
        
        log.info("Pushing {} projects to Kafka in batches of {}", projects.size(), batchSize);

        /*
         * Split projects into batches and push each batch separately
         */
        for (int i = 0; i < projects.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, projects.size());
            List<Project> batch = projects.subList(i, endIndex);
            
            /*
             * Create a ProjectRequest object with the current batch of projects
             */
            ProjectRequest projectRequest = ProjectRequest.builder()
                .projects(batch)
                .build();

            /*
             * Push the current batch to the Kafka topic for updating projects
             */
            log.info("Pushing batch {} with {} projects to Kafka", (i / batchSize) + 1, batch.size());
            
            producer.push(tenantId, projectConfiguration.getUpdateProjectTopic(), projectRequest);
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

    /* Enrich Address with id and audit details in project create request */
    private void enrichProjectAddressOnCreate(Project projectFromRequest, RequestInfo requestInfo) {
        if (projectFromRequest.getAddress() != null) {
            projectFromRequest.getAddress().setId(UUID.randomUUID().toString());
            log.info("Added address with id " + projectFromRequest.getAddress().getId() + " for project " + projectFromRequest.getId());
        }
    }

    /* Enrich address for update project request. If id is not present add address */
    private void enrichProjectAddressOnUpdate(Project projectFromRequest, Project projectFromDB, RequestInfo requestInfo) {
        if (projectFromRequest.getAddress() != null) {
            //Add address if not present already
            if (StringUtils.isBlank(projectFromRequest.getAddress().getId())) {
                log.info("Adding address for project " + projectFromDB.getId());
                enrichProjectAddressOnCreate(projectFromRequest, requestInfo);
            }
        }
        //Address not present in request
        else {
            log.info("Address not provided in project update request for project " + projectFromRequest.getId());
            //Address not present in request but present in db, then enrich response with address
            if (projectFromDB.getAddress() != null && StringUtils.isNotBlank(projectFromDB.getAddress().getId())) {
                projectFromRequest.setAddress(projectFromDB.getAddress());
                log.info("Enriched address details from DB with id " + projectFromDB.getAddress().getId() + " in project response body " + projectFromRequest.getId());
            }
            //Address not present in request and also not present in db, then set address in response to null
            else {
                projectFromRequest.setAddress(null);
                log.info("Address not found for project " + projectFromRequest.getId() + " in DB");
            }
        }
    }

    /* Enrich Target with id and audit details in create project request */
    private void enrichProjectTargetOnCreate(Project projectFromRequest, RequestInfo requestInfo) {
        if (projectFromRequest.getTargets() != null) {
            for (Target target: projectFromRequest.getTargets()) {
                setUUIDAndAuditDetailsForTargetCreate(target, requestInfo, projectFromRequest);
            }
        }
    }

    private void setUUIDAndAuditDetailsForTargetCreate(Target target, RequestInfo requestInfo, Project projectFromRequest) {
        target.setId(UUID.randomUUID().toString());
        AuditDetails auditDetailsForAdd = projectServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), null, true);
        target.setAuditDetails(auditDetailsForAdd);
        log.info("Added target with id " + target.getId() + " for project " + projectFromRequest.getId());
    }

    /* Enrich last modified by and last modified time for target in update project request. If id is not present add target */
    private void enrichProjectTargetOnUpdate(Project projectFromRequest, Project projectFromDB, RequestInfo requestInfo) {
        //Enrich the response with existing targets from the database if targets array is empty in the project request.
        if (projectFromRequest.getTargets() == null && projectFromDB.getTargets() != null) {
            projectFromRequest.setTargets(projectFromDB.getTargets().stream().filter(t -> !t.getIsDeleted()).collect(Collectors.toList()));
            return;
        }
        if (projectFromRequest.getTargets() != null) {
            for (Target target: projectFromRequest.getTargets()) {
                //Add target, if id not provided in request
                if (StringUtils.isBlank(target.getId())) {
                    setUUIDAndAuditDetailsForTargetCreate(target, requestInfo, projectFromRequest);
                }
                //If id provided in request, update existing target
                else {
                    updateExistingTarget(target, projectFromDB, requestInfo);
                }
            }
            //Include targets from the database in the search response that are not included in the request.
            if (projectFromDB.getTargets() != null) {
                addMissingTargetsFromDBToRequest(projectFromRequest, projectFromDB);
            }
        }
    }

    private void updateExistingTarget(Target target, Project projectFromDB, RequestInfo requestInfo) {
        String targetId = String.valueOf(target.getId());
        if (projectFromDB.getTargets() != null) {
            Target targetFromDB = projectFromDB.getTargets().stream().filter(t -> targetId.equals(String.valueOf(t.getId())) && !t.getIsDeleted()).findFirst().orElse(null);
            if (targetFromDB != null) {
                target.setAuditDetails(targetFromDB.getAuditDetails());
                AuditDetails auditDetailsTarget = projectServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), targetFromDB.getAuditDetails(), false);
                target.setAuditDetails(auditDetailsTarget);
                log.info("Enriched target audit details for target " + targetId);
            }
        }
    }

    private void addMissingTargetsFromDBToRequest(Project projectFromRequest, Project projectFromDB) {
        Set<String> targetIdsInRequest = projectFromRequest.getTargets().stream().map(Target :: getId).collect(Collectors.toSet());
        List<Target> filteredTargetsFromDB = projectFromDB.getTargets().stream().filter(t -> !targetIdsInRequest.contains(t.getId()) && !t.getIsDeleted()).collect(Collectors.toList());
        projectFromRequest.getTargets().addAll(filteredTargetsFromDB);
    }

    /* Enrich Document with id and audit details in create project request */
    private void enrichProjectDocumentOnCreate(Project projectFromRequest, RequestInfo requestInfo) {
        if (projectFromRequest.getDocuments() != null) {
            for (Document document: projectFromRequest.getDocuments()) {
                setUUIDAndAuditDetailsForDocumentCreate(document, requestInfo, projectFromRequest);
            }
        }
    }

    private void setUUIDAndAuditDetailsForDocumentCreate(Document document, RequestInfo requestInfo, Project projectFromRequest) {
        document.setId(UUID.randomUUID().toString());
        AuditDetails auditDetailsForAdd = projectServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), null, true);
        document.setAuditDetails(auditDetailsForAdd);
        log.info("Added document with id " + document.getId() + " for project " + projectFromRequest.getId());
    }

    /* Enrich last modified by and last modified time for document in update project request. If id is not present add document */
    private void enrichProjectDocumentOnUpdate(Project projectFromRequest, Project projectFromDB, RequestInfo requestInfo) {
        //Enrich the response with existing targets from the database if targets array is empty in the project request.
        if (projectFromRequest.getDocuments() == null && projectFromDB.getDocuments() != null) {
            projectFromRequest.setDocuments(projectFromDB.getDocuments().stream().filter(d -> (d.getStatus() != null && !d.getStatus().equals("INACTIVE"))).collect(Collectors.toList()));
            return;
        }
        if (projectFromRequest.getDocuments() != null) {
            for (Document document: projectFromRequest.getDocuments()) {
                //Add new document, if id not provided in request
                if (StringUtils.isBlank(document.getId())) {
                    setUUIDAndAuditDetailsForDocumentCreate(document, requestInfo, projectFromRequest);
                }
                //If id provided in request, update existing document
                else {
                    updateExistingDocument(document, projectFromDB, requestInfo);
                }
            }
            //Include documents from the database in the search response that are not included in the request.
            if (projectFromDB.getDocuments() != null) {
                addMissingDocumentFromDBToRequest(projectFromRequest, projectFromDB);
            }
        }
    }

    private void updateExistingDocument(Document document, Project projectFromDB, RequestInfo requestInfo) {
        String documentId = String.valueOf(document.getId());
        if (projectFromDB.getDocuments() != null) {
            Document documentFromDB = projectFromDB.getDocuments().stream().filter(d -> documentId.equals(String.valueOf(d.getId()))).findFirst().orElse(null);
            if (documentFromDB != null) {
                document.setAuditDetails(documentFromDB.getAuditDetails());
                AuditDetails auditDetailsDocument = projectServiceUtil.getAuditDetails(requestInfo.getUserInfo().getUuid(), documentFromDB.getAuditDetails(), false);
                document.setAuditDetails(auditDetailsDocument);
                log.info("Enriched document audit details for document " + documentId);
            }
        }
    }

    private void addMissingDocumentFromDBToRequest(Project projectFromRequest, Project projectFromDB) {
        Set<String> documentIdsInRequest = projectFromRequest.getDocuments().stream().map(Document :: getId).collect(Collectors.toSet());
        List<Document> filteredDocumentsFromDB = projectFromDB.getDocuments().stream().filter(d -> !documentIdsInRequest.contains(d.getId())).collect(Collectors.toList());
        projectFromRequest.getDocuments().addAll(filteredDocumentsFromDB);
    }

    /* Get Project Number list from IdGen service */
    private List<String> getIdList(RequestInfo requestInfo, String tenantId, String idKey,
                                   String idformat, int count) {
        try {
            return idGenService.getIdList(requestInfo, tenantId, idKey, idformat, count);
        } catch (Exception exception) {
            log.error("error while calling id gen service", ExceptionUtils.getStackTrace(exception));
            throw new CustomException("IDGEN_ERROR",
                    String.format("error while calling id gen service for %s", idformat));
        }
    }
}
