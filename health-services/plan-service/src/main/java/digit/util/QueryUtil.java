package digit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import digit.config.Configuration;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import static digit.config.ServiceConstants.*;

@Component
public class QueryUtil {

    private Configuration config;

    private ObjectMapper objectMapper;

    private QueryUtil(Configuration config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    private static final Gson gson = new Gson();

    /**
     * This method aids in adding "WHERE" clause and "AND" condition depending on preparedStatementList i.e.,
     * if preparedStatementList is empty, it will understand that it is the first clause being added so it
     * will add "WHERE" to the query and otherwise it will
     *
     * @param query
     * @param preparedStmtList
     */
    public void addClauseIfRequired(StringBuilder query, List<Object> preparedStmtList) {
        if (preparedStmtList.isEmpty()) {
            query.append(" WHERE ");
        } else {
            query.append(" AND ");
        }
    }

    /**
     * This method returns a string with placeholders equal to the number of values that need to be put inside
     * "IN" clause
     *
     * @param size
     * @return
     */
    public String createQuery(Integer size) {
        StringBuilder builder = new StringBuilder();

        IntStream.range(0, size).forEach(i -> {
            builder.append(" ?");
            if (i != size - 1)
                builder.append(",");
        });

        return builder.toString();
    }

    /**
     * This method adds a set of String values into preparedStatementList
     *
     * @param preparedStmtList
     * @param ids
     */
    public void addToPreparedStatement(List<Object> preparedStmtList, Set<String> ids) {
        ids.forEach(id -> {
            preparedStmtList.add(id);
        });
    }

    public void addToPreparedStatement(List<Object> preparedStmtList, List<String> ids) {
        ids.forEach(id -> {
            preparedStmtList.add(id);
        });
    }
    /**
     * This method appends order by clause to the query
     *
     * @param query
     * @param orderByClause
     * @return
     */
    public String addOrderByClause(String query, String orderByClause) {
        return query + orderByClause;
    }

    /**
     * This method prepares partial json string from the filter map to query on jsonb column
     *
     * - For nested keys (keys containing dot separators), a nested JSON structure is constructed.
     * - For other keys, a simple key-value JSON structure is returned.
     * @param filterMap
     * @return
     */
    public String preparePartialJsonStringFromFilterMap(Map<String, String> filterMap) {
        Map<String, Object> queryMap = new HashMap<>();

        filterMap.keySet().forEach(key -> {
            if (key.contains(DOT_SEPARATOR)) {
                String[] keyArray = key.split(DOT_REGEX);
                Map<String, Object> nestedQueryMap = new HashMap<>();
                prepareNestedQueryMap(0, keyArray, nestedQueryMap, filterMap.get(key));
                queryMap.put(keyArray[0], nestedQueryMap.get(keyArray[0]));
            } else {
                queryMap.put(key, filterMap.get(key));
            }
        });

        String partialJsonQueryString = gson.toJson(queryMap);

        return partialJsonQueryString;
    }

    /**
     * This is an overloaded function, to handle facility multi select for plan search specifically.
     * Prepares a partial JSON query string based on the provided filter map, key, and prepared statement list.
     *
     * @param filterMap         A map containing filter keys and their corresponding set of values.
     * @param preparedStmtList  A list to which placeholder values for prepared statements are added.
     * @param key               The key used to construct the partial query.
     * @return                  A partial JSON query string or a query fragment with OR conditions.
     *
     * This method dynamically constructs a JSON query or query fragment based on the structure of the `key`:
     * - For nested keys (keys containing dot separators), a nested JSON structure is constructed.
     * - For the "facilityId" key, an OR condition is generated for all values in the set.
     * - For other keys, a simple key-value JSON structure is returned.
     *
     * Notes:
     * - When constructing the query for "facilityId", placeholders for each value are added to the `preparedStmtList`.
     */
    public String preparePartialJsonStringFromFilterMap(Map<String, Set<String>> filterMap, List<Object> preparedStmtList, String key) {
        Map<String, Object> queryMap = new HashMap<>();
        StringBuilder finalJsonQuery = new StringBuilder();
        // Handle nested keys (dot separator)
        if (key.contains(DOT_SEPARATOR)) {
            String[] keyArray = key.split(DOT_REGEX);
            Map<String, Object> nestedQueryMap = new HashMap<>();
            prepareNestedQueryMap(0, keyArray, nestedQueryMap, (String) filterMap.get(key).toArray()[0]);
            queryMap.put(keyArray[0], nestedQueryMap.get(keyArray[0]));
            return gson.toJson(queryMap);
        } else if (FACILITY_ID_SEARCH_PARAMETER_KEY.equals(key)) {
            Set<String> values = filterMap.get(key);
            if (values != null && !values.isEmpty()) {
                StringBuilder orClauseBuilder = new StringBuilder();

                // For each value, add an OR condition with a placeholder for the value
                for (String value : values) {
                    if (orClauseBuilder.length() > 0) {
                        orClauseBuilder.append(OR_CONDITION);
                    }
                    // Add the condition for the key-value pair
                    orClauseBuilder.append(JSONB_QUERY_FORMAT);
                    // Add the actual value in the format { "facilityId": "value" }
                    preparedStmtList.add("{\"" + key + "\": \"" + value + "\"}");
                }

                // Append the OR clause as part of the AND conditions
                finalJsonQuery.append(AND_CONDITION).append("(").append(orClauseBuilder.toString()).append(") ");
                return finalJsonQuery.toString();  // Return the query with OR conditions for facilityId
            }
        } else {
            queryMap.put(key, (String) filterMap.get(key).toArray()[0]);
        }


        // Return the dynamically constructed query string
        return gson.toJson(queryMap);
    }


    /**
     * Tail recursive method to prepare n-level nested partial json for queries on nested data in
     * master data. For e.g. , if the key is in the format a.b.c, it will construct a nested json
     * object of the form - {"a":{"b":{"c": "value"}}}
     *
     * @param index
     * @param nestedKeyArray
     * @param currentQueryMap
     * @param value
     */
    private void prepareNestedQueryMap(int index, String[] nestedKeyArray, Map<String, Object> currentQueryMap, String value) {
        // Return when all levels have been reached.
        if (index == nestedKeyArray.length)
            return;

            // For the final level simply put the value in the map.
        else if (index == nestedKeyArray.length - 1) {
            currentQueryMap.put(nestedKeyArray[index], value);
            return;
        }

        // For non terminal levels, add a child map.
        currentQueryMap.put(nestedKeyArray[index], new HashMap<>());

        // Make a recursive call to enrich data in next level.
        prepareNestedQueryMap(index + 1, nestedKeyArray, (Map<String, Object>) currentQueryMap.get(nestedKeyArray[index]), value);
    }

    /**
     * This method adds pagination to the query
     *
     * @param query
     * @param preparedStmtList
     * @return
     */
    public String getPaginatedQuery(String query, List<Object> preparedStmtList) {
        StringBuilder paginatedQuery = new StringBuilder(query);

        // Append offset
        paginatedQuery.append(" OFFSET ? ");
        preparedStmtList.add(config.getDefaultOffset());

        // Append limit
        paginatedQuery.append(" LIMIT ? ");
        preparedStmtList.add(config.getDefaultLimit());

        return paginatedQuery.toString();
    }

    /**
     * This method is used to extract and parse JSON data into a JsonNode object
     *
     * @param pGobject postgreSQL specific object
     * @return returns a JsonNode
     */
    public JsonNode getAdditionalDetail(PGobject pGobject) {
        JsonNode additionalDetail = null;

        try {
            if (!ObjectUtils.isEmpty(pGobject)) {
                additionalDetail = objectMapper.readTree(pGobject.getValue());
            }
        } catch (IOException e) {
            throw new CustomException("PARSING_ERROR", "Failed to parse additionalDetails object");
        }
        return additionalDetail;
    }
}
