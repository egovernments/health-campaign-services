package org.egov.common.data.query.builder;

import org.egov.common.data.query.annotations.Table;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.utils.ObjectUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public interface GenericQueryBuilder {
    String build(String schemaTemplate, Object object) throws QueryBuilderException;

    static String getTableName(Class reflectClass){
        Table table = (Table) reflectClass.getAnnotation(Table.class);
        return table.name();
    }

    static String selectQueryTemplate(String tableName){
        return String.format("SELECT * FROM %s", tableName);
    }

    static String selectQueryTemplate(String schemaTemplate, String tableName){
        if(!org.springframework.util.ObjectUtils.isEmpty(schemaTemplate)) {
            return String.format("SELECT * FROM %s.%s", schemaTemplate, tableName);
        }
        return String.format("SELECT * FROM %s", tableName);
    }

    static String updateQueryTemplate(String tableName){
        return String.format("UPDATE %s", tableName);
    }

    static String updateQueryTemplate(String schemaTemplate, String tableName){
        if(!org.springframework.util.ObjectUtils.isEmpty(schemaTemplate)) {
            return String.format("UPDATE %s.%s", schemaTemplate, tableName);
        }
        return String.format("UPDATE %s", tableName);
    }

    /**
     * Generates a clause for a SQL query.
     *
     * @param clauseName      the name of the clause (e.g., "WHERE", "AND", "OR")
     * @param separator       the separator between query parameters (e.g., "=", "LIKE")
     * @param queryParameters the list of query parameters to be included in the clause
     * @return the generated clause as a string
     */
    static String generateClause(String clauseName, String separator, List<String> queryParameters) {
        // Initialize a StringBuilder to construct the clause
        StringBuilder clauseBuilder = new StringBuilder();

        // If there are no query parameters, return an empty string
        if (queryParameters.isEmpty()) {
            return "";
        }

        // Append the clause name to the clauseBuilder
        clauseBuilder.append(String.format(" %s ", clauseName));

        // Append the first query parameter to the clauseBuilder
        clauseBuilder.append(String.format(queryParameters.get(0)));

        // Append the remaining query parameters to the clauseBuilder with the specified separator
        IntStream.range(1, queryParameters.size()).forEach(i ->
                clauseBuilder.append(String.format(" %s %s", separator, queryParameters.get(i))));

        // Convert the clauseBuilder to a string and return it
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
        getAllDeclaredFields(object.getClass()).forEach(field -> {
            // Check if the field is of type LocalDate or enum
            if (field.getType().equals(LocalDate.class) || field.getType().isEnum() || Modifier.isStatic(field.getModifiers())) {
                // Skip processing for LocalDate and enum fields
                // No condition applied
            } else {
                try {
                    // Make the field accessible for manipulation FIXME TODO
                    field.setAccessible(true);
                } catch (Exception exception) {
                    return;
                }

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


    /**
     * This method retrieves all declared fields from the given class and its superclasses.
     *
     * @param clazz the class whose fields are to be retrieved
     * @return a list of all declared fields in the class and its superclasses, excluding Object class fields
     */
    static List<Field> getAllDeclaredFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                fields.add(field);
            }
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
