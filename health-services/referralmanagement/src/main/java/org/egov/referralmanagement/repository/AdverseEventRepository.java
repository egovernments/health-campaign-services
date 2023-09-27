package org.egov.referralmanagement.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.referralmanagement.adverseevent.AdverseEvent;
import org.egov.common.models.referralmanagement.adverseevent.AdverseEventSearch;
import org.egov.common.models.project.Task;
import org.egov.common.producer.Producer;
import org.egov.referralmanagement.repository.rowmapper.AdverseEventRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdList;
import static org.egov.common.utils.CommonUtils.getIdMethod;

@Repository
@Slf4j
public class AdverseEventRepository extends GenericRepository<AdverseEvent> {
    @Autowired
    private AdverseEventRowMapper rowMapper;

    @Autowired
    protected AdverseEventRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                        RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder,
                                        AdverseEventRowMapper rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, rowMapper, Optional.of("adverse_event"));
    }

    public Map<String, List<AdverseEvent>> fetchAdverseEvents(List<Task> taskList) {
        if (taskList.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> taskIds = getIdList(taskList);
        Map<String, Object> resourceParamsMap = new HashMap<>();
        String resourceQuery = "SELECT * FROM adverse_event ae where ae.taskId IN (:taskIds)";
        resourceParamsMap.put("taskIds", taskIds);
        List<AdverseEvent> adverseEventList = this.namedParameterJdbcTemplate.query(resourceQuery, resourceParamsMap,
                this.rowMapper);
        Map<String, List<AdverseEvent>> idToObjMap = new HashMap<>();

        adverseEventList.forEach(adverseEvent -> {
            String taskId = adverseEvent.getTaskId();
            if (idToObjMap.containsKey(taskId)) {
                idToObjMap.get(taskId).add(adverseEvent);
            } else {
                List<AdverseEvent> adverseEvents = new ArrayList<>();
                adverseEvents.add(adverseEvent);
                idToObjMap.put(taskId, adverseEvents);
            }
        });
        return idToObjMap;
    }

    public List<AdverseEvent> find(AdverseEventSearch searchObject, Integer limit, Integer offset, String tenantId,
                                   Long lastChangedSince, Boolean includeDeleted) throws QueryBuilderException {
        String query = "SELECT * FROM adverse_event ae  LEFT JOIN project_task pt ON ae.taskId = pt.id ";
        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject,
                QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "ae.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "ae.clientReferenceId IN (:clientReferenceId)");

        query = query + " and ae.tenantId=:tenantId ";
        if (Boolean.FALSE.equals(includeDeleted)) {
            query = query + "and ae.isDeleted=:isDeleted ";
        }

        if (lastChangedSince != null) {
            query = query + "and as.lastModifiedTime>=:lastModifiedTime ";
        }
        query = query + "ORDER BY ae.id ASC LIMIT :limit OFFSET :offset";
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        List<AdverseEvent> adverseEventList = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);
        return adverseEventList;
    }

    public List<AdverseEvent> findById(List<String> ids, String columnName, Boolean includeDeleted) {
        List<AdverseEvent> objFound = findInCache(ids).stream()
                .filter(entity -> entity.getIsDeleted().equals(includeDeleted))
                .collect(Collectors.toList());
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));
            if (ids.isEmpty()) {
                return objFound;
            }
        }

        String query = String.format("SELECT * FROM adverse_event ae LEFT JOIN project_task pt ON ae.taskid = pt.id WHERE ae.%s IN (:ids) ", columnName);
        if (includeDeleted == null || !includeDeleted) {
            query += " AND ae.isDeleted = false ";
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        List<AdverseEvent> adverseEventList = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        objFound.addAll(adverseEventList);
        putInCache(objFound);
        return objFound;
    }
}
