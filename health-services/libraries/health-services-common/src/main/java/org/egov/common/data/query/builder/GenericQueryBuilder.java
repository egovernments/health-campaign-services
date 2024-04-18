package org.egov.common.data.query.builder;

import org.egov.common.data.query.annotations.Table;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.utils.ObjectUtils;

import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public interface GenericQueryBuilder {
    String build(Object object) throws QueryBuilderException;

    static String getTableName(Class reflectClass){
        Table table = (Table) reflectClass.getAnnotation(Table.class);
        return table.name();
    }

    static String selectQueryTemplate(String tableName){
        return String.format("SELECT * FROM %s", tableName);
    }

    static String updateQueryTemplate(String tableName){
        return String.format("UPDATE %s", tableName);
    }

    static String generateClause(String clauseName, String seperator, List<String> queryParameters){
        StringBuilder clauseBuilder = new StringBuilder();
        if (queryParameters.isEmpty()) {
            return " ";
        }
        clauseBuilder.append(String.format(" %s ", clauseName));
        clauseBuilder.append(String.format(queryParameters.get(0)));
        IntStream.range(1, queryParameters.size()).forEach(i ->
                clauseBuilder.append(String.format(" %s %s", seperator ,queryParameters.get(i))));
        return clauseBuilder.toString();
    }

    static StringBuilder generateQuery(String queryTemplate, List<String> setClauseFields, List<String> whereClauseFields){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(queryTemplate);
        stringBuilder.append(generateClause("SET", ",", setClauseFields));
        stringBuilder.append(generateClause("WHERE", "AND", whereClauseFields));
        return stringBuilder;
    }

    static StringBuilder generateQuery(String queryTemplate, List<String> whereClauseFields){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(queryTemplate);
        stringBuilder.append(generateClause("WHERE", "AND", whereClauseFields));
        return stringBuilder;
    }

    /**
     * Retrieves fields of an object based on a condition and constructs where clauses for a query.
     *
     * @param object          the object for which fields are to be retrieved
     * @param checkCondition the condition to check for each field
     * @param paramsMap       a map to store parameter values for the query
     * @return a list of where clauses based on the fields and condition
     */
    static List<String> getFieldsWithCondition(Object object, QueryFieldChecker checkCondition, Map<String, Object> paramsMap) {
        // List to store where clauses
        List<String> whereClauses = new ArrayList<>();

        // Iterate through all declared fields of the object
        Arrays.stream(object.getClass().getDeclaredFields()).forEach(field -> {
            // Check if the field is of type LocalDate or enum
            if (field.getType().equals(LocalDate.class) || field.getType().isEnum()) {
                // Skip processing for LocalDate and enum fields
                // No condition applied
            } else {
                // Make the field accessible for manipulation
                field.setAccessible(true);
                try {
                    // Check if the field meets the condition and is not annotated with exclude
                    if (!field.getType().isPrimitive() && checkCondition.check(field, object)
                            && QueryFieldChecker.isNotAnnotatedWithExclude.check(field, object)) {
                        // If the field is a wrapper object
                        if (ObjectUtils.isWrapper(field)) {
                            // Retrieve field name
                            String fieldName = field.getName();
                            // Add parameter to paramsMap
                            paramsMap.put(fieldName, field.get(object));
                            // Add where clause to list
                            whereClauses.add(String.format("%s=:%s", fieldName, fieldName));
                        }
                        // If the field is an ArrayList of Strings
                        else if (field.getType().isAssignableFrom(ArrayList.class)
                                && field.getGenericType() instanceof ParameterizedType
                                && ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0].equals(String.class)) {
                            // Cast field value to ArrayList<String>
                            ArrayList<String> arrayList = (ArrayList<String>) field.get(object);
                            // Retrieve field name
                            String fieldName = field.getName();
                            // Check if the ArrayList is not null or empty
                            if (arrayList != null && !arrayList.isEmpty()) {
                                // Add IN clause to list
                                whereClauses.add(String.format("%s IN (:%s)", fieldName, fieldName));
                                // Add parameter to paramsMap
                                paramsMap.put(fieldName, arrayList);
                            }
                        } else {
                            // If the field is not a wrapper object or ArrayList<String>, recursively process nested objects
                            Object objectAtField = field.get(object);
                            if (objectAtField != null) {
                                // Recursively call the method to retrieve fields from nested object
                                whereClauses.addAll(getFieldsWithCondition(objectAtField, checkCondition, paramsMap));
                            }
                        }
                    }
                } catch (IllegalAccessException e) {
                    // Throw a runtime exception if there's an issue accessing the field
                    throw new RuntimeException(e);
                }
            }
        });
        return whereClauses;
    }

}
