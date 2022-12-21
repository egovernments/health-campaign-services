package org.egov.product.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.product.web.models.ApiOperation;
import org.egov.tracer.model.CustomException;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
}
