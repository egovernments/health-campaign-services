package org.digit.health.common.data.query.builder;

import org.digit.health.common.data.query.annotations.Table;
import org.digit.health.common.data.query.exception.QueryBuilderException;
import org.digit.health.common.utils.DataUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    static List<String> getFieldsWithCondition(Object object, QueryFieldChecker checkCondition){
        List<String> whereClauses = new ArrayList<>();
        Arrays.stream(object.getClass().getDeclaredFields()).forEach(field -> {
            field.setAccessible(true);
            try {
                if(!field.getType().isPrimitive() && checkCondition.check(field, object)){
                    if(DataUtils.isWrapper(field)){
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
