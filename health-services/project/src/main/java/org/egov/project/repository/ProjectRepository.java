package org.egov.project.repository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.ProjectSearchURLParams;
import org.egov.common.models.project.*;
import org.egov.common.producer.Producer;
import org.egov.project.repository.querybuilder.DocumentQueryBuilder;
import org.egov.project.repository.querybuilder.ProjectAddressQueryBuilder;
import org.egov.project.repository.querybuilder.TargetQueryBuilder;
import org.egov.project.repository.rowmapper.DocumentRowMapper;
import org.egov.project.repository.rowmapper.ProjectAddressRowMapper;
import org.egov.project.repository.rowmapper.ProjectRowMapper;
import org.egov.project.repository.rowmapper.TargetRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class ProjectRepository extends GenericRepository<Project> {

    private final ProjectAddressQueryBuilder queryBuilder;

    private final TargetQueryBuilder targetQueryBuilder;
    private final DocumentQueryBuilder documentQueryBuilder;

    private final ProjectAddressRowMapper addressRowMapper;
    private final TargetRowMapper targetRowMapper;
    private final DocumentRowMapper documentRowMapper;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ProjectRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                             RedisTemplate<String, Object> redisTemplate,
                             SelectQueryBuilder selectQueryBuilder, ProjectRowMapper projectRowMapper,
                             ProjectAddressQueryBuilder queryBuilder,
                             TargetQueryBuilder targetQueryBuilder,
                             DocumentQueryBuilder documentQueryBuilder,
                             ProjectAddressRowMapper addressRowMapper, TargetRowMapper targetRowMapper,
                             DocumentRowMapper documentRowMapper, JdbcTemplate jdbcTemplate) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                projectRowMapper, Optional.of("project"));
        this.queryBuilder = queryBuilder;
        this.targetQueryBuilder = targetQueryBuilder;
        this.documentQueryBuilder = documentQueryBuilder;
        this.addressRowMapper = addressRowMapper;
        this.targetRowMapper = targetRowMapper;
        this.documentRowMapper = documentRowMapper;
        this.jdbcTemplate = jdbcTemplate;
    }


    /**
    * @param isAncestorProjectId When true, treats the project IDs in the ProjectRequest as ancestor project IDs
    */
    public List<Project> getProjects(ProjectRequest project, Integer limit, Integer offset, String tenantId, Long lastChangedSince, Boolean includeDeleted, Boolean includeAncestors, Boolean includeDescendants, Boolean includeImmediateChildren, Long createdFrom, Long createdTo, boolean isAncestorProjectId) throws InvalidTenantIdException {

        //Fetch Projects based on search criteria
        List<Project> projects = getProjectsBasedOnSearchCriteria(project.getProjects(), limit, offset, tenantId, lastChangedSince, includeDeleted, createdFrom, createdTo, isAncestorProjectId);

        Set<String> projectIds = projects.stream().map(Project :: getId).collect(Collectors.toSet());

        List<Project> ancestors = null;
        List<Project> descendants = null;
        List<Target> targets = new ArrayList<>();
        List<Document> documents = new ArrayList<>();
        if(!projectIds.isEmpty()) {
            //Get Project ancestors if includeAncestors flag is true
            if (includeAncestors) {
                ancestors = getProjectAncestors(tenantId, projects);
                if (ancestors != null && !ancestors.isEmpty()) {
                    List<String> ancestorProjectIds = ancestors.stream().map(Project :: getId).collect(Collectors.toList());
                    projectIds.addAll(ancestorProjectIds);
                }
            }
            //Get Project descendants if includeDescendants flag is true
            if (includeImmediateChildren) {
                descendants = getProjectImmediateDescendants(tenantId, projects);
            } else if (includeDescendants) {
                descendants = getProjectDescendants(tenantId, projects);
            }
            List<String> descendantsProjectIds = descendants == null || descendants.isEmpty() ? new ArrayList<>() : descendants.stream().map(Project::getId).collect(Collectors.toList());
            projectIds.addAll(descendantsProjectIds);

            //Fetch targets based on Project Ids
            targets = getTargetsBasedOnProjectIds(tenantId, projectIds);

            //Fetch documents based on Project Ids
            documents = getDocumentsBasedOnProjectIds(tenantId, projectIds);
        }

        //Construct Project Objects with fetched projects, targets and documents using Project id
        return buildProjectSearchResult(projects, targets, documents, ancestors, descendants);
    }

    public List<Project> getProjects(@NotNull @Valid ProjectSearch projectSearch, @Valid ProjectSearchURLParams urlParams) throws InvalidTenantIdException {

        //Fetch Projects based on search criteria
        List<Project> projects = getProjectsBasedOnV2SearchCriteria(projectSearch, urlParams);

        Set<String> projectIds = projects.stream().map(Project :: getId).collect(Collectors.toSet());

        List<Project> ancestors = null;
        List<Project> descendants = null;
        List<Target> targets = new ArrayList<>();
        List<Document> documents = new ArrayList<>();
        if(!projectIds.isEmpty()) {
            //Get Project ancestors if includeAncestors flag is true
            if (urlParams.getIncludeAncestors()) {
                ancestors = getProjectAncestors(urlParams.getTenantId(), projects);
                if (ancestors != null && !ancestors.isEmpty()) {
                    List<String> ancestorProjectIds = ancestors.stream().map(Project :: getId).toList();
                    projectIds.addAll(ancestorProjectIds);
                }
            }
            //Get Project descendants if includeDescendants flag is true
            if (urlParams.getIncludeDescendants()) {
                descendants = getProjectDescendants(urlParams.getTenantId(), projects);
                if (descendants != null && !descendants.isEmpty()) {
                    List<String> descendantsProjectIds = descendants.stream().map(Project :: getId).toList();
                    projectIds.addAll(descendantsProjectIds);
                }
            }

            //Fetch targets based on Project Ids
            targets = getTargetsBasedOnProjectIds(urlParams.getTenantId(), projectIds);

            //Fetch documents based on Project Ids
            documents = getDocumentsBasedOnProjectIds(urlParams.getTenantId(), projectIds);
        }

        //Construct Project Objects with fetched projects, targets and documents using Project id
        return buildProjectSearchResult(projects, targets, documents, ancestors, descendants);
    }

    private List<Project> getProjectsBasedOnV2SearchCriteria(@NotNull @Valid ProjectSearch projectSearch, ProjectSearchURLParams urlParams) throws InvalidTenantIdException {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getProjectSearchQuery(projectSearch, urlParams, preparedStmtList, Boolean.FALSE);
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, urlParams.getTenantId());
        List<Project> projects = jdbcTemplate.query(query, addressRowMapper, preparedStmtList.toArray());

        log.info("Fetched project list based on given search criteria");
        return projects;
    }

    /* Fetch Projects based on search criteria */
    private List<Project> getProjectsBasedOnSearchCriteria(List<Project> projectsRequest, Integer limit, Integer offset, String tenantId, Long lastChangedSince, Boolean includeDeleted, Long createdFrom, Long createdTo, boolean isAncestorProjectId) throws InvalidTenantIdException {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getProjectSearchQuery(projectsRequest, limit, offset, tenantId, lastChangedSince, includeDeleted, createdFrom, createdTo, isAncestorProjectId, preparedStmtList, false);
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        List<Project> projects = jdbcTemplate.query(query, addressRowMapper, preparedStmtList.toArray());

        log.info("Fetched project list based on given search criteria");
        return projects;
    }

    /* Fetch Projects based on Project ids */
    public List<Project> getProjectsBasedOnProjectIds(String tenantId, List<String> projectIds,  List<Object> preparedStmtList) throws InvalidTenantIdException {
        String query = queryBuilder.getProjectSearchQueryBasedOnIds(projectIds, preparedStmtList);
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        List<Project> projects = jdbcTemplate.query(query, addressRowMapper, preparedStmtList.toArray());
        log.info("Fetched project list based on given Project Ids");
        return projects;
    }

    /* Fetch Project descendants based on Project ids */
    private List<Project> getProjectsDescendantsBasedOnProjectIds(String tenantId, List<String> projectIds, List<Object> preparedStmtListDescendants) throws InvalidTenantIdException {
        String query = queryBuilder.getProjectDescendantsSearchQueryBasedOnIds(projectIds, preparedStmtListDescendants);
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        List<Project> projects = jdbcTemplate.query(query, addressRowMapper, preparedStmtListDescendants.toArray());
        log.info("Fetched project descendants list based on given Project Ids");
        return projects;
    }

    /* Fetch Project descendants based on Project ids */
    private List<Project> getProjectsImmediateDescendantsBasedOnProjectIds(String tenantId, List<String> projectIds, List<Object> preparedStmtListDescendants) throws InvalidTenantIdException {
        String query = queryBuilder.getProjectImmediateDescendantsSearchQueryBasedOnIds(projectIds, preparedStmtListDescendants);
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        List<Project> projects = jdbcTemplate.query(query, addressRowMapper, preparedStmtListDescendants.toArray());
        log.info("Fetched project immediate descendants list based on given Project Ids");
        return projects;
    }

    /* Fetch targets based on Project Ids */
    private List<Target> getTargetsBasedOnProjectIds(String tenantId, Set<String> projectIds) throws InvalidTenantIdException {
        List<Object> preparedStmtListTarget = new ArrayList<>();
        String queryTarget = targetQueryBuilder.getTargetSearchQuery(projectIds, preparedStmtListTarget);
        // Replacing schema placeholder with the schema name for the tenant id
        queryTarget = multiStateInstanceUtil.replaceSchemaPlaceholder(queryTarget, tenantId);
        List<Target> targets = jdbcTemplate.query(queryTarget, targetRowMapper, preparedStmtListTarget.toArray());
        log.info("Fetched targets based on project Ids");
        return targets;
    }

    /* Fetch documents based on Project Ids */
    private List<Document> getDocumentsBasedOnProjectIds(String tenantId, Set<String> projectIds) throws InvalidTenantIdException {
        List<Object> preparedStmtListDocument = new ArrayList<>();
        String queryDocument = documentQueryBuilder.getDocumentSearchQuery(projectIds, preparedStmtListDocument);
        // Replacing schema placeholder with the schema name for the tenant id
        queryDocument = multiStateInstanceUtil.replaceSchemaPlaceholder(queryDocument, tenantId);
        List<Document> documents = jdbcTemplate.query(queryDocument, documentRowMapper, preparedStmtListDocument.toArray());
        log.info("Fetched documents based on project Ids");
        return documents;
    }

    /* Separates preceding project ids from project hierarchy, adds them in list and fetches data using those project ids */
    private List<Project> getProjectAncestors(String tenantId, List<Project> projects) throws InvalidTenantIdException {
        List<String> ancestorIds = new ArrayList<>();
        List<Project> ancestors = null;

        // Get project Id of ancestor projects from project Hierarchy
        for (Project project: projects) {
            if (StringUtils.isNotBlank(project.getProjectHierarchy())) {
                List<String> projectHierarchyIds = Arrays.asList(project.getProjectHierarchy().split("\\."));
                ancestorIds.addAll(projectHierarchyIds);
            }
        }
        //Fetch projects based on ancestor project Ids
        if (ancestorIds.size() > 0) {
            List<Object> preparedStmtListAncestors = new ArrayList<>();
            ancestors = getProjectsBasedOnProjectIds(tenantId, ancestorIds, preparedStmtListAncestors);
            log.info("Fetched ancestor projects");
        }

        return ancestors;
    }

    /* Fetch projects where project hierarchy for projects in db contains project ID of requested project. The descendant project's projectHierarchy will contain parent project id */
    private List<Project> getProjectDescendants(String tenantId, List<Project> projects) throws InvalidTenantIdException {
        List<String> projectIds = projects.stream().map(Project:: getId).collect(Collectors.toList());

        List<Object> preparedStmtListDescendants = new ArrayList<>();
        log.info("Fetching descendant projects");

        return getProjectsDescendantsBasedOnProjectIds(tenantId, projectIds, preparedStmtListDescendants);
    }

    /* Fetch projects where project parent for projects in db contains project ID of requested project.*/
    private List<Project> getProjectImmediateDescendants(String tenantId, List<Project> projects) throws InvalidTenantIdException {
        List<String> requestProjectIds = projects.stream().map(Project::getId).collect(Collectors.toList());

        List<Object> preparedStmtListDescendants = new ArrayList<>();
        log.info("Fetching immediate descendant projects");

        return getProjectsImmediateDescendantsBasedOnProjectIds(tenantId, requestProjectIds, preparedStmtListDescendants);
    }

    /* Constructs Project Objects with fetched projects, targets and documents using Project id and return list of Projects */
    private List<Project> buildProjectSearchResult(List<Project> projects, List<Target> targets, List<Document> documents, List<Project> ancestors, List<Project> descendants) {
        for (Project project: projects) {
            log.info("Constructing project object for project " + project.getId());
            if (targets != null && targets.size() > 0) {
                log.info("Adding Targets to project " + project.getId());
                addTargetToProject(project, targets);
            }
            if (documents != null && documents.size() > 0) {
                log.info("Adding Documents to project " + project.getId());
                addDocumentToProject(project, documents);
            }
            if (ancestors != null && !ancestors.isEmpty() && StringUtils.isNotBlank(project.getParent())) {
                log.info("Adding ancestors to project " + project.getId());
                addAncestorsToProjectSearchResult(project, ancestors, targets, documents);
            }
            if (descendants != null && !descendants.isEmpty()) {
                log.info("Adding descendants to project " + project.getId());
                addDescendantsToProjectSearchResult(project, descendants, targets, documents);
            }
            log.info("Constructed project object for project " + project.getId());
        }
        return projects;
    }

    /* Add Targets to projects based on projectId and targets list passed */
    private void addTargetToProject(Project project, List<Target> targets) {
        project.setTargets(new ArrayList<>());
        for (Target target: targets) {
            if (target.getProjectid().equals(project.getId()) && !target.getIsDeleted() && project.getTargets().stream().noneMatch(t -> t.getId().equals(target.getId()))) {
                project.getTargets().add(target);
            }
        }
    }

    /* Add Documents to projects based on projectId and documents list passed */
    private void addDocumentToProject(Project project, List<Document> documents) {
        project.setDocuments(new ArrayList<>());
        for (Document document: documents) {
            if (document.getProjectid().equals(project.getId())
                    && (document.getStatus() == null || document.getStatus() != null && !document.getStatus().equals("INACTIVE"))
                    && project.getDocuments().stream().noneMatch(t -> t.getId().equals(document.getId()))) {
                project.getDocuments().add(document);
            }
        }
    }


    /* Adds ancestors to Project based on project and ancestors list  */
    private void addAncestorsToProjectSearchResult(Project project, List<Project> ancestors, List<Target> targets, List<Document> documents) {
        List<Project> currentProjectAncestors = ancestors.stream().filter(a -> (project.getProjectHierarchy().contains(a.getId())
                && !project.getId().equals(a.getId()))).collect(Collectors.toList());
        //Add target and document to ancestor projects using targets and documents list
        for (Project ancestor: currentProjectAncestors) {
            addTargetToProject(ancestor, targets);
            addDocumentToProject(ancestor, documents);
            log.info("Targets and Documents mapped to ancestor projects");
        }
        project.setAncestors(currentProjectAncestors);
        log.info("Ancestors set for project " + project.getId());

        /* The below code returns Project ancestors with tree structure. If project hierarchy A.B.C, "ancestor" field of project C will contain project B
         * "ancestor" field of project B will contain project A and so on. For this to work, change type of "ancestor" to Project instead of List<Project>.
         *  This code snippet has been tested and working as expected. */

//        Project currentProject = project;
//        while (StringUtils.isNotBlank(currentProject.getParent())) {
//            String parentProjectId = currentProject.getParent();
//            Project parentProject = ancestors.stream().filter(prj -> prj.getId().equals(parentProjectId)).findFirst().orElse(null);
//            currentProject.setAncestors(parentProject);
//            currentProject = currentProject.getAncestors();
//        }
    }

    /* Adds ancestors to Project based on project and descendants list  */
    private void addDescendantsToProjectSearchResult(Project project, List<Project> descendants, List<Target> targets, List<Document> documents) {
        List<Project> subProjects = descendants.stream().filter(d -> StringUtils.isNotBlank(d.getParent())
                && d.getProjectHierarchy().contains(project.getId())
                && !d.getId().equals(project.getId())).collect(Collectors.toList());
        //Add target and document to descendants projects using targets and documents list
        for (Project ancestor: subProjects) {
            addTargetToProject(ancestor, targets);
            addDocumentToProject(ancestor, documents);
            log.info("Targets and Documents mapped to descendant projects");
        }
        if (!subProjects.isEmpty()) {
            project.setDescendants(subProjects);
            log.info("Descendants set for project " + project.getId());
        }

        /* The below code returns Project descendants with tree structure. If project hierarchy A.B.C and A.D, "descendants" field of project A will contain project B and project D
         * "descendants" field of project B will contain project C, "descendants" field of project C and D will contain null  and so on.
         *  This code snippet is incomplete and not working for multiple projects, multiple subprojects */
//        for (Project descendant : descendants) {
//            addDescendants(project, descendant);
//        }
//        // Recursive method to add Descendants. This method can be taken out while implementing tree hierarchy
//        public static void addDescendants(Project parent, Project child) {
//            if (parent.getId().equals(child.getParent())) {
//                parent.addDescendant(child);
//            } else {
//                for (Project project : parent.getDescendants()) {
//                    addDescendants(project, child);
//                }
//            }
//        }
    }

    /**
     * Get the count of projects based on the given search criteria (using dynamic
     * query build at the run time)
     * @return
     */
    public Integer getProjectCount(ProjectRequest project, String tenantId, Long lastChangedSince, Boolean includeDeleted, Long createdFrom, Long createdTo, boolean isAncestorProjectId) throws InvalidTenantIdException {
        List<Object> preparedStatement = new ArrayList<>();
        String query = queryBuilder.getSearchCountQueryString(project.getProjects(), tenantId, lastChangedSince, includeDeleted, createdFrom, createdTo, isAncestorProjectId, preparedStatement);

        if (query == null)
            return 0;
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        Integer count = jdbcTemplate.queryForObject(query, preparedStatement.toArray(), Integer.class);
        log.info("Total project count is : " + count);
        return count;
    }

    /**
     * Get the count of projects based on the given search criteria (using dynamic
     * query build at the run time)
     * @return
     */
    public Integer getProjectCount(ProjectSearch projectSearch, ProjectSearchURLParams urlParams) throws InvalidTenantIdException {
        List<Object> preparedStatement = new ArrayList<>();
        String query = queryBuilder.getSearchCountQueryString(projectSearch, urlParams, preparedStatement);

        if (query == null)
            return 0;

        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, urlParams.getTenantId());
        Integer count = jdbcTemplate.queryForObject(query, preparedStatement.toArray(), Integer.class);
        log.info("Total project count is : " + count);
        return count;
    }
}