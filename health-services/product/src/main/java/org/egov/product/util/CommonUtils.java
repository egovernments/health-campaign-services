package org.egov.product.util;

import org.egov.product.web.models.ApiOperation;
import org.egov.tracer.model.CustomException;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

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
}
