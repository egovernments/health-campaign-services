package org.egov.common.data.query.builder;

import org.egov.common.data.query.exception.QueryBuilderException;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Scope(value = "prototype")
public class UpdateQueryBuilder implements GenericQueryBuilder{

    Map<String, Object> paramsMap = new HashMap<>();

    public Map<String, Object> getParamsMap(){
        return paramsMap;
    }
    @Override
    public String build(Object object) throws QueryBuilderException {
        StringBuilder queryStringBuilder = null;
        try {
            String tableName = GenericQueryBuilder.getTableName(object.getClass());
            List<String> fieldsToUpdate = GenericQueryBuilder.getFieldsWithCondition(object,  QueryFieldChecker.isNotNull, paramsMap);
            List<String> fieldsToUpdateWith = GenericQueryBuilder.getFieldsWithCondition(object,  QueryFieldChecker.isAnnotatedWithUpdateBy, paramsMap);
            queryStringBuilder = GenericQueryBuilder.generateQuery(GenericQueryBuilder.updateQueryTemplate(tableName), fieldsToUpdate, fieldsToUpdateWith);
        } catch (Exception exception) {
            throw new QueryBuilderException(exception.getMessage());
        }
        return queryStringBuilder.toString();
    }
}
