package org.digit.health.sync.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Component
@Slf4j
public class DefaultQueryBuilder implements QueryBuilder {
    @Override
    public String buildSelectQuery(Object object) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            Annotation annotation = object.getClass().getDeclaredAnnotation(Table.class);
            Class<? extends Annotation> type = annotation.annotationType();
            String simpleName = object.getClass().getSimpleName();
            String firstChar = String.valueOf(simpleName.charAt(0));
            String tableName = firstChar.toLowerCase() + simpleName.substring(1);
            for (Method method : type.getDeclaredMethods()) {
                Object value = method.invoke(annotation, (Object[])null);
                if ("name".equals(method.getName())) {
                    tableName = (String) value;
                }
            }
            stringBuilder.append(String.format("SELECT * FROM %s", tableName));
            stringBuilder.append(" WHERE");
            List<String> whereClauses = new ArrayList<>();
            Arrays.stream(object.getClass().getDeclaredFields()).forEach(field -> {
                field.setAccessible(true);
                try {
                    Optional.ofNullable(field.get(object)).ifPresent(obj -> {
                        String fieldName = field.getName();
                        whereClauses.add(String.format(" %s:=%s", fieldName, fieldName));
                    });
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });
            stringBuilder.append(String.format(whereClauses.get(0)));
            IntStream.range(1, whereClauses.size()).forEach(i ->
                    stringBuilder.append(String.format(" AND%s", whereClauses.get(i))));
        } catch (Exception exception) {

        }
        return stringBuilder.toString();
    }
}
