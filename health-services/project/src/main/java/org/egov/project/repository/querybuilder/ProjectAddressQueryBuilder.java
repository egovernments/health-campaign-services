package org.egov.project.repository.querybuilder;

import java.util.Collection;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.models.core.ProjectSearchURLParams;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectSearch;
import org.egov.project.config.ProjectConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import static org.egov.project.util.ProjectConstants.DOT;

@Component
@Slf4j
public class ProjectAddressQueryBuilder {

    @Autowired
    private ProjectConfiguration config;

    private static final String FETCH_PROJECT_ADDRESS_QUERY = "SELECT prj.id as projectId, prj.tenantId as project_tenantId, prj.projectNumber as project_projectNumber, prj.name as project_name, prj.projectType as project_projectType, prj.projectTypeId as project_projectTypeId, prj.projectSubType as project_projectSubtype, " +
            " prj.department as project_department, prj.description as project_description, prj.referenceId as project_referenceId, prj.startDate as project_startDate, prj.endDate as project_endDate, " +
            "prj.isTaskEnabled as project_isTaskEnabled, prj.parent as project_parent, prj.projectHierarchy as project_projectHierarchy, prj.natureOfWork as project_natureOfWork, prj.additionalDetails as project_additionalDetails, prj.isDeleted as project_isDeleted, prj.rowVersion as project_rowVersion, " +
            " prj.createdBy as project_createdBy, prj.lastModifiedBy as project_lastModifiedBy, prj.createdTime as project_createdTime, prj.lastModifiedTime as project_lastModifiedTime, " +
            "addr.id as addressId, addr.tenantId as address_tenantId, addr.projectId as address_projectId, addr.doorNo as address_doorNo, addr.latitude as address_latitude, addr.longitude as address_longitude, addr.locationAccuracy as address_locationAccuracy, " +
            " addr.type as address_type, addr.addressLine1 as address_addressLine1, addr.addressLine2 as address_addressLine2, addr.landmark as address_landmark, addr.city as address_city, addr.pinCode as address_pinCode, " +
            " addr.buildingName as address_buildingName, addr.street as address_street, addr.boundaryType as address_boundaryType, addr.boundary as address_boundary " +
            " " +
            "from project prj " +
            "left join project_address addr " +
            "on prj.id = addr.projectId ";

    private final String paginationWrapper = "SELECT * FROM " +
            "(SELECT *, DENSE_RANK() OVER (ORDER BY project_lastModifiedTime DESC , projectId) offset_ FROM " +
            "({})" +
            " result) result_offset " +
            "WHERE offset_ > ? AND offset_ <= ?";

    private static final String PROJECTS_COUNT_QUERY = "SELECT COUNT(*) FROM project prj " +
            "left join project_address addr " +
            "on prj.id = addr.projectId ";;

    /* Constructs project search query based on conditions */
    public String getProjectSearchQuery(List<Project> projects, Integer limit, Integer offset, String tenantId, Long lastChangedSince, Boolean includeDeleted, Long createdFrom, Long createdTo, List<Object> preparedStmtList, boolean isCountQuery) {
        //This uses a ternary operator to choose between PROJECTS_COUNT_QUERY or FETCH_PROJECT_ADDRESS_QUERY based on the value of isCountQuery.
        String query = isCountQuery ? PROJECTS_COUNT_QUERY : FETCH_PROJECT_ADDRESS_QUERY;
        StringBuilder queryBuilder = new StringBuilder(query);

        Integer count = projects.size();

        for (Project project: projects) {

            if (StringUtils.isNotBlank(tenantId)) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                if (!tenantId.contains(DOT)) {
                    log.info("State level tenant");
                    queryBuilder.append(" prj.tenantId like ? ");
                    preparedStmtList.add(tenantId+'%');
                } else {
                    log.info("City level tenant");
                    queryBuilder.append(" prj.tenantId=? ");
                    preparedStmtList.add(tenantId);
                }
            }

            if (StringUtils.isNotBlank(project.getId())) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.id =? ");
                preparedStmtList.add(project.getId());
            }

