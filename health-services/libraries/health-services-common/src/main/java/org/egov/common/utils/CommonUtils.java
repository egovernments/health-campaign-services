package org.egov.common.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.ds.Tuple;
import org.egov.common.error.handler.ErrorHandler;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.ApiDetails;
import org.egov.common.models.Error;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.core.URLParams;
import org.egov.common.validator.Validator;
import org.egov.tracer.model.CustomException;
import org.egov.tracer.model.ErrorDetail;
import org.egov.tracer.model.ErrorEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.ValidatorUtils.getErrorForNullId;

@Slf4j
public class CommonUtils {

    public static final String GET_API_OPERATION = "getApiOperation";

    private static final Map<Class<?>, Map<String, Method>> methodCache = new ConcurrentHashMap<>();

    private static ObjectMapper objectMapper = new ObjectMapper();

    private CommonUtils() {
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setTimeZone(TimeZone.getTimeZone("UTC"));
    }


    //TODO To be removed as it is only used by Product service which is now depricated
    @Deprecated
    public static boolean isForUpdate(Object obj) {
        Method getApiOperationMethod = getMethod(GET_API_OPERATION, obj.getClass());
        Object apiOperation = ReflectionUtils.invokeMethod(getApiOperationMethod, obj);
        if (apiOperation == null) {
            return false;
        }
        Method nameMethod = CommonUtils.getMethod("name", Enum.class);
        return "UPDATE".equals(ReflectionUtils.invokeMethod(nameMethod, apiOperation));
    }

    //TODO To be removed as it is only used by Product service which is now depricated
    @Deprecated
    public static boolean isForDelete(Object obj) {
        Method getApiOperationMethod = getMethod(GET_API_OPERATION, obj.getClass());
        Object apiOperation = ReflectionUtils.invokeMethod(getApiOperationMethod, obj);
        if (apiOperation == null) {
            return false;
        }
        Method nameMethod = CommonUtils.getMethod("name", Enum.class);
        return "DELETE".equals(ReflectionUtils.invokeMethod(nameMethod, apiOperation));
    }

    //TODO To be removed as it is only used by Product service which is now depricated
    @Deprecated
    public static boolean isForCreate(Object obj) {
        Method getApiOperationMethod = getMethod(GET_API_OPERATION, obj.getClass());
        Object apiOperation = ReflectionUtils.invokeMethod(getApiOperationMethod, obj);
        if (apiOperation == null) {
            return false;
        }
        Method nameMethod = CommonUtils.getMethod("name", Enum.class);
        String value = (String) ReflectionUtils.invokeMethod(nameMethod, apiOperation);
        return "CREATE".equals(value);
    }

    public static <T, R> Set<T> getSet(List<R> objects, String methodName) {
        return objects.stream().map(o -> (T) ReflectionUtils
                .invokeMethod(getMethod(methodName, o.getClass()), o))
                .collect(Collectors.toSet());
    }

    public static <T> List<T> getDifference(List<T> list, List<T> subList) {
        List<T> newList = new ArrayList<>(list);
        List<T> newSubList = new ArrayList<>(subList);
        if (newList.size() >= newSubList.size()) {
            newList.removeAll(subList);
        }
        return newList;
    }

    //TODO To be removed as it is only used by Product service which is now depricated
    @Deprecated
    public static <T> void validateIds(Set<T> idsToValidate, UnaryOperator<List<T>> validator) {
        List<T> idsToValidateList = new ArrayList<>(idsToValidate);
        List<T> validIds = validator.apply(idsToValidateList);
        List<T> invalidIds = CommonUtils.getDifference(idsToValidateList, validIds);
        if (!invalidIds.isEmpty()) {
            log.error("Invalid IDs {}", invalidIds);
            throw new CustomException("INVALID_ID", invalidIds.toString());
        }
    }

    /**
     * Retrieves audit details for creating an entity.
     * This method generates audit details including creation and last modified timestamps.
     *
     * @param requestInfo The request information containing user details.
     * @return The audit details for create operation.
     */
    public static AuditDetails getAuditDetailsForCreate(RequestInfo requestInfo) {
        log.info("Creating audit details for create api");
        Long time = System.currentTimeMillis();
        return AuditDetails.builder()
                .createdBy(requestInfo.getUserInfo().getUuid())
                .createdTime(time)
                .lastModifiedBy(requestInfo.getUserInfo().getUuid())
                .lastModifiedTime(time)
                .build();
    }

    /**
     * Get auditDetails having only the lastModifiedBy and lastModifiedTime fields set.
     *
     * @param existingAuditDetails is the audit details coming from request.
     * @param modifiedByUuid is the uuid of the user performing this update.
     * @return auditDetails
     */
    public static AuditDetails getAuditDetailsForUpdate(AuditDetails existingAuditDetails, String modifiedByUuid) {
        log.info("Creating audit details for update/delete api");
        if (existingAuditDetails == null) {
            return AuditDetails.builder()
                    .lastModifiedBy(modifiedByUuid)
                    .lastModifiedTime(System.currentTimeMillis()).build();
        } else {
            existingAuditDetails.setLastModifiedBy(modifiedByUuid);
            existingAuditDetails.setLastModifiedTime(System.currentTimeMillis());
            return existingAuditDetails;
        }
    }

    /**
     * Checks if the search is performed by ID only.
     * @param obj The object to check.
     * @return True if the search is by ID only, false otherwise.
     */
    public static boolean isSearchByIdOnly(Object obj) {
        return isSearchByIdOnly(obj, "id");
    }

