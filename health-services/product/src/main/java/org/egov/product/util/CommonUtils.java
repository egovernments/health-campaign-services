package org.egov.product.util;

import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.product.web.models.ApiOperation;
import org.egov.tracer.model.CustomException;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class CommonUtils {

    public static final String GET_API_OPERATION = "getApiOperation";

    private CommonUtils() {}


    public static boolean isForUpdate(Object obj) {
        Method method = getMethod(GET_API_OPERATION, obj.getClass());
        return ApiOperation.UPDATE.equals(ReflectionUtils.invokeMethod(method, obj));
    }

    public static boolean isForDelete(Object obj) {
        Method method = getMethod(GET_API_OPERATION, obj.getClass());
        return ApiOperation.DELETE.equals(ReflectionUtils.invokeMethod(method, obj));
    }

    public static boolean isForCreate(Object obj) {
        Method method = getMethod(GET_API_OPERATION, obj.getClass());
        return ApiOperation.CREATE.equals(ReflectionUtils.invokeMethod(method, obj))
                || null == ReflectionUtils.invokeMethod(method, obj);
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

    public static AuditDetails getAuditDetailsForUpdate(AuditDetails existingAuditDetails,
                                                        String modifiedByUuid) {
        log.info("Creating audit details for update api");
        return AuditDetails.builder()
                .createdBy(existingAuditDetails.getCreatedBy())
                .createdTime(existingAuditDetails.getCreatedTime())
                .lastModifiedBy(modifiedByUuid)
                .lastModifiedTime(System.currentTimeMillis()).build();
    }

    public static boolean isSearchByIdOnly(Object obj) {
        Class objClass = obj.getClass();
        Method setIdMethod = getMethod("setId", objClass);
        Method getIdMethod = getMethod("getId", objClass);

        Object finalObject = null;
        try {
            finalObject = objClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        Object id = ReflectionUtils.invokeMethod(getIdMethod, obj);
        ReflectionUtils.invokeMethod(setIdMethod, finalObject, id);

        String actual = obj.toString();
        String expected = finalObject.toString();
        return actual.equals(expected);
    }


    public static <T> void checkRowVersion(Map<String, T> idToObjMap, List<T> objList) {
        Class objClass = getObjClass(objList);
        Method rowVersionMethod = getMethod("getRowVersion", objClass);
        Method getIdMethod = getMethod("getId", objClass);
        Set<Object> rowVersionMismatch = objList.stream()
                .filter(obj -> !Objects.equals(ReflectionUtils.invokeMethod(rowVersionMethod, obj),
                        ReflectionUtils.invokeMethod(rowVersionMethod,
                                idToObjMap.get(ReflectionUtils.invokeMethod(getIdMethod, obj)))))
                .map(obj -> ReflectionUtils.invokeMethod(getIdMethod, obj)).collect(Collectors.toSet());
        if (!rowVersionMismatch.isEmpty()) {
            log.error("Mismatch in row versions {}", rowVersionMismatch);
            throw new CustomException("ROW_VERSION_MISMATCH", rowVersionMismatch.toString());
        }
    }

    public static <T> String getTenantId(List<T> objList) {
        Object obj = objList.stream().findAny().get();
        Method getTenantIdMethod = getMethod("getTenantId", obj.getClass());
        String tenantId = (String) ReflectionUtils.invokeMethod(getTenantIdMethod, obj);
        log.info("Tenant ID {}", tenantId);
        return tenantId;
    }

    public static <T> void enrichForCreate(List<T> objList, List<String> idList, RequestInfo requestInfo) {
        AuditDetails auditDetails = getAuditDetailsForCreate(requestInfo);
        Class objClass = getObjClass(objList);
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

    public static <T> void enrichForUpdate(Map<String, T> idToObjMap, List<T> existingObjList, Object request) {
        Class objClass = getObjClass(existingObjList);
        Class requestObjClass = request.getClass();
        Method getIdMethod = getMethod("getId", objClass);
        Method setIsDeletedMethod = getMethod("setIsDeleted", objClass);
        Method getRowVersionMethod = getMethod("getRowVersion", objClass);
        Method setRowVersionMethod = getMethod("setRowVersion", objClass);
        Method getAuditDetailsMethod = getMethod("getAuditDetails", objClass);
        Method setAuditDetailsMethod = getMethod("setAuditDetails", objClass);
        Method getApiOperationMethod = getMethod(GET_API_OPERATION, requestObjClass);
        Method getRequestInfoMethod = getMethod("getRequestInfo", requestObjClass);
        IntStream.range(0, existingObjList.size()).forEach(i -> {
            Object obj = idToObjMap.get(ReflectionUtils.invokeMethod(getIdMethod,
                    existingObjList.get(i)));
            if (ApiOperation.DELETE.equals(ReflectionUtils
                    .invokeMethod(getApiOperationMethod, request))) {
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

    public static <T> Map<String, T> getIdToObjMap(List<T> objList) {
        Class objClass = getObjClass(objList);
        return objList.stream().collect(Collectors.toMap(obj -> (String) ReflectionUtils
                .invokeMethod(getMethod("getId", objClass), obj), obj -> obj));
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

    public static <T> List<String> getIdList(List<T> objInDbList) {
        return objInDbList.stream().map(obj -> (String) ReflectionUtils
                        .invokeMethod(getMethod("getId", obj.getClass()), obj))
                .collect(Collectors.toList());
    }

    private static <T> Class<?> getObjClass(List<T> objList) {
        return objList.stream().findAny().get().getClass();
    }

    private static Method getMethod(String methodName, Class clazz) {
        return Arrays.stream(ReflectionUtils.getDeclaredMethods(clazz))
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElseThrow(() -> new CustomException("INVALID_OBJECT", "Invalid object"));
    }
}
