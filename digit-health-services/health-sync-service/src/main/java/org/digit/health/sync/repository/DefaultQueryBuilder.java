package org.digit.health.sync.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Component
@Slf4j
public class DefaultQueryBuilder implements QueryBuilder {

    private String getTableName(Class reflectClass){
        Table table = (Table) reflectClass.getAnnotation(Table.class);
        return table.name();
    }

    private boolean isWrapper(Field field){
        Type type = field.getType();
        return (type == Double.class || type == Float.class || type == Long.class ||
                type == Integer.class || type == Short.class || type == Character.class ||
                type == Byte.class || type == Boolean.class || type == String.class);
    }

    private List<String> getAllFeilds(Object object){
        List<String> whereClauses = new ArrayList<>();
        Arrays.stream(object.getClass().getDeclaredFields()).forEach(field -> {
            field.setAccessible(true);
            try {
                if(!field.getType().isPrimitive() && Optional.ofNullable(field.get(object)).isPresent()){
                    if(isWrapper(field)){
                        String fieldName = field.getName();
                        whereClauses.add(String.format(" %s:=%s", fieldName, fieldName));
                    }else{
                        whereClauses.addAll(getAllFeilds(field.get(object)));
                    }
                }//workimg

            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
        return whereClauses;
    }

    private StringBuilder buildQuery(String tableName, List<String> queryParameters){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("SELECT * FROM %s", tableName));
        if(queryParameters.size() > 0){
            stringBuilder.append(" WHERE");
            stringBuilder.append(String.format(queryParameters.get(0)));
            IntStream.range(1, queryParameters.size()).forEach(i ->
                    stringBuilder.append(String.format(" AND%s", queryParameters.get(i))));
        }
        return stringBuilder;
    }

    @Override
    public String buildSelectQuery(Object object) {
        StringBuilder queryStringBuilder = null;
        try {
            String tableName = getTableName(object.getClass());
            List<String> whereClauses = getAllFeilds(object);
            queryStringBuilder = buildQuery(tableName, whereClauses);
        } catch (Exception exception) {

        }
        return queryStringBuilder.toString();
    }
}
