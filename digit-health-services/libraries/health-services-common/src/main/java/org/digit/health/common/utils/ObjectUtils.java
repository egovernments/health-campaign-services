package org.digit.health.common.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

public class ObjectUtils {
    public static boolean isWrapper(Field field){
        Type type = field.getType();
        return (type == Double.class || type == Float.class || type == Long.class ||
                type == Integer.class || type == Short.class || type == Character.class ||
                type == Byte.class || type == Boolean.class || type == String.class);
    }
}
