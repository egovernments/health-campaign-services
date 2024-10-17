package org.egov.common.data.query.builder;

import org.apache.commons.lang3.StringUtils;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Scope(value="prototype")
public class SelectQueryBuilder implements GenericQueryBuilder {

    Map<String, Object> paramsMap = new HashMap<>();

    public Map<String, Object> getParamsMap(){
        return paramsMap;
    }

    @Override
    public String build(Object object) throws QueryBuilderException {
        String tableName = null;
        try {
            tableName = GenericQueryBuilder.getTableName(object.getClass());
        } catch (Exception exception) {
            throw new QueryBuilderException(exception.getMessage());
        }

        return build(object, tableName);
    }

    public String build(Object object, String tableName) throws QueryBuilderException {
        StringBuilder queryStringBuilder = null;
        try {
            List<String> whereClauses = GenericQueryBuilder.getFieldsWithCondition(object, QueryFieldChecker.isNotNull, paramsMap);
            queryStringBuilder = GenericQueryBuilder.generateQuery(GenericQueryBuilder.selectQueryTemplate(tableName), whereClauses);
        } catch (Exception exception) {
            throw new QueryBuilderException(exception.getMessage());
        }
        return queryStringBuilder.toString();
    }
}
