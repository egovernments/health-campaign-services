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
import org.egov.common.models.core.SearchResponse;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskResource;
import org.egov.common.models.project.TaskSearch;
import org.egov.common.producer.Producer;
import org.egov.common.utils.CommonUtils;
import org.egov.project.repository.rowmapper.ClosedHouseholdTaskRowMapper;
import org.egov.project.repository.rowmapper.TaskResourceRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;

@Repository
@Slf4j
public class ClosedHouseholdTaskRepository  extends GenericRepository<Task> {

    @Autowired
    private TaskResourceRowMapper taskResourceRowMapper;

    @Autowired
    protected ClosedHouseholdTaskRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                    RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder,
                                    ClosedHouseholdTaskRowMapper rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, rowMapper, Optional.of("closed_household"));
    }

    public SearchResponse<Task> find(TaskSearch searchObject, Integer limit, Integer offset, String tenantId,
                                     Long lastChangedSince, Boolean includeDeleted) throws QueryBuilderException {
        String query = "SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM closed_household ch  LEFT JOIN address a ON ch.addressid = a.id";
        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject,
                QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "ch.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "ch.clientReferenceId IN (:clientReferenceId)");

        if(CollectionUtils.isEmpty(whereFields)) {
            query = query + " where ch.tenantId=:tenantId ";
        } else {
            query = query + " and ch.tenantId=:tenantId ";
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

        Long totalCount = CommonUtils.constructTotalCountCTEAndReturnResult(query, paramsMap, this.namedParameterJdbcTemplate);

        query = query + "ORDER BY ch.id ASC LIMIT :limit OFFSET :offset";
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        List<Task> taskList = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);
        fetchAndSetTaskResource(taskList);

        return SearchResponse.<Task>builder().response(taskList).totalCount(totalCount).build();
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

    public SearchResponse<Task> findById(List<String> ids, String columnName, Boolean includeDeleted) {
        List<Task> objFound = findInCache(ids);
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

        String query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM closed_household ch LEFT JOIN address a ON ch.addressid = a.id WHERE ch.%s IN (:ids) AND isDeleted = false", columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT *, a.id as aid,a.tenantid as atenantid, a.clientreferenceid as aclientreferenceid FROM closed_household ch LEFT JOIN address a ON ch.addressid = a.id  WHERE ch.%s IN (:ids)", columnName);
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        List<Task> taskList = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        fetchAndSetTaskResource(taskList);
        objFound.addAll(taskList);
        putInCache(objFound);
        return SearchResponse.<Task>builder().response(objFound).build();
    }
}
