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

    /**
     * Generates a SQL clause for selection from a database table
     *
     * @param schemaTemplate      the name of the database schema
     * @param object              an object of the class for which query needs to be built
     * @return the generated clause as a string
     */
    @Override
    public String build(String schemaTemplate, Object object) throws QueryBuilderException {
        String tableName = null;
        try {
            tableName = GenericQueryBuilder.getTableName(object.getClass());
        } catch (Exception exception) {
            throw new QueryBuilderException(exception.getMessage());
        }

        return build(object, tableName, schemaTemplate);
    }

    /**
     * Generates a SQL clause for selection from a database table
     *
     * @param schemaTemplate      the name of the database schema
     * @param tableName           the name of the database table
     * @param object              an object of the class for which query needs to be built
     * @return the generated clause as a string
     */
    public String build(Object object, String tableName, String schemaTemplate) throws QueryBuilderException {
        StringBuilder queryStringBuilder = null;
        try {
            List<String> whereClauses = GenericQueryBuilder.getFieldsWithCondition(object, QueryFieldChecker.isNotNull, paramsMap);
            queryStringBuilder = GenericQueryBuilder.generateQuery(GenericQueryBuilder.selectQueryTemplate(schemaTemplate, tableName), whereClauses);
        } catch (Exception exception) {
            throw new QueryBuilderException(exception.getMessage());
        }
        return queryStringBuilder.toString();
    }
}
