package org.digit.health.sync.repository;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public class QueryBuilderUtils {

    public String getTableName(Class reflectClass){
        Table table = (Table) reflectClass.getAnnotation(Table.class);
        return table.name();
    }

    public String generateClause(String clauseName, String seperator, List<String> queryParameters){
        StringBuilder clauseBuilder = new StringBuilder();
        if(queryParameters.size() == 0){
            return "";
        }
        clauseBuilder.append(String.format(" %s ", clauseName));
        clauseBuilder.append(String.format(queryParameters.get(0)));
        IntStream.range(1, queryParameters.size()).forEach(i ->
                clauseBuilder.append(String.format(" %s %s", seperator ,queryParameters.get(i))));
        return clauseBuilder.toString();
    }

    public boolean isWrapper(Field field){
        Type type = field.getType();
        return (type == Double.class || type == Float.class || type == Long.class ||
                type == Integer.class || type == Short.class || type == Character.class ||
                type == Byte.class || type == Boolean.class || type == String.class);
    }

    public List<String> getFieldsWithCondition(Object object, QueryFieldConditionChecker checkCondition){
        List<String> whereClauses = new ArrayList<>();
        Arrays.stream(object.getClass().getDeclaredFields()).forEach(field -> {
            field.setAccessible(true);
            try {
                if(!field.getType().isPrimitive() && checkCondition.check(field, object)){
                    if(isWrapper(field)){
                        String fieldName = field.getName();
                        whereClauses.add(String.format("%s:=%s", fieldName, fieldName));
                    }else{
                        Object objectAtField = field.get(object);
                        if(objectAtField != null){
                            whereClauses.addAll(getFieldsWithCondition(objectAtField, checkCondition));
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
        return whereClauses;
    }
}