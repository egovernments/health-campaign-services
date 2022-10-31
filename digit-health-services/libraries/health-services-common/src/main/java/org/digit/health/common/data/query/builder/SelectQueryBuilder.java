package org.digit.health.common.data.query.builder;

import java.util.List;

public class SelectQueryBuilder implements GenericQueryBuilder {

    @Override
    public String build(Object object) {
        StringBuilder queryStringBuilder = null;
        try {
            String tableName = GenericQueryBuilder.getTableName(object.getClass());
            List<String> whereClauses = GenericQueryBuilder.getFieldsWithCondition(object, QueryFieldChecker.isNotNull);
            queryStringBuilder = GenericQueryBuilder.generateQuery(GenericQueryBuilder.selectQueryTemplate(tableName), whereClauses);
        } catch (Exception exception) {
            System.out.println(exception.getMessage());
        }
        return queryStringBuilder.toString();
    }
}
