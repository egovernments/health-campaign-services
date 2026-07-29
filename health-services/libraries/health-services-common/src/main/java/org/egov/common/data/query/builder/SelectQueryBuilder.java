package org.egov.common.data.query.builder;

import org.egov.common.data.query.exception.QueryBuilderException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public final class SelectQueryBuilder implements GenericQueryBuilder {

    /**
     * Generates a SQL clause for selection from a database table
     *
     * @param schemaTemplate      the name of the database schema
     * @param object              an object of the class for which query needs to be built
     * @param paramsMap           caller-owned map the named parameters are written into
     * @return the generated clause as a string
     */
    @Override
    public String build(String schemaTemplate, Object object, Map<String, Object> paramsMap)
            throws QueryBuilderException {
        String tableName;
        try {
            tableName = GenericQueryBuilder.getTableName(object.getClass());
        } catch (Exception exception) {
            throw new QueryBuilderException(exception.getMessage());
        }
        return build(object, tableName, schemaTemplate, paramsMap);
    }

    /**
     * Generates a SQL clause for selection from a database table
     *
     * @param schemaTemplate      the name of the database schema
     * @param tableName           the name of the database table
     * @param object              an object of the class for which query needs to be built
     * @param paramsMap           caller-owned map the named parameters are written into
     * @return the generated clause as a string
     */
    public String build(Object object, String tableName, String schemaTemplate,
                        Map<String, Object> paramsMap) throws QueryBuilderException {
        try {
            List<String> whereClauses = GenericQueryBuilder.getFieldsWithCondition(object,
                    QueryFieldChecker.isNotNull, paramsMap);
            return GenericQueryBuilder.generateQuery(
                    GenericQueryBuilder.selectQueryTemplate(schemaTemplate, tableName),
                    whereClauses).toString();
        } catch (Exception exception) {
            throw new QueryBuilderException(exception.getMessage());
        }
    }
}
