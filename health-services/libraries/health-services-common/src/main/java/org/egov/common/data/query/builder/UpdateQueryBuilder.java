package org.egov.common.data.query.builder;

import org.egov.common.data.query.exception.QueryBuilderException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public final class UpdateQueryBuilder implements GenericQueryBuilder {

    /**
     * Generates a SQL clause for updating a database table row
     *
     * @param schemaTemplate      the name of the database schema
     * @param object              an object of the class for which query needs to be built
     * @param paramsMap           caller-owned map the named parameters are written into
     * @return the generated clause as a string
     */
    @Override
    public String build(String schemaTemplate, Object object, Map<String, Object> paramsMap)
            throws QueryBuilderException {
        try {
            String tableName = GenericQueryBuilder.getTableName(object.getClass());
            List<String> fieldsToUpdate = GenericQueryBuilder.getFieldsWithCondition(object,
                    QueryFieldChecker.isNotNull, paramsMap);
            List<String> fieldsToUpdateWith = GenericQueryBuilder.getFieldsWithCondition(object,
                    QueryFieldChecker.isAnnotatedWithUpdateBy, paramsMap);
            return GenericQueryBuilder.generateQuery(
                    GenericQueryBuilder.updateQueryTemplate(schemaTemplate, tableName),
                    fieldsToUpdate, fieldsToUpdateWith).toString();
        } catch (Exception exception) {
            throw new QueryBuilderException(exception.getMessage());
        }
    }
}