    /**
     * Checks if the search is performed only by the specified field.
     *
     * @param obj The object to perform the search on.
     * @param fieldName The name of the field to search.
     * @return true if the search is performed only by the specified field, otherwise false.
     */
    public static boolean isSearchByIdOnly(Object obj, String fieldName) {
        // Get the class of the object
        Class<?> objClass = obj.getClass();
        // Capitalize the first letter of the field name
        String propertyName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        // Get the method to set the field
        Method setFieldMethod = getMethod("set" + propertyName, objClass);
        // Get the method to get the field value
        Method getFieldMethod = getMethod("get" + propertyName, objClass);

        // Create a new instance of the object
        Object finalObject = null;
        try {
            finalObject = objClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            // Throw a runtime exception if instantiation fails
            throw new RuntimeException(e);
        }

        // Get the ID of the object
        Object id = ReflectionUtils.invokeMethod(getFieldMethod, obj);
        // If ID is null, return false
        if (id == null) {
            return false;
        }

        // Set the ID to the final object
        ReflectionUtils.invokeMethod(setFieldMethod, finalObject, id);

        // If the object is an instance of URLParams, set common properties
        if (obj instanceof URLParams) {
            URLParams urlParamsObj = ((URLParams) obj);
            URLParams finalUrlParamsObj = ((URLParams) finalObject);
            finalUrlParamsObj.setIncludeDeleted(urlParamsObj.getIncludeDeleted());
            finalUrlParamsObj.setTenantId(urlParamsObj.getTenantId());
            finalUrlParamsObj.setOffset(urlParamsObj.getOffset());
            finalUrlParamsObj.setLimit(urlParamsObj.getLimit());
            finalUrlParamsObj.setLastChangedSince(urlParamsObj.getLastChangedSince());
        }

        // compare both objects
        return areObjectsEqual(obj, finalObject);
    }

