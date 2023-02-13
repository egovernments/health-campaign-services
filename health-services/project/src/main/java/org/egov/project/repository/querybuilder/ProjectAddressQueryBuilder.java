package org.egov.project.repository.querybuilder;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.project.config.ProjectConfiguration;
import org.egov.project.web.models.Project;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

import static org.egov.project.util.ProjectConstants.DOT;

@Component
@Slf4j
public class ProjectAddressQueryBuilder {

    @Autowired
    private ProjectConfiguration config;

    private static final String FETCH_PROJECT_ADDRESS_QUERY = "SELECT prj.id as projectId, prj.tenant_id as project_tenantId, prj.project_number as project_projectNumber, prj.name as project_name, prj.project_type as project_projectType, prj.project_subtype as project_projectSubtype, " +
            " prj.department as project_department, prj.description as project_description, prj.reference_id as project_referenceId, prj.start_date as project_startDate, prj.end_date as project_endDate, " +
            "prj.is_task_enabled as project_isTaskEnabled, prj.parent as project_parent, prj.project_hierarchy as project_projectHierarchy, prj.additional_details as project_additionalDetails, prj.is_deleted as project_isDeleted, prj.row_version as project_rowVersion, " +
            " prj.created_by as project_createdBy, prj.last_modified_by as project_lastModifiedBy, prj.created_time as project_createdTime, prj.last_modified_time as project_lastModifiedTime, " +
            "addr.id as addressId, addr.tenant_id as address_tenantId, addr.project_id as address_projectId, addr.door_no as address_doorNo, addr.latitude as address_latitude, addr.longitude as address_longitude, addr.location_accuracy as address_locationAccuracy, " +
            " addr.type as address_type, addr.address_line1 as address_addressLine1, addr.address_line2 as address_addressLine2, addr.landmark as address_landmark, addr.city as address_city, addr.pin_code as address_pinCode, " +
            " addr.building_name as address_buildingName, addr.street as address_street, addr.locality as address_locality, addr.created_by as address_createdBy, addr.last_modified_by as address_lastModifiedBy, addr.created_time as address_createdTime, addr.last_modified_time as address_lastModifiedTime " +
            " " +
            "from eg_pms_project prj " +
            "left join eg_pms_address addr " +
            "on prj.id = addr.project_id ";

    private final String paginationWrapper = "SELECT * FROM " +
            "(SELECT *, DENSE_RANK() OVER (ORDER BY project_lastModifiedTime DESC , projectId) offset_ FROM " +
            "({})" +
            " result) result_offset " +
            "WHERE offset_ > ? AND offset_ <= ?";

    private static final String PROJECTS_COUNT_QUERY = "SELECT COUNT(*) FROM eg_pms_project prj ";

    /* Constructs project search query based on conditions */
    public String getProjectSearchQuery(List<Project> projects, Integer limit, Integer offset, String tenantId, Long lastChangedSince, Boolean includeDeleted, List<Object> preparedStmtList, boolean isCountQuery) {
        //This uses a ternary operator to choose between PROJECTS_COUNT_QUERY or FETCH_PROJECT_ADDRESS_QUERY based on the value of isCountQuery.
        String query = isCountQuery ? PROJECTS_COUNT_QUERY : FETCH_PROJECT_ADDRESS_QUERY;
        StringBuilder queryBuilder = new StringBuilder(query);

        Integer count = projects.size();

        for (Project project: projects) {

            if (StringUtils.isNotBlank(tenantId)) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                if (!tenantId.contains(DOT)) {
                    log.info("State level tenant");
                    queryBuilder.append(" prj.tenant_id like ? ");
                    preparedStmtList.add(tenantId+'%');
                } else {
                    log.info("City level tenant");
                    queryBuilder.append(" prj.tenant_id=? ");
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
                queryBuilder.append(" prj.project_number =? ");
                preparedStmtList.add(project.getProjectNumber());
            }

            if (StringUtils.isNotBlank(project.getName())) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.name =? ");
                preparedStmtList.add(project.getName());
            }

            if (StringUtils.isNotBlank(project.getProjectType())) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.project_type=? ");
                preparedStmtList.add(project.getProjectType());
            }

            if (StringUtils.isNotBlank(project.getProjectSubType())) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.project_subtype=? ");
                preparedStmtList.add(project.getProjectSubType());
            }

            if (project.getStartDate() != null && project.getStartDate() != 0) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.start_date >= ? ");
                preparedStmtList.add(project.getStartDate());
            }

            if (project.getEndDate() != null && project.getEndDate() != 0) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" prj.end_date <= ? ");
                preparedStmtList.add(project.getEndDate());
            }

            if (lastChangedSince != null && lastChangedSince != 0) {
                addClauseIfRequired(preparedStmtList, queryBuilder);
                queryBuilder.append(" ( prj.last_modified_time >= ? )");
                preparedStmtList.add(lastChangedSince);
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
            queryBuilder.append(" prj.is_deleted = false ");
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
            queryBuilder.append(" ( prj.project_hierarchy LIKE ? )");
            preparedStmtListDescendants.add('%' + projectId + '%');
        }
        
        return queryBuilder.toString();
    }
    
    /* Returns query to get total projects count based on project search params */
    public String getSearchCountQueryString(List<Project> projects, String tenantId, Long lastChangedSince, Boolean includeDeleted, List<Object> preparedStatement) {
        String query = getProjectSearchQuery(projects, config.getMaxLimit(), config.getDefaultOffset(), tenantId, lastChangedSince, includeDeleted, preparedStatement, true);
        return query;
    }

}
