package org.digit.health.common.data.query.builder;

import org.digit.health.common.data.query.exception.QueryBuilderException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectQueryBuilder implements GenericQueryBuilder {

    Map<String, Object> paramsMap = new HashMap<>();

    public Map<String, Object> getParamsMap(){
        return paramsMap;
    }

    @Override
    public String build(Object object) throws QueryBuilderException {
        StringBuilder queryStringBuilder = null;
        try {
            String tableName = GenericQueryBuilder.getTableName(object.getClass());
            List<String> whereClauses = GenericQueryBuilder.getFieldsWithCondition(object, QueryFieldChecker.isNotNull, paramsMap);
            queryStringBuilder = GenericQueryBuilder.generateQuery(GenericQueryBuilder.selectQueryTemplate(tableName), whereClauses);
        } catch (Exception exception) {
            throw new QueryBuilderException(exception.getMessage());
        }
        return queryStringBuilder.toString();
    }
}