    public static boolean areObjectsEqual(Object obj1, Object obj2) {
        if (obj1 == null || obj2 == null) {
            return false;
        }

        // Get the class of the objects
        Class<?> objClass = obj1.getClass();

        // Iterate through all fields in the class, including parent fields
        StringBuilder obj1Fields = new StringBuilder();
        StringBuilder obj2Fields = new StringBuilder();

        while(objClass.getSuperclass() != null) {
            for (Field field : objClass.getDeclaredFields()) {
                field.setAccessible(true); // Ensure private fields are accessible

                try {
                    // Get the value of the field for each object
                    Object value1 = field.get(obj1);
                    Object value2 = field.get(obj2);

                    // Append the field name and value to the string representation
                    obj1Fields.append(field.getName()).append(":").append(value1).append(",");
                    obj2Fields.append(field.getName()).append(":").append(value2).append(",");
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            objClass = objClass.getSuperclass();
        }

        // Compare the string representations of the objects
        return obj1Fields.toString().equals(obj2Fields.toString());
    }


    //TODO To be removed as it is only used by Product service which is now depricated
    @Deprecated
    public static <T> void checkRowVersion(Map<String, T> idToObjMap, List<T> objList) {
        Class<?> objClass = getObjClass(objList);
        checkRowVersion(idToObjMap, objList, getMethod("getId", objClass));
    }

    //TODO To be removed as it is only used by Product service which is now depricated
    @Deprecated
    public static <T> void checkRowVersion(Map<String, T> idToObjMap, List<T> objList, Method idMethod) {
        Class<?> objClass = getObjClass(objList);
        Method rowVersionMethod = getMethod("getRowVersion", objClass);
        Set<Object> rowVersionMismatch = objList.stream()
                .filter(obj -> !Objects.equals(ReflectionUtils.invokeMethod(rowVersionMethod, obj),
                        ReflectionUtils.invokeMethod(rowVersionMethod,
                                idToObjMap.get(ReflectionUtils.invokeMethod(idMethod, obj)))))
                .map(obj -> ReflectionUtils.invokeMethod(idMethod, obj)).collect(Collectors.toSet());
        if (!rowVersionMismatch.isEmpty()) {
            log.error("Mismatch in row versions {}", rowVersionMismatch);
            throw new CustomException("ROW_VERSION_MISMATCH", rowVersionMismatch.toString());
        }
    }

    /**
     * Retrieves entities from a list with mismatched row versions compared to a map of IDs to objects.
     * @param idToObjMap A map of IDs to objects.
     * @param objList The list of objects to check.
     * @param idMethod The method to retrieve the ID of an object.
     * @param <T> The type of objects in the list.
     * @return A list of entities with mismatched row versions.
     */
    public static <T> List<T> getEntitiesWithMismatchedRowVersion(Map<String, T> idToObjMap,
                                                                  List<T> objList, Method idMethod) {
        // Get the class of objects in the list
        Class<?> objClass = getObjClass(objList);
        // Get the method to retrieve the row version
        Method rowVersionMethod = getMethod("getRowVersion", objClass);
        // Filter the object list to include only those with mismatched row versions
        return objList.stream()
                .filter(obj -> !Objects.equals(ReflectionUtils.invokeMethod(rowVersionMethod, obj),
                        ReflectionUtils.invokeMethod(rowVersionMethod,
                                idToObjMap.get(ReflectionUtils.invokeMethod(idMethod, obj)))))
                .map(obj -> idToObjMap.get(ReflectionUtils.invokeMethod(idMethod, obj)))
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the tenant ID from a list of objects.
     * @param objList The list of objects from which to retrieve the tenant ID.
     * @param <T> The type of objects in the list.
     * @return The tenant ID.
     */
    public static <T> String getTenantId(List<T> objList) {
        // Retrieve any object from the list
        Object obj = objList.stream().findAny().get();
        // Get the method to retrieve the tenant ID
        Method getTenantIdMethod = getMethod("getTenantId", obj.getClass());
        // Invoke the method to retrieve the tenant ID
        String tenantId = (String) ReflectionUtils.invokeMethod(getTenantIdMethod, obj);
        // Log the retrieved tenant ID
        log.info("tenantId is {}", tenantId);
        return tenantId;
    }

    /**
     * Enriches objList with requestInfo, auditDetails, rowVersion and sets idDeleted to FALSE.
     *
     * <p>It also enriches the system generated ids in the objList. To be used for create APIs.
     *
     * @param objList is list of objects
     * @param idList is the list of system generated ids
     * @param requestInfo is requestInfo, usually part of the request object
     * @param <T> is any type that has an id field, auditDetails field, rowVersion field and isDeleted field with setters and getters
     */
    public static <T> void enrichForCreate(List<T> objList, List<String> idList, RequestInfo requestInfo) {
        enrichForCreate(objList, idList, requestInfo, true);
    }

    /**
     * Enriches objList with requestInfo, auditDetails, rowVersion and sets idDeleted to FALSE.
     *
     * <p>It also enriches the system generated ids in the type objList.
     * This method updates rowVersion if and only if the updateRowVersion param is set.
     * To be used for create APIs.
     *
     * @param objList is list of objects with type objList
     * @param idList is the list of system generated ids
     * @param requestInfo is requestInfo, usually part of the request object
     * @param updateRowVersion denoting whether to update rowVersion or not
     * @param <T> is any type that has an id field, auditDetails field, rowVersion field and isDeleted field with setters and getters
     */
    //TODO COMPARE the size and throw error when objList and idList are not equal
    public static <T> void enrichForCreate(List<T> objList, List<String> idList, RequestInfo requestInfo,
                                           boolean updateRowVersion) {
        AuditDetails auditDetails = getAuditDetailsForCreate(requestInfo);
        Class<?> objClass = getObjClass(objList);
        Method setIdMethod = getMethod("setId", objClass);
        Method setAuditDetailsMethod = getMethod("setAuditDetails", objClass);
        Method setIsDeletedMethod = getMethod("setIsDeleted", objClass);
        IntStream.range(0, objList.size())
                .forEach(i -> {
                    final Object obj = objList.get(i);
                    ReflectionUtils.invokeMethod(setIdMethod, obj, idList.get(i));
                    ReflectionUtils.invokeMethod(setAuditDetailsMethod, obj, auditDetails);
                    if (updateRowVersion) {
                        Method setRowVersionMethod = getMethod("setRowVersion", objClass);
                        ReflectionUtils.invokeMethod(setRowVersionMethod, obj, 1);
                    }
                    ReflectionUtils.invokeMethod(setIsDeletedMethod, obj, Boolean.FALSE);
                });
    }

    /**
     * Retrieves the ID method from a list of objects, prioritizing "id" and "clientReferenceId" fields.
     * @param objList The list of objects from which to retrieve the ID method.
     * @param <T> The type of objects in the list.
     * @return The ID method.
     */
    public static <T> Method getIdMethod(List<T> objList) {
        return getIdMethod(objList, "id", "clientReferenceId");
    }

    /**
     * Retrieves the ID method from a list of objects based on a specified ID field name.
     * @param objList The list of objects from which to retrieve the ID method.
     * @param idFieldName The name of the ID field.
     * @param <T> The type of objects in the list.
     * @return The ID method.
     */
    public static <T> Method getIdMethod(List<T> objList, String idFieldName) {
        // Construct the method name based on the ID field name
        String idMethodName = "get" + idFieldName.substring(0, 1).toUpperCase() + idFieldName.substring(1);
        // Get the ID method using the constructed method name
        return getMethod(idMethodName, getObjClass(objList));
    }

    /**
     * Retrieves the ID method from a list of objects based on specified ID and client reference ID field names.
     * @param objList The list of objects from which to retrieve the ID method.
     * @param idField The name of the ID field.
     * @param clientReferenceIdField The name of the client reference ID field.
     * @param <T> The type of objects in the list.
     * @return The ID method.
     */
    public static <T> Method getIdMethod(List<T> objList, String idField, String clientReferenceIdField) {
        // Construct the method names based on the specified field names
        String idMethodName = "get" + idField.substring(0, 1).toUpperCase() + idField.substring(1);
        String clientReferenceIdMethodName = "get" + clientReferenceIdField.substring(0, 1).toUpperCase() + clientReferenceIdField.substring(1);
        try {
            // Attempt to retrieve the ID method
            Method getId = getMethod(idMethodName, getObjClass(objList));
            // Invoke the ID method on an object from the list to check if it returns a non-null value
            Object value = ReflectionUtils.invokeMethod(getId, objList.stream().findAny().get());
            // If the value is not null, return the ID method
            if (value != null) {
                return getId;
            }
        } catch (CustomException e) {
            // Log and handle any custom exceptions
            log.error(e.getMessage());
        }
        // If the ID method does not return a non-null value, return the client reference ID method
        return getMethod(clientReferenceIdMethodName, getObjClass(objList));
    }


    /**
     * Enriches the objects in a list with IDs from a corresponding list of IDs.
     * @param objList The list of objects to enrich with IDs.
     * @param idList The list of IDs to use for enrichment.
     * @param <T> The type of objects in the list.
     */
    public static <T> void enrichId(List<T> objList, List<String> idList) {
        // Get the class of objects in the list
        Class<?> objClass = getObjClass(objList);
        // Get the method to set the ID
        Method setIdMethod = getMethod("setId", objClass);
        // Iterate over the indices of the object list
        IntStream.range(0, objList.size())
                .forEach(i -> {
                    // Get the object at the current index
                    final Object obj = objList.get(i);
                    // Invoke the method to set the ID on the object using the corresponding ID from the ID list
                    ReflectionUtils.invokeMethod(setIdMethod, obj, idList.get(i));
                });
    }

    /**
     * Enriches objects for update based on a map of IDs to objects and a request object.
     * @param idToObjMap A map of IDs to objects.
     * @param request The request object.
     * @param <T> The type of objects in the map.
     */
    public static <T> void enrichForUpdate(Map<String, T> idToObjMap, Object request) {
        // Get the class of objects in the map
        Class<?> objClass = getObjClass(Arrays.asList(idToObjMap.values().toArray()));
        // Get the class of the request object
        Class<?> requestObjClass = request.getClass();
        // Get methods related to row version, audit details, and request information
        Method getRowVersionMethod = getMethod("getRowVersion", objClass);
        Method setRowVersionMethod = getMethod("setRowVersion", objClass);
        Method setAuditDetailsMethod = getMethod("setAuditDetails", objClass);
        Method getAuditDetailsMethod = getMethod("getAuditDetails", objClass);
        Method getRequestInfoMethod = getMethod("getRequestInfo", requestObjClass);
        // Iterate over the keys (IDs) in the map
        idToObjMap.keySet().forEach(i -> {
            // Get the object corresponding to the current ID
            Object obj = idToObjMap.get(i);
            // Retrieve row version and update it
            Integer rowVersion = (Integer) ReflectionUtils.invokeMethod(getRowVersionMethod, obj);
            ReflectionUtils.invokeMethod(setRowVersionMethod, obj, rowVersion + 1);
            // Retrieve request information
            RequestInfo requestInfo = (RequestInfo) ReflectionUtils.invokeMethod(getRequestInfoMethod, request);
            // Retrieve existing audit details and update them
            AuditDetails existingAuditDetails = (AuditDetails) ReflectionUtils.invokeMethod(getAuditDetailsMethod, obj);
            AuditDetails auditDetailsForUpdate = getAuditDetailsForUpdate(existingAuditDetails, requestInfo.getUserInfo().getUuid());
            ReflectionUtils.invokeMethod(setAuditDetailsMethod, obj, auditDetailsForUpdate);
        });
    }

    /**
     * Enriches objects for update based on a map of IDs to objects, a list of existing objects, a request object, and an ID method.
     * @param idToObjMap A map of IDs to objects.
     * @param existingObjList The list of existing objects.
     * @param request The request object.
     * @param idMethod The method to retrieve the ID.
     * @param <T> The type of objects in the list.
     */
    public static <T> void enrichForUpdate(Map<String, T> idToObjMap, List<T> existingObjList, Object request, Method idMethod) {
        // Get the class of objects in the list
        Class<?> objClass = getObjClass(existingObjList);
        // Get the class of the request object
        Class<?> requestObjClass = request.getClass();
        // Get methods related to deletion, row version, audit details, and request information
        Method setIsDeletedMethod = getMethod("setIsDeleted", objClass);
        Method getRowVersionMethod = getMethod("getRowVersion", objClass);
        Method setRowVersionMethod = getMethod("setRowVersion", objClass);
        Method getAuditDetailsMethod = getMethod("getAuditDetails", objClass);
        Method setAuditDetailsMethod = getMethod("setAuditDetails", objClass);
        Method getRequestInfoMethod = getMethod("getRequestInfo", requestObjClass);
        // Iterate over the indices of the existing object list
        IntStream.range(0, existingObjList.size()).forEach(i -> {
            // Get the object corresponding to the current index
            Object obj = idToObjMap.get(ReflectionUtils.invokeMethod(idMethod, existingObjList.get(i)));
            try {
                // Get the API operation method and API operation name
                Method getApiOperationMethod = getMethod(GET_API_OPERATION, requestObjClass);
                Object apiOperation = ReflectionUtils.invokeMethod(getApiOperationMethod, request);
                Method nameMethod = CommonUtils.getMethod("name", Enum.class);
                // If the API operation is DELETE, set the object's "isDeleted" flag to true
                if ("DELETE".equals(ReflectionUtils.invokeMethod(nameMethod, apiOperation))) {
                    ReflectionUtils.invokeMethod(setIsDeletedMethod, obj, true);
                }
            } catch (Exception exception) {
                // Do nothing; remove later
            }
            // Retrieve row version and update it
            Integer rowVersion = (Integer) ReflectionUtils.invokeMethod(getRowVersionMethod, obj);
            ReflectionUtils.invokeMethod(setRowVersionMethod, obj, rowVersion + 1);
            // Retrieve request information
            RequestInfo requestInfo = (RequestInfo) ReflectionUtils.invokeMethod(getRequestInfoMethod, request);
            // Retrieve existing audit details and update them
            AuditDetails existingAuditDetails = (AuditDetails) ReflectionUtils.invokeMethod(getAuditDetailsMethod, existingObjList.get(i));
            AuditDetails auditDetailsForUpdate = getAuditDetailsForUpdate(existingAuditDetails, requestInfo.getUserInfo().getUuid());
            ReflectionUtils.invokeMethod(setAuditDetailsMethod, obj, auditDetailsForUpdate);
        });
    }

    /**
     * Enriches objects for update based on a map of IDs to objects, a list of existing objects, and a request object.
     *
     * @param idToObjMap      A map of IDs to objects.
     * @param existingObjList The list of existing objects.
     * @param request         The request object.
     * @param <T>             The type of objects in the list.
     */
    public static <T> void enrichForUpdate(Map<String, T> idToObjMap, List<T> existingObjList, Object request) {
        Class<?> objClass = getObjClass(existingObjList);
        Method getIdMethod = getMethod("getId", objClass);

        enrichForUpdate(idToObjMap, existingObjList, request, getIdMethod);
    }


    /**
     * Creates a map of IDs to objects using the default ID method.
     * @param objList The list of objects from which to create the map.
     * @param <T> The type of objects in the list.
     * @return A map of IDs to objects.
     */
    public static <T> Map<String, T> getIdToObjMap(List<T> objList) {
        // Get the class of objects in the list
        Class<?> objClass = getObjClass(objList);
        // Get the default ID method
        Method idMethod = getMethod("getId", objClass);
        // Delegate to the overloaded method to create the map
        return getIdToObjMap(objList, idMethod);
    }

    /**
     * Creates a map of IDs to objects using the specified ID method.
     * @param objList The list of objects from which to create the map.
     * @param idMethod The method to retrieve the ID from an object.
     * @param <T> The type of objects in the list.
     * @return A map of IDs to objects.
     */
    public static <T> Map<String, T> getIdToObjMap(List<T> objList, Method idMethod) {
        // Collect the objects into a map using the specified ID method
        return objList.stream().collect(Collectors.toMap(
                obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj),
                obj -> obj,
                (obj1, obj2) -> obj2
        ));
    }


    /**
     * Validates entities by comparing the number of entities in the request map with those in the database list.
     * @param idToObjInRequestMap A map of IDs to objects in the request.
     * @param objInDbList The list of objects in the database.
     * @param <T> The type of objects in the lists.
     */
    public static <T> void validateEntities(Map<String, T> idToObjInRequestMap, List<T> objInDbList) {
        // Check if the number of entities in the request map exceeds those in the database list
        if (idToObjInRequestMap.size() > objInDbList.size()) {
            // Get the list of IDs for objects in the database
            List<String> idsForObjInDb = getIdList(objInDbList);
            // Identify the IDs for invalid objects in the request map
            List<String> idsForInvalidObj = idToObjInRequestMap.keySet().stream()
                    .filter(id -> !idsForObjInDb.contains(id))
                    .collect(Collectors.toList());
            // Log and throw an exception for the invalid entities
            log.error("Invalid entities {}", idsForInvalidObj);
            throw new CustomException("INVALID_ENTITY", idsForInvalidObj.toString());
        }
    }

    /**
     * Validates entities by comparing the number of entities in the request map with those in the database list,
     * using a specified ID retrieval method.
     * @param idToObjInRequestMap A map of IDs to objects in the request.
     * @param objInDbList The list of objects in the database.
     * @param idMethod The method to retrieve the ID from an object.
     * @param <T> The type of objects in the lists.
     */
    public static <T> void validateEntities(Map<String, T> idToObjInRequestMap, List<T> objInDbList, Method idMethod) {
        // Check if the number of entities in the request map exceeds those in the database list
        if (idToObjInRequestMap.size() > objInDbList.size()) {
            // Get the list of IDs for objects in the database using the specified ID retrieval method
            List<String> idsForObjInDb = getIdList(objInDbList, idMethod);
            // Identify the IDs for invalid objects in the request map
            List<String> idsForInvalidObj = idToObjInRequestMap.keySet().stream()
                    .filter(id -> !idsForObjInDb.contains(id))
                    .collect(Collectors.toList());
            // Log and throw an exception for the invalid entities
            log.error("Invalid entities {}", idsForInvalidObj);
            throw new CustomException("INVALID_ENTITY", idsForInvalidObj.toString());
        }
    }


    /**
     * Checks for non-existent entities in the request map based on the ID method, comparing them with entities in the database list.
     * @param idToObjInRequestMap A map of IDs to objects in the request.
     * @param objInDbList The list of objects in the database.
     * @param idMethod The method to retrieve the ID from an object.
     * @param <T> The type of objects in the lists.
     * @return A list of entities from the request map that do not exist in the database.
     */
    public static <T> List<T> checkNonExistentEntities(Map<String, T> idToObjInRequestMap, List<T> objInDbList,
                                                       Method idMethod) {
        // Check if the number of entities in the request map exceeds those in the database list
        if (idToObjInRequestMap.size() > objInDbList.size()) {
            // Get the list of IDs for objects in the database using the specified ID retrieval method
            List<String> idsForObjInDb = getIdList(objInDbList, idMethod);
            // Filter out entities from the request map that do not exist in the database list
            return idToObjInRequestMap.entrySet().stream()
                    .filter(e -> !idsForObjInDb.contains(e.getKey())) // Filter non-existent entities
                    .map(Map.Entry::getValue) // Map to the corresponding object
                    .collect(Collectors.toList()); // Collect into a list
        }
        return Collections.emptyList(); // Return an empty list if no non-existent entities are found
    }


    public static <T> List<String> getIdList(List<T> objList) {
        if (objList == null || objList.isEmpty()) {
            return Collections.emptyList();
        }
        Class<?> objClass = getObjClass(objList);
        return getIdList(objList, getMethod("getId", objClass));
    }

    public static <T> List<String> getIdList(List<T> objList, Method idMethod) {
        if (objList == null || objList.isEmpty()) {
            return Collections.emptyList();
        }
        return objList.stream().map(obj -> (String) ReflectionUtils
                        .invokeMethod(idMethod, obj))
                .collect(Collectors.toList());
    }

    public static <T> Predicate<T> lastChangedSince(Long lastChangedSince) {
        if (lastChangedSince == null)
            return obj -> true;
        return obj -> {
            Method getAuditDetailsMethod = getMethod("getAuditDetails", obj.getClass());
            Object auditDetails = ReflectionUtils.invokeMethod(getAuditDetailsMethod, obj);
            Method getLastModifiedTimeMethod = getMethod("getLastModifiedTime",
                    auditDetails.getClass());
            Long lastModifiedTime = (Long) ReflectionUtils
                    .invokeMethod(getLastModifiedTimeMethod, auditDetails);
            return lastModifiedTime > lastChangedSince;
        };
    }

    public static <T> Predicate<T> includeDeleted(Boolean includeDeleted) {
        if (includeDeleted == null || !includeDeleted) {
            return obj -> {
                Method getIsDeletedMethod = getMethod("getIsDeleted", obj.getClass());
                Boolean isDeleted = (Boolean) ReflectionUtils
                        .invokeMethod(getIsDeletedMethod, obj);
                return Objects.equals(isDeleted, false);
            };
        }
        return obj -> true;
    }

    public static <T> Predicate<T> havingTenantId(String tenantId) {
        if (tenantId == null)
            return obj -> true;
        return obj -> {
            Method getTenantIdMethod = getMethod("getTenantId", obj.getClass());
            String actualTenantId  = (String) ReflectionUtils
                    .invokeMethod(getTenantIdMethod, obj);
            return Objects.equals(actualTenantId, tenantId);
        };
    }

    public static <T> Class<?> getObjClass(List<T> objList) {
        return objList.stream().findAny().get().getClass();
    }

    public static <T> void identifyNullIds(List<T> objList) {
        Class<?> objClass = getObjClass(objList);
        identifyNullIds(objList, getMethod("getId", objClass));
    }

    public static <T> void identifyNullIds(List<T> objList, Method idMethod) {
        Long nullCount = objList.stream().filter(obj -> null == ReflectionUtils.invokeMethod(
                idMethod, obj)).count();

        if (nullCount > 0) {
            throw new CustomException("NULL_ID", String.format("Ids cannot be null, found %d", nullCount));
        }
    }

    public static <T> List<T> identifyObjectsWithNullIds(List<T> objList, Method idMethod) {
        return objList.stream().filter(obj -> null == ReflectionUtils.invokeMethod(
                idMethod, obj)).collect(Collectors.toList());
    }

    /**
     * Collects list of objects from an object in objList and then merges those list of objects into a single list.
     *
     * @param objList is the list of objects from which the list is to be collected
     * @param function which takes an object from the objList and returns the list of required objects from that object
     * @return single combined list of all objects collected from each object in the objList
     * @param <T> is the object in objList
     * @param <R> is the object in the list of objects in one object in the objList
     */
    public static <T, R> List<R> collectFromList(List<T> objList, Function<T, List<R>> function) {
        return objList.stream()
                .flatMap(obj -> {
                    List<R> aList = function.apply(obj);
                    if (aList == null || aList.isEmpty()) {
                        return new ArrayList<R>().stream();
                    }
                    return aList.stream();
                })
                .collect(Collectors.toList());
    }

    public static String getIdFieldName(Object obj) {
        String defaultVal = "id";
        try {
            Field idField = getParentClassField(obj.getClass(), "id");
            if(idField != null) {
                idField.setAccessible(true);
                Object idFieldValue = idField.get(obj);
                if (idFieldValue != null) {
                    return "id";
                }
            }
            Field clientReferenceIdField = getParentClassField(obj.getClass(), "clientReferenceId");
            clientReferenceIdField.setAccessible(true);
            Object clientReferenceIdFieldValue = clientReferenceIdField.get(obj);
            if (clientReferenceIdFieldValue != null) {
                return "clientReferenceId";
            }
        } catch (IllegalAccessException | NullPointerException e) {
            return defaultVal;
        }
        return defaultVal;
    }

    // Method to get the field from the parent class
    public static Field getParentClassField(Class<?> clazz, String fieldName) {
        Class<?> parentClass = clazz;
        while (parentClass != null) {
            try {
                return parentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                // Field not found in this class, proceed to the parent class
                parentClass = parentClass.getSuperclass();
            }
        }
        // Field not found in the class hierarchy
        return null;
    }

    public static String getIdFieldName(Method method) {
        if (method != null) {
            return method.getName().contains("Reference") ? "clientReferenceId" : "id";
        }
        return "id";
    }

    public static <T> Predicate<T> notHavingErrors() {
        return obj -> !((Boolean) ReflectionUtils.invokeMethod(getMethod("getHasErrors",
                obj.getClass()), obj));
    }

    public static <T> void enrichIdsFromExistingEntities(Map<String, T> idToObjMap, List<T> existingEntities,
                                                         Method idMethod) {
        IntStream.range(0, existingEntities.size()).forEach(i -> {
            T existing = existingEntities.get(i);
            String id = (String) ReflectionUtils.invokeMethod(getMethod("getId",
                    existing.getClass()), existing);
            String clientReferenceId = (String) ReflectionUtils.invokeMethod(getMethod("getClientReferenceId",
                    existing.getClass()), existing);
            String key = getIdFieldName(idMethod).equalsIgnoreCase("id")
                    ? id : clientReferenceId;
            T toUpdate = idToObjMap.get(key);
            ReflectionUtils.invokeMethod(getMethod("setId", toUpdate.getClass()),
                    toUpdate, id);
            ReflectionUtils.invokeMethod(getMethod("setClientReferenceId",
                    toUpdate.getClass()), toUpdate, clientReferenceId);
        });
    }

    public static Function<Integer, List<String>> uuidSupplier() {
        return integer ->  {
            List<String> uuidList = new ArrayList<>();
            for (int i = 0; i < integer; i++) {
                uuidList.add(UUID.randomUUID().toString());
            }
            return uuidList;
        };
    }

    public static Method getMethod(String methodName, Class<?> clazz) {
        if (methodCache.containsKey(clazz)) {
            Map<String, Method> methodMap = methodCache.get(clazz);
            if (methodMap.containsKey(methodName)) {
                return methodMap.get(methodName);
            } else {
                Method method = findMethod(methodName, clazz);
                methodMap.put(methodName, method);
                return method;
            }
        } else {
            Method method = findMethod(methodName, clazz);
            Map<String, Method> methodMap = new ConcurrentHashMap<>();
            methodMap.put(methodName, method);
            methodCache.put(clazz, methodMap);
            return method;
        }
    }

    /**
     * Enriches objList with requestInfo, auditDetails, rowVersion and sets idDeleted to FALSE.
     *
     * <p>To be used for delete APIs
     *
     * @param objList is list of objects
     * @param requestInfo is requestInfo, usually part of the request object
     * @param updateRowVersion determines whether to update the rowVersion or not
     * @param <T> is any type that has an auditDetails field, rowVersion field and isDeleted field with setters and getters
     */
    public static <T> void enrichForDelete(List<T> objList, RequestInfo requestInfo, boolean updateRowVersion) {
        Class<?> objClass = getObjClass(objList);
        Method setIsDeletedMethod = getMethod("setIsDeleted", objClass);
        Method setAuditDetailsMethod = getMethod("setAuditDetails", objClass);
        Method getAuditDetailsMethod = getMethod("getAuditDetails", objClass);
        objList.forEach(obj -> {
            ReflectionUtils.invokeMethod(setIsDeletedMethod, obj, true);
            if (updateRowVersion) {
                Method getRowVersionMethod = getMethod("getRowVersion", objClass);
                Method setRowVersionMethod = getMethod("setRowVersion", objClass);
                Integer rowVersion = (Integer) ReflectionUtils.invokeMethod(getRowVersionMethod, obj);
                ReflectionUtils.invokeMethod(setRowVersionMethod, obj, rowVersion + 1);
            }
            AuditDetails existingAuditDetails = (AuditDetails) ReflectionUtils.invokeMethod(getAuditDetailsMethod, obj);
            AuditDetails auditDetailsForUpdate = getAuditDetailsForUpdate(existingAuditDetails,
                    requestInfo.getUserInfo().getUuid());
            ReflectionUtils.invokeMethod(setAuditDetailsMethod, obj, auditDetailsForUpdate);
        });
    }

    /**
     * Validate and return the consolidated errorDetailsMap based on all the validations.
     *
     * @param validators is the list of validators
     * @param applicableValidators is a predicate defining the validators to apply
     * @param request is the request body
     * @param setPayloadMethodName is a setter method available on the request body
     * @param getPayloadMethodName is a getter method available on the request body
     * @param errorCode is error code for the exception
     * @return a map of payload vs errorDetails object
     * @param <T> is the type of payload
     * @param <R> is the type of request
     */
    public static <T, R> Tuple<List<T>, Map<T, ErrorDetails>> validate(List<Validator<R, T>> validators,
                                                                 Predicate<Validator<R, T>> applicableValidators,
                                                                 R request, String setPayloadMethodName,
                                                                 String getPayloadMethodName, String errorCode, boolean isBulk) {
        Map<T, ErrorDetails> errorDetailsMap = validate(validators,
                applicableValidators, request,
                setPayloadMethodName);
        if (!errorDetailsMap.isEmpty() && !isBulk) {
            throw new CustomException(errorCode, errorDetailsMap.values().toString());
        }
        Method getEntities = getMethod(getPayloadMethodName, request.getClass());
        List<T> validEntities = (List<T>) ReflectionUtils.invokeMethod(getEntities, request);
        validEntities = validEntities.stream().filter(notHavingErrors()).collect(Collectors.toList());
        return new Tuple<>(validEntities, errorDetailsMap);
    }

    /**
     * Validate and return the consolidated errorDetailsMap based on all the validations.
     *
     * @param validators is the list of validators
     * @param applicableValidators is a predicate defining the validators to apply
     * @param request is the request body
     * @param setPayloadMethodName is a setter method available on the request body
     * @return a map of payload vs errorDetails object
     * @param <T> is the type of payload
     * @param <R> is the type of request
     */
    public static <T, R> Map<T, ErrorDetails> validate(List<Validator<R, T>> validators,
                                                       Predicate<Validator<R, T>> applicableValidators,
                                                       R request,
                                                       String setPayloadMethodName) {
        Map<T, ErrorDetails> errorDetailsMap = new HashMap<>();
        validators.stream().filter(applicableValidators)
                .map(validator -> validator.validate(request))
                .forEach(e -> populateErrorDetails(request, errorDetailsMap, e,
                        setPayloadMethodName));
        return errorDetailsMap;
    }

    /**
     * Populate error details for error handler.
     *
     * @param request is the request body having a getRequestInfo method
     * @param errorDetailsMap is a map of payload vs errorDetails
     * @param errorMap is a map of payload vs all its errors across validations
     * @param setPayloadMethodName is a setter method available on the request body
     * @param <T> is the type of payload
     * @param <R> is the type of request
     */
    public static <T, R> void populateErrorDetails(R request,
                                                   Map<T, ErrorDetails> errorDetailsMap,
                                                   Map<T, List<Error>> errorMap,
                                                   String setPayloadMethodName) {
        try {
            for (Map.Entry<T, List<Error>> entry : errorMap.entrySet()) {
                T payload = entry.getKey();
                if (errorDetailsMap.containsKey(payload)) {
                    errorDetailsMap.get(payload).getErrors().addAll(entry.getValue());
                } else {
                    RequestInfo requestInfo = (RequestInfo) ReflectionUtils
                            .invokeMethod(getMethod("getRequestInfo",
                                    request.getClass()), request);
                    R newRequest = (R) ReflectionUtils.accessibleConstructor(request.getClass(),
                            null).newInstance();
                    ReflectionUtils.invokeMethod(getMethod("setRequestInfo",
                            newRequest.getClass()), newRequest, requestInfo);
                    ReflectionUtils.invokeMethod(getMethod(setPayloadMethodName,
                                    newRequest.getClass()), newRequest,
                            Collections.singletonList(payload));
                    ApiDetails apiDetails = ApiDetails.builder()
                            .methodType(HttpMethod.POST.name())
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .url(requestInfo.getApiId()).build();
                    apiDetails.setRequestBody(objectMapper.writeValueAsString(newRequest));
                    ErrorDetails errorDetails = ErrorDetails.builder()
                            .errors(entry.getValue())
                            .apiDetails(apiDetails)
                            .build();
                    errorDetailsMap.put(payload, errorDetails);
                }
            }
        } catch (Exception exception) {
            log.error("failure in error handling", exception);
            throw new CustomException("FAILURE_IN_ERROR_HANDLING", exception.getMessage());
        }
    }

    /**
     * Populate error details for exception scenarios.
     *
     *
     * @param request is the request body
     * @param errorDetailsMap is a map of payload vs errorDetails
     * @param validPayloads are the payloads without validation errors
     * @param exception is the exception
     * @param setPayloadMethodName is a setter method available on the request body
     * @param <T> is the type of payload
     * @param <R> is the type of request
     */
    public static <R,T> void populateErrorDetails(R request, Map<T, ErrorDetails> errorDetailsMap,
                                                  List<T> validPayloads, Exception exception,
                                                  String setPayloadMethodName) {
        Error.ErrorType errorType = Error.ErrorType.NON_RECOVERABLE;
        String errorCode = "INTERNAL_SERVER_ERROR";
        if (exception instanceof CustomException) {
            errorCode = ((CustomException) exception).getCode();
            // in case further cases come up, we can add more cases in a set and check using contains.
            if (exception instanceof CustomException
                    && !("IDGEN_ERROR".equals(((CustomException) exception).getCode()))) {
                errorType = Error.ErrorType.RECOVERABLE;
            } else {
                errorType = Error.ErrorType.NON_RECOVERABLE;
            }
        }
        List<Error> errorList = new ArrayList<>();
        errorList.add(Error.builder().errorMessage(exception.getMessage())
                .errorCode(errorCode)
                .type(errorType)
                .exception(new CustomException(errorCode, exception.getMessage())).build());
        Map<T, List<Error>> errorListMap = new HashMap<>();
        validPayloads.forEach(payload -> {
            if (errorListMap.containsKey(payload)) {
                errorListMap.get(payload).addAll(errorList);
            } else {
                errorListMap.put(payload, errorList);
            }
        });
        populateErrorDetails(request, errorDetailsMap, errorListMap, setPayloadMethodName);
    }

    /**
     * Populate error details for exception scenarios.
     *
     *
     * @param request is the request body
     * @param errorListMap is a map of payload vs errorList
     * @param validPayloads are the payloads without validation errors
     * @param exception is the exception
     * @param <T> is the type of payload
     * @param <R> is the type of request
     */
    public static <R,T> void populateErrorDetails(R request, Map<T, List<Error>> errorListMap,
                                                  List<T> validPayloads, Exception exception) {
//        Error.ErrorType errorType = Error.ErrorType.NON_RECOVERABLE;
//        String errorCode = "INTERNAL_SERVER_ERROR";
//        if (exception instanceof CustomException) {
//            errorCode = ((CustomException) exception).getCode();
//            // in case further cases come up, we can add more cases in a set and check using contains.
//            if (!((CustomException) exception).getCode().equals("IDGEN_ERROR")) {
//                errorType = Error.ErrorType.RECOVERABLE;
//            }
//        }
//        List<Error> errorList = new ArrayList<>();
//        errorList.add(Error.builder().errorMessage(exception.getMessage())
//                .errorCode(errorCode)
//                .type(errorType)
//                .exception(new CustomException(errorCode, exception.getMessage())).build());
//        validPayloads.forEach(payload -> {
//            ReflectionUtils.invokeMethod(getMethod("setHasErrors", payload.getClass()),
//                    payload, Boolean.TRUE);
//            errorListMap.put(payload, errorList);
//        });
    }

    /**
     * Handle errors after validators & enrichment layer.
     *
     *
     * @param errorDetailsMap is a map of payload vs errorList
     * @param isBulk is to indicate whether we are using bulk api or not
     * @param errorCode error code
     */
    public static <T> void handleErrors(Map<T, ErrorDetails> errorDetailsMap, boolean isBulk, String errorCode) {
        if (!errorDetailsMap.isEmpty()) {
            log.error("{} errors collected", errorDetailsMap.size());
            List<ErrorDetail> errorDetailList = errorDetailsMap.values()
                    .stream().map(ErrorDetails::getTracerModel).collect(Collectors.toList());
            if (isBulk) {
                ErrorHandler.exceptionAdviseInstance.exceptionHandler(errorDetailList);
            } else {
                Map<String, String> getErrorMap = getErrorMap(errorDetailList);
                String code =  getErrorMap.keySet().stream().collect(Collectors.joining(":"));
                throw new CustomException(code, errorDetailsMap.values().toString());
            }
        }
    }

    private static Map<String, String> getErrorMap(List<ErrorDetail> errorDetailList) {
        return errorDetailList.stream()
                .flatMap(errorDetail -> errorDetail.getErrors().stream())
                .collect(Collectors.toMap(ErrorEntity::getErrorCode, ErrorEntity::getErrorMessage));
    }

    /**
     * Validate for null ids
     *
     * @param request is the request body
     * @param getPayloadMethodName is the get method of the payloads available on the request body
     * @return a map of payload vs list of all errors for that payload
     * @param <R> is the type of request
     * @param <T> is the type of payload
     */
    public static <R,T> HashMap<T, List<Error>> validateForNullId(R request, String getPayloadMethodName) {
        log.info("validating for null id");
        HashMap<T, List<Error>> errorDetailsMap = new HashMap<>();
        List<T> validPayloads = ((List<T>)ReflectionUtils.invokeMethod(getMethod(getPayloadMethodName,
                request.getClass()), request)).stream().filter(notHavingErrors()).collect(Collectors.toList());
        if (!validPayloads.isEmpty()) {
            Class<?> objClass = getObjClass(validPayloads);
            Method idMethod = getMethod("getId", objClass);
            List<T> payloadWithNullIds = identifyObjectsWithNullIds(validPayloads, idMethod);
            payloadWithNullIds.forEach(payload -> {
                Error error = getErrorForNullId();
                populateErrorDetails(payload, error, errorDetailsMap);
            });
            log.info("null id validation completed successfully, total errors: {}", payloadWithNullIds.size());
        }
        return errorDetailsMap;
    }

    /**
     * Populate error details for validators.
     *
     * @param payload is the payload in request body
     * @param error is the error for the validator
     * @param errorDetailsMap is a map of payload vs errorDetails
     * @param <T> is the type of payload
     */
    public static <T> void populateErrorDetails(T payload, Error error,
                                  Map<T, List<Error>> errorDetailsMap) {
        ReflectionUtils.invokeMethod(getMethod("setHasErrors", payload.getClass()),
                payload, Boolean.TRUE);
        if (errorDetailsMap.containsKey(payload)) {
            errorDetailsMap.get(payload).add(error);
        } else {
            List<Error> errors = new ArrayList<>();
            errors.add(error);
            errorDetailsMap.put(payload, errors);
        }
    }

    private static Method findMethod(String methodName, Class<?> clazz) {
        return Arrays.stream(ReflectionUtils.getAllDeclaredMethods(clazz))
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElseThrow(() -> new CustomException("INVALID_OBJECT_OR_METHOD", "Invalid object or method"));
    }

    /**
     * Checks if the value matches the regex pattern.
     * @param value The value to be checked.
     * @param regexPattern The regex pattern to match against.
     * @return true if the value matches the pattern, false otherwise.
     */
    public static boolean isValidPattern(String value, String regexPattern) {

        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(value);
        return matcher.matches();
    }

    /**
     * Construct a Common Table Expression that returns total count if there is any otherwise return 0L
     * @param query
     * @param paramsMap
     * @param namedParameterJdbcTemplate
     * @return
     */
    public static Long constructTotalCountCTEAndReturnResult(String query, Map<String, Object> paramsMap, final NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        String cteQuery = "WITH result_cte AS ("+query+"), totalCount_cte AS (SELECT COUNT(*) AS totalRows FROM result_cte) select * from totalCount_cte";
        return namedParameterJdbcTemplate.query(cteQuery, paramsMap, resultSet -> {
            if(resultSet.next())
                return resultSet.getLong("totalRows");
            else
                return 0L;
        });
    }

    public static String getSchemaName(String tenantId, MultiStateInstanceUtil multiStateInstanceUtil) {
        String schemaName = "";
        if (!ObjectUtils.isEmpty(multiStateInstanceUtil) && multiStateInstanceUtil.getIsEnvironmentCentralInstance()) {
            String[] tenants = tenantId.split("\\.");
            if (tenants.length > multiStateInstanceUtil.getStateSchemaIndexPositionInTenantId()) {
                schemaName = tenants[multiStateInstanceUtil.getStateSchemaIndexPositionInTenantId()];
            } else {
                schemaName = tenantId;
            }
        }
        return schemaName;
    }

}