            if (StringUtils.isNotBlank(project.getProjectNumber())) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.projectNumber =? ");
                preparedStmtList.add(project.getProjectNumber());
            }

            if (StringUtils.isNotBlank(project.getName())) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.name LIKE ? ");
                preparedStmtList.add('%' + project.getName() + '%');
            }

            if (StringUtils.isNotBlank(project.getProjectType())) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.projectType=? ");
                preparedStmtList.add(project.getProjectType());
            }

            if (project.getAddress() != null && StringUtils.isNotBlank(project.getAddress().getBoundary())) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" addr.boundary=? ");
                preparedStmtList.add(project.getAddress().getBoundary());
            }

            if (StringUtils.isNotBlank(project.getProjectSubType())) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.projectSubtype=? ");
                preparedStmtList.add(project.getProjectSubType());
            }

            if (project.getStartDate() != null && project.getStartDate() != 0) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.startDate >= ? ");
                preparedStmtList.add(project.getStartDate());
            }

            if (project.getEndDate() != null && project.getEndDate() != 0) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.endDate <= ? ");
                preparedStmtList.add(project.getEndDate());
            }

            if (lastChangedSince != null && lastChangedSince != 0) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" ( prj.lastModifiedTime >= ? )");
                preparedStmtList.add(lastChangedSince);
            }

            if (createdFrom != null && createdFrom != 0) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.createdTime >= ? ");
                preparedStmtList.add(createdFrom);
            }

            if (createdTo != null && createdTo != 0) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.createdTime <= ? ");
                preparedStmtList.add(createdTo);
            }

            //Add clause if includeDeleted is true in request parameter
            addIsDeletedCondition(preparedStmtList, queryBuilder, includeDeleted);

            queryBuilder.append(" )");
            count--;
            addORClause(count, queryBuilder);
        }

        if (isCountQuery) {
            return queryBuilder.toString();
        }

        //Wrap constructed SQL query with where criteria in pagination query
        return addPaginationWrapper(queryBuilder.toString(), preparedStmtList, limit, offset);
    }


    /**
     * Constructs the SQL query string for searching projects based on the given parameters.
     *
     * @param projectSearch       The search criteria provided in the request body.
     * @param urlParams           The search criteria provided as URL parameters.
     * @param preparedStmtList    The list to which prepared statement parameters will be added.
     * @param isCountQuery        Boolean flag indicating if the query is for counting records.
     * @return                    The constructed SQL query string.
     */
    public String getProjectSearchQuery(@NotNull @Valid ProjectSearch projectSearch, ProjectSearchURLParams urlParams, List<Object> preparedStmtList, Boolean isCountQuery) {
        // Use a ternary operator to select between PROJECTS_COUNT_QUERY and FETCH_PROJECT_ADDRESS_QUERY based on isCountQuery flag.
        String query = isCountQuery ? PROJECTS_COUNT_QUERY : FETCH_PROJECT_ADDRESS_QUERY;
        StringBuilder queryBuilder = new StringBuilder(query);

        // Check if tenant ID is provided in URL parameters
        if (StringUtils.isNotBlank(urlParams.getTenantId())) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            if (!urlParams.getTenantId().contains(DOT)) {
                // State level tenant ID: use LIKE for partial matching
                log.info("State level tenant");
                queryBuilder.append(" prj.tenantId like ? ");
                preparedStmtList.add(urlParams.getTenantId() + '%');
            } else {
                // City level tenant ID: use exact match
                log.info("City level tenant");
                queryBuilder.append(" prj.tenantId=? ");
                preparedStmtList.add(urlParams.getTenantId());
            }
        }

        // Check if project IDs are provided
        if (!CollectionUtils.isEmpty(projectSearch.getId())) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" prj.id IN (").append(createQuery(projectSearch.getId())).append(")");
            addToPreparedStatement(preparedStmtList, projectSearch.getId());
        }

        // Check if reference ID is provided
        if (StringUtils.isNotBlank(projectSearch.getReferenceId())) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" prj.referenceId =? ");
            preparedStmtList.add(projectSearch.getReferenceId());
        }

        // Check if project name is provided
        if (StringUtils.isNotBlank(projectSearch.getName())) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" prj.name LIKE ? ");
            preparedStmtList.add('%' + projectSearch.getName() + '%');
        }

        // Check if project type ID is provided
        if (StringUtils.isNotBlank(projectSearch.getProjectTypeId())) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" prj.projectType=? ");
            preparedStmtList.add(projectSearch.getProjectTypeId());
        }

        // Check if boundary code is provided
        if (projectSearch.getBoundaryCode() != null && StringUtils.isNotBlank(projectSearch.getBoundaryCode())) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" addr.boundary=? ");
            preparedStmtList.add(projectSearch.getBoundaryCode());
        }

        // Check if sub-project type ID is provided
        if (StringUtils.isNotBlank(projectSearch.getSubProjectTypeId())) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" prj.projectSubtype=? ");
            preparedStmtList.add(projectSearch.getSubProjectTypeId());
        }

        // Check if start date is provided
        if (projectSearch.getStartDate() != null && projectSearch.getStartDate() != 0) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" prj.startDate >= ? ");
            preparedStmtList.add(projectSearch.getStartDate());
        }

        // Check if end date is provided
        if (projectSearch.getEndDate() != null && projectSearch.getEndDate() != 0) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" prj.endDate <= ? ");
            preparedStmtList.add(projectSearch.getEndDate());
        }

        // Check if lastChangedSince is provided
        if (urlParams.getLastChangedSince() != null && urlParams.getLastChangedSince() != 0) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" ( prj.lastModifiedTime >= ? )");
            preparedStmtList.add(urlParams.getLastChangedSince());
        }

        // Check if createdFrom date is provided
        if (urlParams.getCreatedFrom() != null && urlParams.getCreatedFrom() != 0) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" prj.createdTime >= ? ");
            preparedStmtList.add(urlParams.getCreatedFrom());
        }

        // Check if createdTo date is provided
        if (urlParams.getCreatedTo() != null && urlParams.getCreatedTo() != 0) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" prj.createdTime <= ? ");
            preparedStmtList.add(urlParams.getCreatedTo());
        }

        // Add clause if includeDeleted is true in request parameter
        addIsDeletedCondition(preparedStmtList, queryBuilder, urlParams.getIncludeDeleted());

        // Close the query with a closing bracket
        queryBuilder.append(" )");

        // Return query if it's a count query
        if (isCountQuery) {
            return queryBuilder.toString();
        }

        // Wrap constructed SQL query with pagination criteria
        return addPaginationWrapper(queryBuilder.toString(), preparedStmtList, urlParams.getLimit(), urlParams.getOffset());
    }

    /* Constructs project search query based on Project Ids */
    public String getProjectSearchQueryBasedOnIds(List<String> projectIds, List<Object> preparedStmtList) {
        StringBuilder queryBuilder = new StringBuilder(FETCH_PROJECT_ADDRESS_QUERY);

        if (projectIds != null && !projectIds.isEmpty()) {
            addConditionalClause(preparedStmtList, queryBuilder);
            queryBuilder.append(" prj.id IN (").append(createQuery(projectIds)).append(")");
            addToPreparedStatement(preparedStmtList, projectIds);
        }

        return queryBuilder.toString();
    }

    private void addIsDeletedCondition(List<Object> preparedStmtList, StringBuilder queryBuilder, Boolean includeDeleted) {
        if (!includeDeleted) {
            addClauseIfRequired(preparedStmtList, queryBuilder);
            queryBuilder.append(" prj.isDeleted = false ");
        }
    }

    private void addORClause(Integer count, StringBuilder queryBuilder) {
        if (count > 0) {
            queryBuilder.append(" OR ( ");
        }
    }

    /* Add WHERE clause before first condition, ADD and for subsequent conditions. Do not add AND before any condition and after "(" */
    private static void addClauseIfRequired(List<Object> values, StringBuilder queryString) {
        if (values.isEmpty())
            queryString.append(" WHERE ( ");
        else if (queryString.toString().lastIndexOf("(") != (queryString.toString().trim().length() - 1)) {
            queryString.append(" AND");
        }
    }

    /* Add conditional clause */
    private static void addConditionalClause(List<Object> values, StringBuilder queryString) {
        if (values.isEmpty())
            queryString.append(" WHERE ");
        else {
            queryString.append(" OR ");
        }
    }

    /* Wrap constructed SQL query with where criteria in pagination query */
    private String addPaginationWrapper(String query,List<Object> preparedStmtList, Integer limitParam, Integer offsetParam){
        int limit = config.getDefaultLimit();
        int offset = config.getDefaultOffset();
        String finalQuery = paginationWrapper.replace("{}", query);

        if (limitParam != null) {
            if (limitParam <= config.getMaxLimit())
                limit = limitParam;
            else
                limit = config.getMaxLimit();
        }

        if (offsetParam != null)
            offset = offsetParam;

        preparedStmtList.add(offset);
        preparedStmtList.add(limit + offset);

        return finalQuery;
    }

    private String createQuery(Collection<String> ids) {
        StringBuilder builder = new StringBuilder();
        int length = ids.size();
        for (int i = 0; i < length; i++) {
            builder.append(" ? ");
            if (i != length - 1) builder.append(",");
        }
        return builder.toString();
    }

    private void addToPreparedStatement(List<Object> preparedStmtList, Collection<String> ids) {
        preparedStmtList.addAll(ids);
    }

    /* Returns query to search for projects where project_hierarchy contains project Ids */
    public String getProjectDescendantsSearchQueryBasedOnIds(List<String> projectIds, List<Object> preparedStmtListDescendants) {
        StringBuilder queryBuilder = new StringBuilder(FETCH_PROJECT_ADDRESS_QUERY);
        for (String projectId : projectIds) {
            addConditionalClause(preparedStmtListDescendants, queryBuilder);
            queryBuilder.append(" ( prj.projectHierarchy LIKE ? )");
            preparedStmtListDescendants.add('%' + projectId + '%');
        }
        
        return queryBuilder.toString();
    }
    
    /* Returns query to get total projects count based on project search params */
    public String getSearchCountQueryString(List<Project> projects, String tenantId, Long lastChangedSince, Boolean includeDeleted, Long createdFrom, Long createdTo, List<Object> preparedStatement) {
        String query = getProjectSearchQuery(projects, config.getMaxLimit(), config.getDefaultOffset(), tenantId, lastChangedSince, includeDeleted, createdFrom, createdTo, preparedStatement, true);
        return query;
    }

    /* Returns query to get total projects count based on project search params */
    public String getSearchCountQueryString(ProjectSearch projectSearch, ProjectSearchURLParams urlParams, List<Object> preparedStatement) {
        String query = getProjectSearchQuery(projectSearch, urlParams, preparedStatement, Boolean.TRUE);
        return query;
    }
}
