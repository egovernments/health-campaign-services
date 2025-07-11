package org.egov.common.data.query.builder;

import org.egov.common.data.query.exception.QueryBuilderException;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Scope(value = "prototype")
public class UpdateQueryBuilder implements GenericQueryBuilder {

    Map<String, Object> paramsMap = new HashMap<>();

    public Map<String, Object> getParamsMap(){
        return paramsMap;
    }

    /**
     * Generates a SQL clause for updating a database table row
     *
     * @param schemaTemplate      the name of the database schema
     * @param object              an object of the class for which query needs to be built
     * @return the generated clause as a string
     */
    @Override
    public String build(String schemaTemplate, Object object) throws QueryBuilderException {
        StringBuilder queryStringBuilder = null;
        try {
            String tableName = GenericQueryBuilder.getTableName(object.getClass());
            List<String> fieldsToUpdate = GenericQueryBuilder.getFieldsWithCondition(object,  QueryFieldChecker.isNotNull, paramsMap);
            List<String> fieldsToUpdateWith = GenericQueryBuilder.getFieldsWithCondition(object,  QueryFieldChecker.isAnnotatedWithUpdateBy, paramsMap);
            queryStringBuilder = GenericQueryBuilder.generateQuery(GenericQueryBuilder.updateQueryTemplate(schemaTemplate, tableName), fieldsToUpdate, fieldsToUpdateWith);
        } catch (Exception exception) {
            throw new QueryBuilderException(exception.getMessage());
        }
        return queryStringBuilder.toString();
    }
}
