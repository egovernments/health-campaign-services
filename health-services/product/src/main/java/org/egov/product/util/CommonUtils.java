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

    private static Method getMethod(String methodName, Class clazz) {
        return Arrays.stream(ReflectionUtils.getDeclaredMethods(clazz))
                .filter(m -> m.getName().equals(methodName))
                .findFirst().orElseThrow(() -> new CustomException("INVALID_OBJECT", "Invalid object"));
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
        List<T> validProductIds = validator.apply(idsToValidateList);
        List<T> invalidProductIds = CommonUtils.getDifference(idsToValidateList, validProductIds);
        if (!invalidProductIds.isEmpty()) {
            log.error("Invalid IDs");
            throw new CustomException("INVALID_ID", invalidProductIds.toString());
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

    public static AuditDetails getAuditDetailsForUpdate(AuditDetails existingAuditDetails, String uuid) {
        log.info("Creating audit details for update api");
        return AuditDetails.builder()
                .createdBy(existingAuditDetails.getCreatedBy())
                .createdTime(existingAuditDetails.getCreatedTime())
                .lastModifiedBy(uuid)
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
        Class objClass = objList.stream().findAny().get().getClass();
        Method rowVersionMethod = getMethod("getRowVersion", objClass);
        Method getIdMethod = getMethod("getId", objClass);
        Set<Object> rowVersionMismatch = objList.stream()
                .filter(obj -> !Objects.equals(ReflectionUtils.invokeMethod(rowVersionMethod, obj),
                        ReflectionUtils.invokeMethod(rowVersionMethod,
                                idToObjMap.get(ReflectionUtils.invokeMethod(getIdMethod, obj)))))
                .map(obj -> ReflectionUtils.invokeMethod(getIdMethod, obj)).collect(Collectors.toSet());
        if (!rowVersionMismatch.isEmpty()) {
            throw new CustomException("ROW_VERSION_MISMATCH", rowVersionMismatch.toString());
        }
    }
}
