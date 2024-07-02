package org.egov.common.data.repository;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.producer.Producer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.ReflectionUtils;

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

import static org.egov.common.utils.CommonUtils.getIdMethod;
import static org.egov.common.utils.CommonUtils.getMethod;
import static org.egov.common.utils.CommonUtils.getObjClass;

/**
 * Generic Repository Class for common data operations.
 *
 * @param <T> The type of entity this repository deals with.
 */
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

    /**
     * Finds entities by their IDs.
     *
     * @param ids The list of IDs to search for.
     * @return A list of entities found by the given IDs.
     */
    public List<T> findById(List<String> ids) {
        return findById(ids, false);
    }

    /**
     * Finds entities by their IDs with an option to include deleted entities.
     *
     * @param ids            The list of IDs to search for.
     * @param includeDeleted Flag to include deleted entities in the search result.
     * @return A list of entities found by the given IDs.
     */
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

    /**
     * Finds entities by their IDs with an option to include deleted entities,
     * using the default column name "id" for ID search.
     *
     * @param ids            The list of IDs to search for.
     * @param includeDeleted Flag to include deleted entities in the search result.
     * @return A list of entities found by the given IDs.
     */
    public List<T> findById(List<String> ids, Boolean includeDeleted) {
        // Delegates to the main findById method with the default column name "id"
        return findById(ids, includeDeleted, "id");
    }

    /**
     * Finds entities by their IDs with options to include deleted entities and specify a column name.
     *
     * @param ids            The list of IDs to search for.
     * @param includeDeleted Flag to include deleted entities in the search result.
     * @param columnName     The name of the column to search IDs in.
     * @return A list of entities found by the given IDs.
     */
    public List<T> findById(List<String> ids, Boolean includeDeleted, String columnName) {
        List<T> objFound = findInCache(ids);

        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            Method isDeleted = getMethod("getIsDeleted", getObjClass(objFound));
            if (!includeDeleted) {
                objFound = objFound.stream()
                        .filter(entity -> Objects.equals(ReflectionUtils.invokeMethod(isDeleted, entity), false))
                        .collect(Collectors.toList());
            }
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

    /**
     * Saves entities to Kafka and caches them.
     *
     * @param objects The list of entities to save.
     * @param topic   The Kafka topic to push the entities to.
     * @return The list of saved entities.
     */
    public List<T> save(List<T> objects, String topic) {
        producer.push(topic, objects);
        log.info("Pushed to kafka");
        putInCache(objects);
        log.info("Saved to cache");
        return objects;
    }

    /**
     * Saves entities to Kafka, caches them with specified cache key.
     *
     * @param objects  The list of entities to save.
     * @param topic    The Kafka topic to push the entities to.
     * @param cacheKey The cache key to use for caching the entities.
     * @return The list of saved entities.
     */
    public List<T> save(List<T> objects, String topic, String cacheKey) {
        producer.push(topic, objects);
        log.info("Pushed to kafka");
        putInCache(objects, cacheKey);
        log.info("Saved to cache");
        return objects;
    }

    // Cache objects by key
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
            log.warn("Error while saving to cache: {}", ExceptionUtils.getStackTrace(exception));
        }
    }

    /**
     * Puts objects in cache.
     *
     * @param objects The list of objects to put in cache.
     */
    public void putInCache(List<T> objects) {
        if(objects == null || objects.isEmpty()) {
            return;
        }

        cacheByKey(objects, "clientReferenceId");
        // cacheByKey(objects, "id");
    }

    /**
     * Puts objects in cache with specified cache key.
     *
     * @param objects The list of objects to put in cache.
     * @param key     The cache key to use for caching the objects.
     */
    public void putInCache(List<T> objects, String key) {
        if(objects == null || objects.isEmpty()) {
            return;
        }

        cacheByKey(objects, key);
    }

    /**
     * Finds entities based on search criteria.
     *
     * @param searchObject     The object containing search criteria.
     * @param limit            The maximum number of entities to return.
     * @param offset           The offset for pagination.
     * @param tenantId         The tenant ID to filter entities.
     * @param lastChangedSince The timestamp for last modified entities.
     * @param includeDeleted   Flag to include deleted entities in the search result.
     * @return A list of entities found based on the search criteria.
     * @throws QueryBuilderException If an error occurs while building the query.
     */
    public List<T> find(Object searchObject,
                        Integer limit,
                        Integer offset,
                        String tenantId,
                        Long lastChangedSince,
                        Boolean includeDeleted) throws QueryBuilderException {
        String query = selectQueryBuilder.build(searchObject, tableName);
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

    /**
     * Validates IDs against existing entities.
     *
     * @param idsToValidate The list of IDs to validate.
     * @param columnName    The name of the column containing IDs.
     * @return A list of valid IDs.
     */
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
