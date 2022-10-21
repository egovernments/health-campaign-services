package org.digit.health.sync.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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

    QueryBuilderUtils queryUtils = new QueryBuilderUtils();
    QueryFieldConditionChecker fieldConditionChecker;

    private StringBuilder generateQuery(String queryTemplate, List<String> whereClauseFields){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(queryTemplate);
        stringBuilder.append(queryUtils.generateClause("WHERE", "AND", whereClauseFields));
        return stringBuilder;
    }

    private StringBuilder generateQuery(String queryTemplate, List<String> setClauseFields, List<String> whereClauseFields){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(queryTemplate);
        stringBuilder.append(queryUtils.generateClause("SET", ",", setClauseFields));
        stringBuilder.append(queryUtils.generateClause("WHERE", "AND", whereClauseFields));
        return stringBuilder;
    }

    @Override
    public String buildSelectQuery(Object object) {
        StringBuilder queryStringBuilder = null;
        try {
            String tableName = queryUtils.getTableName(object.getClass());
            List<String> whereClauses = queryUtils.getFieldsWithCondition(object, fieldConditionChecker.checkIfFieldIsNotNull);
            queryStringBuilder = generateQuery(DefaultQueryTemplate.select(tableName), whereClauses);
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
        return queryStringBuilder.toString();
    }

    @Override
    public String buildUpdateQuery(Object object) {
        StringBuilder queryStringBuilder = null;
        try {
            String tableName = queryUtils.getTableName(object.getClass());
            List<String> fieldsToUpdate = queryUtils.getFieldsWithCondition(object,  fieldConditionChecker.checkIfFieldIsNotNull);
            List<String> fieldsToUpdateWith = queryUtils.getFieldsWithCondition(object,  fieldConditionChecker.checkIfUpdateByAnnotationIsPresent);
            queryStringBuilder = generateQuery(DefaultQueryTemplate.update(tableName), fieldsToUpdate, fieldsToUpdateWith);
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
        return queryStringBuilder.toString();
    }

}