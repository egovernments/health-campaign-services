package org.egov.project.repository;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskResource;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.producer.Producer;
import org.egov.common.utils.CommonUtils;
import org.egov.project.repository.rowmapper.ProjectTaskRowMapper;
import org.egov.project.repository.rowmapper.TaskResourceRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

@Repository
@Slf4j
public class ProjectTaskRepository extends GenericRepository<Task> {

    @Autowired
    private TaskResourceRowMapper taskResourceRowMapper;

    @Autowired
    protected ProjectTaskRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                    RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder,
                                    ProjectTaskRowMapper rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, rowMapper, Optional.of("project_task"));
    }

    /**
     * Finds and retrieves a list of Task entities based on the given search parameters. The results are paginated
     * and filtered according to the specified conditions.
     *
     * @param searchObject the search criteria encapsulated in a TaskSearch object
     * @param limit the maximum number of results to retrieve
     * @param offset the starting index for the result set
     * @param tenantId the identifier for the tenant, used for schema-specific queries
     * @param lastChangedSince the timestamp for fetching tasks that were modified after this time
     * @param includeDeleted flag indicating whether to include deleted tasks in the result set
     * @return a SearchResponse containing the list of matched Task entities and the total count of results
     * @throws QueryBuilderException if there is an error in generating the query
     * @throws InvalidTenantIdException if the provided tenant ID is invalid
     */
    public SearchResponse<Task> find(TaskSearch searchObject, Integer limit, Integer offset, String tenantId,
                           Long lastChangedSince, Boolean includeDeleted) throws QueryBuilderException, InvalidTenantIdException {
        String query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM %s.project_task pt  LEFT JOIN %s.address a ON pt.addressid = a.id", SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING);
        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject,
                QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "pt.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "pt.clientReferenceId IN (:clientReferenceId)");

        if(CollectionUtils.isEmpty(whereFields)) {
            query = query + " where pt.tenantId=:tenantId ";
        } else {
            query = query + " and pt.tenantId=:tenantId ";
        }
        if (Boolean.FALSE.equals(includeDeleted)) {
            query = query + "and isDeleted=:isDeleted ";
        }

        if (lastChangedSince != null) {
            query = query + "and lastModifiedTime>=:lastModifiedTime ";
        }
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        Long totalCount = CommonUtils.constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);

        query = query + "ORDER BY pt.id ASC LIMIT :limit OFFSET :offset";
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        List<Task> taskList = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);
        fetchAndSetTaskResource(tenantId, taskList);

        return SearchResponse.<Task>builder().response(taskList).totalCount(totalCount).build();
    }

    /**
     * Fetches task resources associated with the provided list of tasks for the given tenant ID and sets them in the corresponding tasks.
     * This method performs a database query to retrieve the resources and maps them to their respective tasks.
     *
     * @param tenantId the identifier for the tenant, used for schema-specific queries
     * @param taskList a list of Task objects for which the task resources need to be fetched and set
     * @throws InvalidTenantIdException if the provided tenant ID is invalid
     */
    private void fetchAndSetTaskResource(String tenantId, List<Task> taskList) throws InvalidTenantIdException {
        if (taskList.isEmpty()) {
            return;
        }
        List<String> taskIds = getIdList(taskList);
        Map<String, Object> resourceParamsMap = new HashMap<>();
        String resourceQuery = String.format("SELECT * FROM %s.task_resource tr where tr.taskid IN (:taskIds)", SCHEMA_REPLACE_STRING);
        resourceParamsMap.put("taskIds", taskIds);
        // Replacing schema placeholder with the schema name for the tenant id
        resourceQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(resourceQuery, tenantId);
        List<TaskResource> taskResourceList = this.namedParameterJdbcTemplate.query(resourceQuery, resourceParamsMap,
                this.taskResourceRowMapper);
        Map<String, List<TaskResource>> idToObjMap = new HashMap<>();

        taskResourceList.forEach(taskResource -> {
            String taskId = taskResource.getTaskId();
            if (idToObjMap.containsKey(taskId)) {
                idToObjMap.get(taskId).add(taskResource);
            } else {
                List<TaskResource> taskResources = new ArrayList<>();
                taskResources.add(taskResource);
                idToObjMap.put(taskId, taskResources);
            }
        });
        taskList.forEach(task -> task.setResources(idToObjMap.get(task.getId())));
    }

    /**
     * Retrieves a list of Task entities by their IDs for a specific tenant, with optional filtering based on deletion status.
     * The method attempts to retrieve the data from the cache first, and if not found, queries the underlying database.
     *
     * @param tenantId the identifier for the tenant, used to specify the schema for queries
     * @param ids the list of IDs of the Task entities to be retrieved
     * @param columnName the name of the column corresponding to the IDs in the database
     * @param includeDeleted a flag indicating whether to include deleted entities in the result
     * @return a SearchResponse object containing the list of matched Task entities, along with pagination information
     * @throws InvalidTenantIdException if the provided tenant ID is invalid
     */
    public SearchResponse<Task> findById(String tenantId, List<String> ids, String columnName, Boolean includeDeleted) throws InvalidTenantIdException {
        List<Task> objFound = findInCache(tenantId, ids);
        if (!includeDeleted) {
            objFound = objFound.stream()
                    .filter(entity -> entity.getIsDeleted().equals(false))
                    .collect(Collectors.toList());
        }
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));
            if (ids.isEmpty()) {
                return SearchResponse.<Task>builder().response(objFound).build();
            }
        }

        String query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM %s.project_task pt LEFT JOIN %s.address a ON pt.addressid = a.id WHERE pt.%s IN (:ids) AND isDeleted = false", SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING, columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM %s.project_task pt LEFT JOIN %s.address a ON pt.addressid = a.id  WHERE pt.%s IN (:ids)", SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING, columnName);
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        List<Task> taskList = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        fetchAndSetTaskResource(tenantId, taskList);
        objFound.addAll(taskList);
        putInCache(objFound);
        return SearchResponse.<Task>builder().response(objFound).build();
    }

}
