package org.egov.common.utils;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.ApiDetails;
import org.egov.common.models.Error;
import org.egov.common.models.ErrorDetails;
import org.egov.common.validator.Validator;
import org.egov.tracer.model.CustomException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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

import static org.egov.common.utils.ValidatorUtils.getErrorForNullId;

@Slf4j
public class CommonUtils {

    public static final String GET_API_OPERATION = "getApiOperation";

    private static final Map<Class<?>, Map<String, Method>> methodCache = new HashMap<>();

    private CommonUtils() {}


    public static boolean isForUpdate(Object obj) {
        Method getApiOperationMethod = getMethod(GET_API_OPERATION, obj.getClass());
        Object apiOperation = ReflectionUtils.invokeMethod(getApiOperationMethod, obj);
        if (apiOperation == null) {
            return false;
        }
        Method nameMethod = CommonUtils.getMethod("name", Enum.class);
        return "UPDATE".equals(ReflectionUtils.invokeMethod(nameMethod, apiOperation));
    }

    public static boolean isForDelete(Object obj) {
        Method getApiOperationMethod = getMethod(GET_API_OPERATION, obj.getClass());
        Object apiOperation = ReflectionUtils.invokeMethod(getApiOperationMethod, obj);
        if (apiOperation == null) {
            return false;
        }
        Method nameMethod = CommonUtils.getMethod("name", Enum.class);
        return "DELETE".equals(ReflectionUtils.invokeMethod(nameMethod, apiOperation));
    }

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

    public static <T> void validateIds(Set<T> idsToValidate, UnaryOperator<List<T>> validator) {
        List<T> idsToValidateList = new ArrayList<>(idsToValidate);
        List<T> validIds = validator.apply(idsToValidateList);
        List<T> invalidIds = CommonUtils.getDifference(idsToValidateList, validIds);
        if (!invalidIds.isEmpty()) {
            log.error("Invalid IDs {}", invalidIds);
            throw new CustomException("INVALID_ID", invalidIds.toString());
        }
    }

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

    public static boolean isSearchByIdOnly(Object obj) {
        return isSearchByIdOnly(obj, "id");
    }

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

    public static <T> void checkRowVersion(Map<String, T> idToObjMap, List<T> objList) {
        Class<?> objClass = getObjClass(objList);
        checkRowVersion(idToObjMap, objList, getMethod("getId", objClass));
    }

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

    public static <T> List<T> getEntitiesWithMismatchedRowVersion(Map<String, T> idToObjMap,
                                                                  List<T> objList, Method idMethod) {
        Class<?> objClass = getObjClass(objList);
        Method rowVersionMethod = getMethod("getRowVersion", objClass);
        return objList.stream()
                .filter(obj -> !Objects.equals(ReflectionUtils.invokeMethod(rowVersionMethod, obj),
                        ReflectionUtils.invokeMethod(rowVersionMethod,
                                idToObjMap.get(ReflectionUtils.invokeMethod(idMethod, obj)))))
                .map(obj -> idToObjMap.get(ReflectionUtils.invokeMethod(idMethod, obj)))
                .collect(Collectors.toList());
    }

