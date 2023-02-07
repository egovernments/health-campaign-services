package org.egov.project.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.project.repository.rowmapper.ProjectTaskRowMapper;
import org.egov.project.repository.rowmapper.TaskResourceRowMapper;
import org.egov.project.web.models.Task;
import org.egov.project.web.models.TaskResource;
import org.egov.project.web.models.TaskSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdList;

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

    public List<Task> find(TaskSearch searchObject, Integer limit, Integer offset, String tenantId,
                           Long lastChangedSince, Boolean includeDeleted) throws QueryBuilderException {
        String query = "SELECT * FROM project_task pt  LEFT JOIN address a ON pt.addressid = a.id";
        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject,
                QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id=:id", "pt.id=:id");

        query = query + " and pt.tenantId=:tenantId ";
        if (Boolean.FALSE.equals(includeDeleted)) {
            query = query + "and isDeleted=:isDeleted ";
        }

        if (lastChangedSince != null) {
            query = query + "and lastModifiedTime>=:lastModifiedTime ";
        }
        query = query + "ORDER BY pt.id ASC LIMIT :limit OFFSET :offset";
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        List<Task> taskList = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);
        fetchAndSetTaskResource(taskList);
        return taskList;
    }

    private void fetchAndSetTaskResource(List<Task> taskList) {
        if (taskList.isEmpty()) {
            return;
        }
        List<String> taskIds = getIdList(taskList);
        Map<String, Object> resourceParamsMap = new HashMap<>();
        String resourceQuery = "SELECT * FROM task_resource tr where tr.taskid IN (:taskIds)";
        resourceParamsMap.put("taskIds", taskIds);
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

    public List<Task> findById(List<String> ids, String columnName, Boolean includeDeleted) {
        List<Task> objFound;
        Map<Object, Object> redisMap = this.redisTemplate.opsForHash().entries(tableName);
        List<String> foundInCache = ids.stream().filter(redisMap::containsKey).collect(Collectors.toList());
        objFound = foundInCache.stream().map(id -> (Task)redisMap.get(id)).collect(Collectors.toList());
        log.info("Cache hit: {}", !objFound.isEmpty());
        ids.removeAll(foundInCache);
        if (ids.isEmpty()) {
            return objFound;
        }

        String query = String.format("SELECT * FROM project_task pt LEFT JOIN address a ON pt.addressid = a.id WHERE pt.%s IN (:ids) AND isDeleted = false", columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT * FROM project_task pt LEFT JOIN address a ON pt.addressid = a.id  WHERE pt.%s IN (:ids)", columnName);
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        List<Task> taskList = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        fetchAndSetTaskResource(taskList);
        objFound.addAll(taskList);
        putInCache(objFound);
        return objFound;
    }
}
