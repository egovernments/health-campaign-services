package org.egov.common.utils;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.tracer.model.CustomException;
import org.springframework.util.ReflectionUtils;

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
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class CommonUtils {

    public static final String GET_API_OPERATION = "getApiOperation";

    private static final Map<Class<?>, Map<String, Method>> methodCache = new HashMap<>();

    private CommonUtils() {}

    /**
     * Returns whether the provided object is intended for update operation
     *
     * @param obj the object to check
     *
     * @return true if the object is intended for update operation, false otherwise
     *
     */
    public static boolean isForUpdate(Object obj) {
        Method getApiOperationMethod = getMethod(GET_API_OPERATION, obj.getClass());
        Object apiOperation = ReflectionUtils.invokeMethod(getApiOperationMethod, obj);
        if (apiOperation == null) {
            return false;
        }
        Method nameMethod = CommonUtils.getMethod("name", Enum.class);
        return "UPDATE".equals(ReflectionUtils.invokeMethod(nameMethod, apiOperation));
    }

    /**
     * Returns whether the provided object is intended for delete operation
     *
     * @param obj the object to check
     *
     * @return true if the object is intended for delete operation, false otherwise
     *
     */
    public static boolean isForDelete(Object obj) {
        Method getApiOperationMethod = getMethod(GET_API_OPERATION, obj.getClass());
        Object apiOperation = ReflectionUtils.invokeMethod(getApiOperationMethod, obj);
        if (apiOperation == null) {
            return false;
        }
        Method nameMethod = CommonUtils.getMethod("name", Enum.class);
        return "DELETE".equals(ReflectionUtils.invokeMethod(nameMethod, apiOperation));
    }

    /**
     * Returns whether the provided object is intended for create operation
     *
     * @param obj the object to check
     *
     * @return true if the object is intended for create operation, false otherwise
     *
     */
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

    /**
     * Returns a Set of values extracted from a list of objects using the specified method
     *
     * @param <T> the type of the values to be extracted
     * @param <R> the type of the objects in the list
     * @param objects the list of objects to extract values from
     * @param methodName the name of the method to be used for extracting the values
     *
     * @return a Set of the extracted values, empty set if the objects list is empty
     *
     */
    public static <T, R> Set<T> getSet(List<R> objects, String methodName) {
        return objects.stream().map(o -> (T) ReflectionUtils
                .invokeMethod(getMethod(methodName, o.getClass()), o))
                .collect(Collectors.toSet());
    }

    /**
     * Returns the difference between two lists.
     *
     * @param <T> the type of the elements in the lists
     * @param list the first list
     * @param subList the second list
     *
     * @return a new list containing the elements in list that are not in subList.
     *
     */
    public static <T> List<T> getDifference(List<T> list, List<T> subList) {
        List<T> newList = new ArrayList<>(list);
        List<T> newSubList = new ArrayList<>(subList);
        if (newList.size() >= newSubList.size()) {
            newList.removeAll(subList);
        }
        return newList;
    }

    /**
     *  Validate a set of IDs using a validator function and throws an exception if any of them are invalid
     *
     * @param <T> the type of the IDs
     * @param idsToValidate the set of IDs to be validated
     * @param validator the validator function that takes a list of IDs and returns a list of valid IDs
     *
     */
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
     * Creates an AuditDetails object for a create operation
     *
     * @param requestInfo the RequestInfo object containing the user details
     *
     * @return an AuditDetails object with fields populated for a create operation
     *
     */
    public static AuditDetails getAuditDetailsForCreate(RequestInfo requestInfo) {
        log.info("Creating audit details for create api");
        Long time = System.currentTimeMillis();
        return AuditDetails.builder()
                .createdBy(requestInfo.getUserInfo().getUuid())
                .createdTime(time)
                .lastModifiedBy(requestInfo.getUserInfo().getUuid())
                .lastModifiedTime(time).build();
    }

    /**
     * Returns an AuditDetails object for update operation
     *
     * @param existingAuditDetails the existing AuditDetails object
     * @param modifiedByUuid the UUID of the user who modified the object
     *
     * @return an AuditDetails object for update operation with the given modifiedByUuid and current time as lastModifiedTime
     *
     */
    public static AuditDetails getAuditDetailsForUpdate(AuditDetails existingAuditDetails,
                                                        String modifiedByUuid) {
        log.info("Creating audit details for update api");
        return AuditDetails.builder()
                .createdBy(existingAuditDetails.getCreatedBy())
                .createdTime(existingAuditDetails.getCreatedTime())
                .lastModifiedBy(modifiedByUuid)
                .lastModifiedTime(System.currentTimeMillis()).build();
    }

    /**
     * Returns whether the provided object is intended to search by ID only
     *
     * @param obj the object to check
     *
     * @return true if the object is intended to search by ID only, false otherwise
     *
     */
    public static boolean isSearchByIdOnly(Object obj) {
        return isSearchByIdOnly(obj, "id");
    }

    /**
     * Returns whether the provided object is intended to search by ID only
     *
     * @param obj the object to check
     * @param fieldName the name of the id field to check
     *
     * @return true if the object is intended to search by ID only, false otherwise
     *
     */
    public static boolean isSearchByIdOnly(Object obj, String fieldName) {
        Class<?> objClass = obj.getClass();
        String propertyName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        Method setIdMethod = getMethod("set"+propertyName, objClass);
        Method getIdMethod = getMethod("get"+propertyName, objClass);

        Object finalObject = null;
        try {
            finalObject = objClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        Object id = ReflectionUtils.invokeMethod(getIdMethod, obj);
        ReflectionUtils.invokeMethod(setIdMethod, finalObject, id);

        if (id == null) {
            return false;
        }

        String actual = obj.toString();
        String expected = finalObject.toString();
        return actual.equals(expected);
    }

    /**
     * Compare the row version present in the provided objects in the map with the one present in the list
     *
     * @param idToObjMap  a map of objects that needs to be compared with the ones in the list
     * @param objList list of objects that needs to be compared with the ones in the map
     *
     */
    public static <T> void checkRowVersion(Map<String, T> idToObjMap, List<T> objList) {
        Class<?> objClass = getObjClass(objList);
        checkRowVersion(idToObjMap, objList, getMethod("getId", objClass));
    }


    /**
     * The checkRowVersion method checks whether the row versions of objects from the idToObjMap match with the objects from objList.
     *
     * @param <T> Type of the object
     * @param idToObjMap A map of objects, where the key is the id of the object
     * @param objList A list of objects that need to be checked for row version mismatch
     * @param idMethod A method object used to extract the id from the object
     *
     * @throws CustomException if there's any mismatch in the row versions, it will throw CustomException with message
     * "ROW_VERSION_MISMATCH" and the ids
     *
     */
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
     *
     * Returns the tenant id of the objects present in the given list.
     * The assumption is that all objects present in the list have a tenantId and all have the same tenantId.
     * This method makes use of ReflectionUtils to access the getTenantId method on any object from the list.
     *
     * @param objList a list of objects whose tenantId is to be returned.
     * @param <T> type of the objects in the list
     *
     * @return the tenantId of the objects
     */
    public static <T> String getTenantId(List<T> objList) {
        Object obj = objList.stream().findAny().get();
        Method getTenantIdMethod = getMethod("getTenantId", obj.getClass());
        String tenantId = (String) ReflectionUtils.invokeMethod(getTenantIdMethod, obj);
        log.info("Tenant ID {}", tenantId);
        return tenantId;
    }

    /**
     *
     * This method is used to enrich a list of objects of generic type T with the provided idList, RequestInfo, AuditDetails, rowVersion and isDeleted values.
     * It uses reflection to invoke the setId, setAuditDetails, setRowVersion and setIsDeleted methods on the objects in the list.
     *
     * @param objList A List of objects of generic type T.
     * @param idList A List of String ids that need to be set on the objects in objList.
     * @param requestInfo requestInfo object to get the AuditDetails
     *
     */
    public static <T> void enrichForCreate(List<T> objList, List<String> idList, RequestInfo requestInfo) {
        AuditDetails auditDetails = getAuditDetailsForCreate(requestInfo);
        Class<?> objClass = getObjClass(objList);
        Method setIdMethod = getMethod("setId", objClass);
        Method setAuditDetailsMethod = getMethod("setAuditDetails", objClass);
        Method setRowVersionMethod = getMethod("setRowVersion", objClass);
        Method setIsDeletedMethod = getMethod("setIsDeleted", objClass);
        IntStream.range(0, objList.size())
                .forEach(i -> {
                    final Object obj = objList.get(i);
                    ReflectionUtils.invokeMethod(setIdMethod, obj, idList.get(i));
                    ReflectionUtils.invokeMethod(setAuditDetailsMethod, obj, auditDetails);
                    ReflectionUtils.invokeMethod(setRowVersionMethod, obj, 1);
                    ReflectionUtils.invokeMethod(setIsDeletedMethod, obj, Boolean.FALSE);
                });
    }

    /**
     * This method is used to get the id Method of a list of objects of generic type T.
     * It uses reflection to find the 'id' or 'clientReferenceId' method of the objects in the list.
     *
     * @param objList A List of objects of generic type T.
     *
     * @return A Method object representing the 'id' or 'clientReferenceId' method of the objects in the objList.
     *
     */
    public static <T> Method getIdMethod(List<T> objList) {
        return getIdMethod(objList, "id", "clientReferenceId");
    }

    /**
     * This method is used to get the id Method of a list of objects of generic type T.
     * It uses reflection to find the method with the provided 'idFieldName' in the objects in the list.
     *
     * @param objList A List of objects of generic type T.
     * @param idFieldName a string representing the name of the field which represents the 'id' of the object
     *
     * @return A Method object representing the 'idFieldName' method of the objects in the objList.
     *
     */
    public static <T> Method getIdMethod(List<T> objList, String idFieldName) {
        String idMethodName = "get" + idFieldName.substring(0, 1).toUpperCase()
                + idFieldName.substring(1);
        return getMethod(idMethodName, getObjClass(objList));
    }


    /**
     * This method is used to get the id Method of a list of objects of generic type T.
     * It uses reflection to find the method with the provided 'idField' or 'clientReferenceIdField' in the objects in the list.
     * It try to invoke 'idField' method first, if it's return is not null it return 'idField' method
     * otherwise it return 'clientReferenceIdField' method
     *
     * @param objList A List of objects of generic type T.
     * @param idField a string representing the name of the field which represents the 'id' of the object
     * @param clientReferenceIdField a string representing the name of the field which represents the 'clientReferenceId' of the object
     *
     * @return A Method object representing the 'idField' or 'clientReferenceIdField' method of the objects in the objList.
     *
     */
    public static <T> Method getIdMethod(List<T> objList, String idField, String clientReferenceIdField) {
        String idMethodName = "get" + idField.substring(0, 1).toUpperCase()
                + idField.substring(1);
        String clientReferenceIdMethodName = "get" + clientReferenceIdField.substring(0, 1).toUpperCase()
                + clientReferenceIdField.substring(1);
        try{
            Method getId = getMethod(idMethodName, getObjClass(objList));
            String value = (String) ReflectionUtils.invokeMethod(getId, objList.stream().findAny().get());
            if (value != null) {
                return getId;
            }
        } catch (CustomException e){
            log.error(e.getMessage());
        }

        return getMethod(clientReferenceIdMethodName, getObjClass(objList));
    }

    /**
     * This method is used to set the id of a list of objects of generic type T, using the provided idList.
     * It uses reflection to access the 'setId' method of the objects.
     *
     * @param objList List of objects of generic type T.
     * @param idList list of id strings
     *
     */
    public static <T> void enrichId(List<T> objList, List<String> idList) {
        Class<?> objClass = getObjClass(objList);
        Method setIdMethod = getMethod("setId", objClass);
        IntStream.range(0, objList.size())
                .forEach(i -> {
                    final Object obj = objList.get(i);
                    ReflectionUtils.invokeMethod(setIdMethod, obj, idList.get(i));
                });
    }

    /**
     * This method is used to update a list of objects of generic type T, based on a request object and a map of objects.
     * It uses reflection to access the 'setIsDeleted', 'getRowVersion', 'setRowVersion', 'getAuditDetails', 'setAuditDetails' methods of the objects, and 'getApiOperation', 'getRequestInfo' methods of the request object.
     * It updates the isDeleted field of the object, If the operation is DELETE, increments the row version, and update auditDetails of the object
     *
     * @param idToObjMap A map of objects of generic type T, where the keys are the ids of the objects and the values are the objects themselves.
     * @param existingObjList A List of objects of generic type T that are already present in the database.
     * @param request A request object containing the information needed to update the objects.
     *
     */
    public static <T> void enrichForUpdate(Map<String, T> idToObjMap, List<T> existingObjList, Object request) {
        Class<?> objClass = getObjClass(existingObjList);
        enrichForUpdate(idToObjMap, existingObjList, request, getMethod("getId", objClass));
    }

    /**
     * This method is used to update a list of objects of generic type T, based on a request object and a map of objects.
     * It uses reflection to access the 'setIsDeleted', 'getRowVersion', 'setRowVersion', 'getAuditDetails', 'setAuditDetails' methods of the objects, and 'getApiOperation', 'getRequestInfo' methods of the request object.
     * It updates the isDeleted field of the object, If the operation is DELETE, increments the row version, and update auditDetails of the object
     *
     * @param idToObjMap A map of objects of generic type T, where the keys are the ids of the objects and the values are the objects themselves.
     * @param existingObjList A List of objects of generic type T that are already present in the database.
     * @param request A request object containing the information needed to update the objects.
     * @param idMethod A Method object representing the method that returns the id of the objects.
     *
     */
    public static <T> void enrichForUpdate(Map<String, T> idToObjMap, List<T> existingObjList, Object request, Method idMethod) {
        Class<?> objClass = getObjClass(existingObjList);
        Class<?> requestObjClass = request.getClass();
        Method setIsDeletedMethod = getMethod("setIsDeleted", objClass);
        Method getRowVersionMethod = getMethod("getRowVersion", objClass);
        Method setRowVersionMethod = getMethod("setRowVersion", objClass);
        Method getAuditDetailsMethod = getMethod("getAuditDetails", objClass);
        Method setAuditDetailsMethod = getMethod("setAuditDetails", objClass);
        Method getApiOperationMethod = getMethod(GET_API_OPERATION, requestObjClass);
        Method getRequestInfoMethod = getMethod("getRequestInfo", requestObjClass);
        IntStream.range(0, existingObjList.size()).forEach(i -> {
            Object obj = idToObjMap.get(ReflectionUtils.invokeMethod(idMethod,
                    existingObjList.get(i)));
            Object apiOperation = ReflectionUtils.invokeMethod(getApiOperationMethod, request);
            Method nameMethod = CommonUtils.getMethod("name", Enum.class);
            if ("DELETE".equals(ReflectionUtils.invokeMethod(nameMethod, apiOperation))) {
                ReflectionUtils.invokeMethod(setIsDeletedMethod, obj, true);
            }
            Integer rowVersion = (Integer) ReflectionUtils.invokeMethod(getRowVersionMethod, obj);
            ReflectionUtils.invokeMethod(setRowVersionMethod, obj, rowVersion + 1);
            RequestInfo requestInfo = (RequestInfo) ReflectionUtils
                    .invokeMethod(getRequestInfoMethod, request);
            AuditDetails existingAuditDetails = (AuditDetails) ReflectionUtils
                    .invokeMethod(getAuditDetailsMethod, existingObjList.get(i));
            AuditDetails auditDetailsForUpdate = getAuditDetailsForUpdate(existingAuditDetails,
                    requestInfo.getUserInfo().getUuid());
            ReflectionUtils.invokeMethod(setAuditDetailsMethod, obj, auditDetailsForUpdate);
        });
    }

    /**
     * This method is used to create a map of objects of generic type T, where the keys are the ids of the objects and the values are the objects themselves.
     * It uses the 'getId' method to extract the ids from the objects and uses them as keys in the map.
     *
     * @param objList A List of objects of generic type T.
     *
     * @return A Map of objects of generic type T, where the keys are the ids of the objects and the values are the objects themselves.
     *
     */
    public static <T> Map<String, T> getIdToObjMap(List<T> objList) {
        Class<?> objClass = getObjClass(objList);
        return getIdToObjMap(objList, getMethod("getId", objClass));
    }

    /**
     * This method is used to create a map of objects of generic type T, where the keys are the ids of the objects and the values are the objects themselves.
     * It uses the provided idMethod to extract the ids from the objects and uses them as keys in the map.
     *
     * @param objList A List of objects of generic type T.
     * @param idMethod A Method object representing the method that returns the id of the objects in the objList.
     *
     * @return A Map of objects of generic type T, where the keys are the ids of the objects and the values are the objects themselves.
     *
     */
    public static <T> Map<String, T> getIdToObjMap(List<T> objList, Method idMethod) {
        return objList.stream().collect(Collectors.toMap(obj -> (String) ReflectionUtils
                .invokeMethod(idMethod, obj), obj -> obj));
    }

    /**
     * This method is used to validate a map of objects of generic type T, extracted from a request, against a list of the same objects that are already present in the database.
     * It checks if the number of objects in the map is less than or equal to the number of objects in the list,
     * and it verifies if each object in the map has an id present in the list, obtained by invoking 'getId' method using reflection.
     * If any of these conditions is not met, it logs an error and throws a custom exception containing the invalid entities.
     *
     * @param idToObjInRequestMap a map of objects of generic type T, extracted from a request, with their ids as keys.
     * @param objInDbList a list of objects of generic type T that are already present in the database.
     *
     */
    public static <T> void validateEntities(Map<String, T> idToObjInRequestMap, List<T> objInDbList) {
        if (idToObjInRequestMap.size() > objInDbList.size()) {
            List<String> idsForObjInDb = getIdList(objInDbList);
            List<String> idsForInvalidObj = idToObjInRequestMap.keySet().stream()
                    .filter(id -> !idsForObjInDb.contains(id))
                    .collect(Collectors.toList());
            log.error("Invalid entities {}", idsForInvalidObj);
            throw new CustomException("INVALID_ENTITY", idsForInvalidObj.toString());
        }
    }

    /**
     * This method is used to validate a map of objects of generic type T, extracted from a request, against a list of the same objects that are already present in the database.
     * It checks if the number of objects in the map is less than or equal to the number of objects in the list,
     * and it verifies if each object in the map has an id present in the list.
     * If any of these conditions is not met, it logs an error and throws a custom exception containing the invalid entities.
     *
     * @param idToObjInRequestMap a map of objects of generic type T, extracted from a request, with their ids as keys.
     * @param objInDbList a list of objects of generic type T that are already present in the database.
     * @param idMethod A Method object representing the method that returns the id of the objects in the objList.
     *
     */
    public static <T> void validateEntities(Map<String, T> idToObjInRequestMap, List<T> objInDbList,
                                            Method idMethod) {
        if (idToObjInRequestMap.size() > objInDbList.size()) {
            List<String> idsForObjInDb = getIdList(objInDbList, idMethod);
            List<String> idsForInvalidObj = idToObjInRequestMap.keySet().stream()
                    .filter(id -> !idsForObjInDb.contains(id))
                    .collect(Collectors.toList());
            log.error("Invalid entities {}", idsForInvalidObj);
            throw new CustomException("INVALID_ENTITY", idsForInvalidObj.toString());
        }
    }

    /**
     * This method is used to extract a list of ids from a list of objects of generic type T.
     * It uses reflection to invoke the 'getId' method on each object in the list and extract the returned id.
     *
     * @param objList A List of objects of generic type T.
     *
     * @return A List of String ids extracted from the objects in the objList using the 'getId' method.
     *
     */
    public static <T> List<String> getIdList(List<T> objList) {
        if (objList == null || objList.isEmpty()) {
            return Collections.emptyList();
        }
        Class<?> objClass = getObjClass(objList);
        return getIdList(objList, getMethod("getId", objClass));
    }

    /**
     * This method is used to extract a list of ids from a list of objects of generic type T using the provided idMethod.
     * It uses reflection to invoke the provided idMethod on each object in the list and extract the returned id.
     *
     * @param objList A List of objects of generic type T.
     * @param idMethod A Method object representing the method that returns the id of the objects in the objList.
     *
     * @return A List of String ids extracted from the objects in the objList using the provided idMethod.
     *
     */
    public static <T> List<String> getIdList(List<T> objList, Method idMethod) {
        if (objList == null || objList.isEmpty()) {
            return Collections.emptyList();
        }
        return objList.stream().map(obj -> (String) ReflectionUtils
                        .invokeMethod(idMethod, obj))
                .collect(Collectors.toList());
    }

    /**
     * This method is used to return a Predicate of generic type T that filters the elements of a list of objects based on their 'lastModifiedTime' value.
     * The returned predicate will only include the elements of the list that have a 'lastModifiedTime' value greater than the lastChangedSince parameter,
     * unless the lastChangedSince is null in that case it returns true.
     * It uses reflection to invoke the 'getAuditDetails' and 'getLastModifiedTime' methods of the objects in the list.
     *
     * @param lastChangedSince A Long timestamp that is used to filter the elements of the list
     *
     * @return A Predicate of generic type T that filters the elements of a list of objects based on their 'lastModifiedTime' value
     *
     */
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

    /**
     * This method is used to return a Predicate of generic type T that filters the elements of a list of objects based on their 'isDeleted' value.
     * The returned predicate will only include the elements of the list that have a false 'isDeleted' value, unless the 'includeDeleted' parameter is true.
     * It uses reflection to invoke the 'getIsDeleted' method of the objects in the list.
     *
     * @param includeDeleted A boolean value indicating whether the returned predicate should include deleted objects or not
     *
     * @return A Predicate of generic type T that filters the elements of a list of objects based on their 'isDeleted' value
     */
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

    /**
     * Returns a Predicate that checks whether an object has a tenant id that is equal to the provided value
     *
     * @param <T> the type of the object to be checked
     * @param tenantId the value to check against the object's tenant id
     *
     * @return a Predicate that returns true if the object's tenant id is equal to the provided value,
     *   or if tenantId is null, it returns true for any object
     *
     */
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

    /**
     * Returns the class of the first object in the list passed in.
     *
     * @param <T> the type of the objects in the list
     * @param objList list of objects which class is to be extracted
     *
     * @return the class of the first object in the list, returns null if the objList is empty
     *
     */
    public static <T> Class<?> getObjClass(List<T> objList) {
        return objList.stream().findAny().get().getClass();
    }

    /**
     * Throws an exception if any of the objects in a list have a null id.
     *
     * @param <T> the type of the objects in the list
     * @param objList the list of objects to check for null ids
     *
     */

    public static <T> void identifyNullIds(List<T> objList) {
        Class<?> objClass = getObjClass(objList);
        identifyNullIds(objList, getMethod("getId", objClass));
    }

    /**
     * Throws an exception if any of the objects in a list have a null id.
     *
     * @param <T> the type of the objects in the list
     * @param objList the list of objects to check for null ids
     * @param idMethod the method to use for getting the id from the objects
     *
     * @throws CustomException with message "NULL_ID" and the number of null ids found if any objects have null ids.
     *
     */
    public static <T> void identifyNullIds(List<T> objList, Method idMethod) {
        Long nullCount = objList.stream().filter(obj -> null == ReflectionUtils.invokeMethod(
                idMethod, obj)).count();

        if (nullCount > 0) {
            throw new CustomException("NULL_ID", String.format("Ids cannot be null, found %d", nullCount));
        }
    }

    /**
     * Applies a function to a list of objects and collects the result into a new list
     *
     * @param <T> the type of the objects in the input list
     * @param <R> the type of the objects in the output list
     * @param objList the input list of objects
     * @param function the function to apply to each object in the input list
     *
     * @return a new list containing the results of applying the function to each object in the input list
     *
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

    /**
     * Returns the name of the id field from the object passed in
     *
     * @param obj the object from which to extract the id field name
     *
     * @return the id field name, returns "clientReferenceId" if it contain
     *  a non-null value otherwise "id" if it contain a non-null value , if none is found the default value is "id"
     *
     */
    public static String getIdFieldName(Object obj) {
        String defaultVal = "id";
        try {
            Field idField = obj.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            Object idFieldValue = idField.get(obj);
            if (idFieldValue != null) {
                return "id";
            }
            Field clientReferenceIdField = obj.getClass().getDeclaredField("clientReferenceId");
            clientReferenceIdField.setAccessible(true);
            Object clientReferenceIdFieldValue = clientReferenceIdField.get(obj);
            if (clientReferenceIdFieldValue != null) {
                return "clientReferenceId";
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return defaultVal;
        }
        return defaultVal;
    }

    /**
     * Returns the name of the id field from the method passed in
     *
     * @param method the Method object from which to extract the id field name
     * @return the id field name. default to "id" if the method passed in is null,
     *  "clientReferenceId" if it contain "Reference" in its name else "id"
     *
     */
    public static String getIdFieldName(Method method) {
        if (method != null) {
            return method.getName().contains("Reference") ? "clientReferenceId" : "id";
        }
        return "id";
    }

    /**
     * Enriches the IDs and client reference IDs of objects in a map with values from a list of existing entities.
     *
     * @param <T> the type of the objects in the map
     * @param idToObjMap a map of objects whose IDs and client reference IDs will be enriched
     * @param existingEntities a list of existing entities that the IDs and client reference IDs will be taken from
     * @param idMethod the method used to retrieve the ID and client reference ID from the existing entities
     *
     */
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

    /**
     * Returns a Function that generates a List of specified number of random UUIDs.
     *
     * @return a function that accept an integer and return a list of random UUIDs as string
     *
     */
    public static Function<Integer, List<String>> uuidSupplier() {
        return integer ->  {
            List<String> uuidList = new ArrayList<>();
            for (int i = 0; i < integer; i++) {
                uuidList.add(UUID.randomUUID().toString());
            }
            return uuidList;
        };
    }

    /**
     * Returns a Method object that reflects the specified method of the specified class.
     * The method is cached in a HashMap for future use.
     *
     * @param methodName the name of the method
     * @param clazz the class that the method is a member of
     *
     * @return the Method object for the method with the specified name in the specified class
     *
     */
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
            Map<String, Method> methodMap = new HashMap<>();
            methodMap.put(methodName, method);
            methodCache.put(clazz, methodMap);
            return method;
        }
    }

    /**
     * Returns a Method object that reflects the specified method of the specified class.
     *
     * @param methodName the name of the method
     * @param clazz the class that the method is a member of
     * @return the Method object for the method with the specified name in the specified class
     *
     * @throws CustomException  if the method is not found in the class
     *
     */
    private static Method findMethod(String methodName, Class<?> clazz) {
        return Arrays.stream(ReflectionUtils.getDeclaredMethods(clazz))
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElseThrow(() -> new CustomException("INVALID_OBJECT_OR_METHOD", "Invalid object or method"));
    }
}
