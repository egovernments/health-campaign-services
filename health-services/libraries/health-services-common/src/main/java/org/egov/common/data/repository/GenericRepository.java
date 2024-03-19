package org.egov.common.data.repository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.producer.Producer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;

@Slf4j
public abstract class GenericRepository<T> {

    protected final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    protected final Producer producer;

    protected final RedisTemplate<String, Object> redisTemplate;

    protected final SelectQueryBuilder selectQueryBuilder;

    protected final RowMapper<T> rowMapper;

    protected String tableName;

    @Value("${spring.cache.redis.time-to-live:60}")
    private String timeToLive;

    protected GenericRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                RedisTemplate<String, Object> redisTemplate,
                                SelectQueryBuilder selectQueryBuilder, RowMapper<T> rowMapper,
                                Optional<String> tableName) {
        this.producer = producer;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.selectQueryBuilder = selectQueryBuilder;
        this.rowMapper = rowMapper;
        tableName.ifPresent(tb -> this.tableName = tb);
    }

    public List<T> findById(List<String> ids) {
        return findById(ids, false);
    }

    protected List<T> findInCache(List<String> ids) {
        ArrayList<T> objFound = new ArrayList<>();
        Collection<Object> collection = ids.stream().filter(Objects::nonNull)
                .collect(Collectors.toList());
        log.info("Searching in cache");
        List<Object> objFromCache = redisTemplate.opsForHash()
                .multiGet(tableName, collection).stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (!objFromCache.isEmpty()) {
            if (objFromCache.size() == 1 && objFromCache.contains(null)) {
                log.info("Cache miss");
            } else {
                log.info("Cache hit, {} items found", objFromCache.size());
                objFound = (ArrayList<T>) objFromCache.stream().map(Object.class::cast)
                        .collect(Collectors.toList());
            }
        } else {
            log.info("Cache miss");
        }
        return objFound;
    }

    public List<T> findById(List<String> ids, Boolean includeDeleted) {
        return findById(ids, includeDeleted, "id");
    }

    public List<T> findById(List<String> ids, Boolean includeDeleted, String columnName) {
        List<T> objFound = findInCache(ids);

        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            Method isDeleted = getMethod("getIsDeleted", getObjClass(objFound));
            objFound = objFound.stream()
                    .filter(entity -> Objects.equals(ReflectionUtils.invokeMethod(isDeleted, entity), includeDeleted))
                    .collect(Collectors.toList());
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));
            if (ids.isEmpty()) {
                return objFound;
            }
        }

        String query = String.format("SELECT * FROM %s WHERE %s IN (:ids) AND isDeleted = false", tableName, columnName);
        if (null != includeDeleted && includeDeleted) {
            query = String.format("SELECT * FROM %s WHERE %s IN (:ids)", tableName, columnName);
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);

        objFound.addAll(namedParameterJdbcTemplate.query(query, paramMap, rowMapper));
        putInCache(objFound);

        return objFound;
    }

    public List<T> save(List<T> objects, String topic) {
        producer.push(topic, objects);
        log.info("Pushed to kafka");
        putInCache(objects);
        log.info("Saved to cache");
        return objects;
    }

    public Object save(Object object, String topic, String fieldName){
        producer.push(topic, object);
        log.info("Pushed to kafka");
        List<T> objList = extractField(object, fieldName);
        putInCache(objList);
        log.info("Saved to cache");
        return object;
    }

    public static <T> List<T> extractField(Object object, String fieldName) {
        List<T> extractedValues = new ArrayList<>();
        if (object == null || fieldName == null || fieldName.isEmpty()) {
            return extractedValues;
        }
        try {
            Class<?> clazz = object.getClass();
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(object);
            if (value != null) {
                if (value instanceof List<?>) {
                    for (Object element : (List<?>) value) {
                        extractedValues.add((T) element);
                    }
                } else {
                    extractedValues.add((T) value);
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return extractedValues;
    }

    public List<T> save(List<T> objects, String topic, String cacheKey) {
        producer.push(topic, objects);
        log.info("Pushed to kafka");
        putInCache(objects, cacheKey);
        log.info("Saved to cache");
        return objects;
    }

    protected void cacheByKey(List<T> objects, String fieldName) {
        try{
            Method getIdMethod = getIdMethod(objects, fieldName);
            if (ReflectionUtils.invokeMethod(getIdMethod, objects.stream().findAny().get()) != null) {
                Map<String, T> objMap = objects.stream()
                        .collect(Collectors
                                .toMap(obj -> {
                                            String str =  (String) ReflectionUtils.invokeMethod(getIdMethod, obj);
                                            log.info("Caching the {}: {}", getIdMethod.getName(), str);
                                            return str;
                                        }
                                        ,
                                        obj -> obj,
                                        // in case of duplicates pick the latter
                                        (obj1, obj2) -> obj2));
                redisTemplate.opsForHash().putAll(tableName, objMap);
                redisTemplate.expire(tableName, Long.parseLong(timeToLive), TimeUnit.SECONDS);
            }
        } catch (Exception exception) {
            log.warn("Error while saving to cache: {}", exception.getMessage());
        }
    }


    public void putInCache(List<T> objects) {
        if(objects == null || objects.isEmpty()) {
            return;
        }

        cacheByKey(objects, "clientReferenceId");
        // cacheByKey(objects, "id");
    }


    public void putInCache(List<T> objects, String key) {
        if(objects == null || objects.isEmpty()) {
            return;
        }

        cacheByKey(objects, key);
    }

    public List<T> find(Object searchObject,
                              Integer limit,
                              Integer offset,
                              String tenantId,
                              Long lastChangedSince,
                              Boolean includeDeleted) throws QueryBuilderException {
        String query = selectQueryBuilder.build(searchObject);
        query += " AND tenantId=:tenantId ";
        if (query.contains(tableName + " AND")) {
            query = query.replace(tableName + " AND", tableName + " WHERE");
        }
        if (Boolean.FALSE.equals(includeDeleted)) {
            query += "AND isDeleted=:isDeleted ";
        }
        if (lastChangedSince != null) {
            query += "AND lastModifiedTime>=:lastModifiedTime ";
        }
        query += "ORDER BY id ASC LIMIT :limit OFFSET :offset";
        Map<String, Object> paramsMap = selectQueryBuilder.getParamsMap();
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        return namedParameterJdbcTemplate.query(query, paramsMap, rowMapper);
    }

    public List<String> validateIds(List<String> idsToValidate, String columnName){
        List<T> validIds = findById(idsToValidate, false, columnName);
        if (validIds.isEmpty()) {
            return Collections.emptyList();
        }
        Method idMethod = getIdMethod(validIds, columnName);
        return validIds.stream().map((obj) -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                .collect(Collectors.toList());
    }
}
