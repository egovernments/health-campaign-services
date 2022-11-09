package org.digit.health.common.data.query.builder;

import org.digit.health.common.data.query.exception.QueryBuilderException;

import java.util.List;

public class UpdateQueryBuilder implements GenericQueryBuilder{
    @Override
    public String build(Object object) throws QueryBuilderException {
        StringBuilder queryStringBuilder = null;
        try {
            String tableName = GenericQueryBuilder.getTableName(object.getClass());
            List<String> fieldsToUpdate = GenericQueryBuilder.getFieldsWithCondition(object,  QueryFieldChecker.isNotNull);
            List<String> fieldsToUpdateWith = GenericQueryBuilder.getFieldsWithCondition(object,  QueryFieldChecker.isAnnotatedWithUpdateBy);
            queryStringBuilder = GenericQueryBuilder.generateQuery(GenericQueryBuilder.updateQueryTemplate(tableName), fieldsToUpdate, fieldsToUpdateWith);
        } catch (Exception exception) {
            throw new QueryBuilderException(exception.getMessage());
        }
        return queryStringBuilder.toString();
    }
}
