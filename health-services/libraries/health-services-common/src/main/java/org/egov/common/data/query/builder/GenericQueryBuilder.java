package org.egov.common.data.query.builder;

import org.egov.common.data.query.annotations.Table;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.utils.ObjectUtils;

import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public interface GenericQueryBuilder {
    String build(Object object) throws QueryBuilderException;

    static String getTableName(Class reflectClass){
        Table table = (Table) reflectClass.getAnnotation(Table.class);
        return table.name();
    }

    static String selectQueryTemplate(String tableName){
        return String.format("SELECT * FROM %s", tableName);
    }

    static String updateQueryTemplate(String tableName){
        return String.format("UPDATE %s", tableName);
    }

    static String generateClause(String clauseName, String seperator, List<String> queryParameters){
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

    static StringBuilder generateQuery(String queryTemplate, List<String> setClauseFields, List<String> whereClauseFields){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(queryTemplate);
        stringBuilder.append(generateClause("SET", ",", setClauseFields));
        stringBuilder.append(generateClause("WHERE", "AND", whereClauseFields));
        return stringBuilder;
    }

    static StringBuilder generateQuery(String queryTemplate, List<String> whereClauseFields){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(queryTemplate);
        stringBuilder.append(generateClause("WHERE", "AND", whereClauseFields));
        return stringBuilder;
    }

    static List<String> getFieldsWithCondition(Object object, QueryFieldChecker checkCondition, Map<String, Object> paramsMap) {
        List<String> whereClauses = new ArrayList<>();
        Arrays.stream(object.getClass().getDeclaredFields()).forEach(field -> {
            if (field.getType().equals(LocalDate.class) || field.getType().isEnum()) {
                // do nothing
            } else {
                field.setAccessible(true);
                try {
                    if (!field.getType().isPrimitive() && checkCondition.check(field, object)
                            && QueryFieldChecker.isNotAnnotatedWithExclude.check(field, object)) {
                        if (ObjectUtils.isWrapper(field)) {
                            String fieldName = field.getName();
                            paramsMap.put(fieldName, field.get(object));
                            whereClauses.add(String.format("%s=:%s", fieldName, fieldName));
                        } else if (field.getType().isAssignableFrom(ArrayList.class)
                                && field.getGenericType() instanceof ParameterizedType
                                && ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0].equals(String.class)) {
                            ArrayList<String> arrayList = (ArrayList<String>) field.get(object);
                            if (arrayList != null && !arrayList.isEmpty()) {
                                String fieldName = field.getName();
                                StringBuilder value = new StringBuilder();
                                for (String arrayValue : arrayList) {
                                    String paramName = fieldName + "_" + arrayList.indexOf(arrayValue);
                                    value.append(":" + paramName + ",");
                                    paramsMap.put(paramName, arrayValue);
                                }
                                whereClauses.add(String.format("%s IN (%s)", fieldName, value.deleteCharAt(value.length() - 1).toString()));
                            }
                        } else {
                            Object objectAtField = field.get(object);
                            if (objectAtField != null) {
                                whereClauses.addAll(getFieldsWithCondition(objectAtField, checkCondition, paramsMap));
                            }
                        }
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return whereClauses;
    }
}