    public static <T> String getTenantId(List<T> objList) {
        Object obj = objList.stream().findAny().get();
        Method getTenantIdMethod = getMethod("getTenantId", obj.getClass());
        String tenantId = (String) ReflectionUtils.invokeMethod(getTenantIdMethod, obj);
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

    public static <T> Method getIdMethod(List<T> objList) {
        return getIdMethod(objList, "id", "clientReferenceId");
    }

    public static <T> Method getIdMethod(List<T> objList, String idFieldName) {
        String idMethodName = "get" + idFieldName.substring(0, 1).toUpperCase()
                + idFieldName.substring(1);
        return getMethod(idMethodName, getObjClass(objList));
    }

    public static <T> Method getIdMethod(List<T> objList, String idField, String clientReferenceIdField) {
        String idMethodName = "get" + idField.substring(0, 1).toUpperCase()
                + idField.substring(1);
        String clientReferenceIdMethodName = "get" + clientReferenceIdField.substring(0, 1).toUpperCase()
                + clientReferenceIdField.substring(1);
        try{
            Method getId = getMethod(idMethodName, getObjClass(objList));
            Object value = ReflectionUtils.invokeMethod(getId, objList.stream().findAny().get());
            if (value != null) {
                return getId;
            }
        } catch (CustomException e){
            log.error(e.getMessage());
        }

        return getMethod(clientReferenceIdMethodName, getObjClass(objList));
    }

    public static <T> void enrichId(List<T> objList, List<String> idList) {
        Class<?> objClass = getObjClass(objList);
        Method setIdMethod = getMethod("setId", objClass);
        IntStream.range(0, objList.size())
                .forEach(i -> {
                    final Object obj = objList.get(i);
                    ReflectionUtils.invokeMethod(setIdMethod, obj, idList.get(i));
                });
    }

    public static <T> void enrichForUpdate(Map<String, T> idToObjMap, Object request) {
        Class<?> objClass = getObjClass(Arrays.asList(idToObjMap.values().toArray()));
        Class<?> requestObjClass = request.getClass();
        Method getRowVersionMethod = getMethod("getRowVersion", objClass);
        Method setRowVersionMethod = getMethod("setRowVersion", objClass);
        Method setAuditDetailsMethod = getMethod("setAuditDetails", objClass);
        Method getAuditDetailsMethod = getMethod("getAuditDetails", objClass);

        Method getRequestInfoMethod = getMethod("getRequestInfo", requestObjClass);
        idToObjMap.keySet().forEach(i -> {
            Object obj = idToObjMap.get(i);
            Integer rowVersion = (Integer) ReflectionUtils.invokeMethod(getRowVersionMethod, obj);
            ReflectionUtils.invokeMethod(setRowVersionMethod, obj, rowVersion + 1);
            RequestInfo requestInfo = (RequestInfo) ReflectionUtils
                    .invokeMethod(getRequestInfoMethod, request);
            AuditDetails existingAuditDetails = (AuditDetails) ReflectionUtils.invokeMethod(getAuditDetailsMethod, obj);
            AuditDetails auditDetailsForUpdate = getAuditDetailsForUpdate(existingAuditDetails, requestInfo.getUserInfo().getUuid());
            ReflectionUtils.invokeMethod(setAuditDetailsMethod, obj, auditDetailsForUpdate);
        });
    }

    public static <T> void enrichForUpdate(Map<String, T> idToObjMap, List<T> existingObjList, Object request) {
        Class<?> objClass = getObjClass(existingObjList);
        enrichForUpdate(idToObjMap, existingObjList, request, getMethod("getId", objClass));
    }

    public static <T> void enrichForUpdate(Map<String, T> idToObjMap, List<T> existingObjList, Object request, Method idMethod) {
        Class<?> objClass = getObjClass(existingObjList);
        Class<?> requestObjClass = request.getClass();
        Method setIsDeletedMethod = getMethod("setIsDeleted", objClass);
        Method getRowVersionMethod = getMethod("getRowVersion", objClass);
        Method setRowVersionMethod = getMethod("setRowVersion", objClass);
        Method getAuditDetailsMethod = getMethod("getAuditDetails", objClass);
        Method setAuditDetailsMethod = getMethod("setAuditDetails", objClass);
        Method getRequestInfoMethod = getMethod("getRequestInfo", requestObjClass);
        IntStream.range(0, existingObjList.size()).forEach(i -> {
            Object obj = idToObjMap.get(ReflectionUtils.invokeMethod(idMethod,
                    existingObjList.get(i)));
            try {
                Method getApiOperationMethod = getMethod(GET_API_OPERATION, requestObjClass);
                Object apiOperation = ReflectionUtils.invokeMethod(getApiOperationMethod, request);
                Method nameMethod = CommonUtils.getMethod("name", Enum.class);
                if ("DELETE".equals(ReflectionUtils.invokeMethod(nameMethod, apiOperation))) {
                    ReflectionUtils.invokeMethod(setIsDeletedMethod, obj, true);
                }
            } catch (Exception exception) {
                // Do nothing remove later
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

    public static <T> Map<String, T> getIdToObjMap(List<T> objList) {
        Class<?> objClass = getObjClass(objList);
        return getIdToObjMap(objList, getMethod("getId", objClass));
    }

    public static <T> Map<String, T> getIdToObjMap(List<T> objList, Method idMethod) {
        return objList.stream().collect(Collectors.toMap(obj -> (String) ReflectionUtils
                .invokeMethod(idMethod, obj), obj -> obj, (obj1, obj2) -> obj2));
    }

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

    public static <T> List<T> checkNonExistentEntities(Map<String, T> idToObjInRequestMap, List<T> objInDbList,
                                                       Method idMethod) {
        if (idToObjInRequestMap.size() > objInDbList.size()) {
            List<String> idsForObjInDb = getIdList(objInDbList, idMethod);
            return idToObjInRequestMap.entrySet().stream()
                    .filter(e -> !idsForObjInDb.contains(e.getKey())).map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
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
            Map<String, Method> methodMap = new HashMap<>();
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
                    apiDetails.setRequestBody(newRequest);
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
            if (!((CustomException) exception).getCode().equals("IDGEN_ERROR")) {
                errorType = Error.ErrorType.RECOVERABLE;
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
            populateErrorDetails(request, errorDetailsMap, errorListMap, setPayloadMethodName);
        });
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
        Error.ErrorType errorType = Error.ErrorType.NON_RECOVERABLE;
        String errorCode = "INTERNAL_SERVER_ERROR";
        if (exception instanceof CustomException) {
            errorCode = ((CustomException) exception).getCode();
            // in case further cases come up, we can add more cases in a set and check using contains.
            if (!((CustomException) exception).getCode().equals("IDGEN_ERROR")) {
                errorType = Error.ErrorType.RECOVERABLE;
            }
        }
        List<Error> errorList = new ArrayList<>();
        errorList.add(Error.builder().errorMessage(exception.getMessage())
                .errorCode(errorCode)
                .type(errorType)
                .exception(new CustomException(errorCode, exception.getMessage())).build());
        validPayloads.forEach(payload -> {
            errorListMap.put(payload, errorList);
        });
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
        return Arrays.stream(ReflectionUtils.getDeclaredMethods(clazz))
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElseThrow(() -> new CustomException("INVALID_OBJECT_OR_METHOD", "Invalid object or method"));
    }
}
