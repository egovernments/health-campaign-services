package digit.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.config.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static digit.config.ServiceConstants.*;

@Slf4j
@Component
public class QueryUtil {

    private Configuration config;

    private ObjectMapper objectMapper;

    private QueryUtil(Configuration config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * This method adds "WHERE" clause and "AND" condition depending on preparedStatementList i.e.,
     * if preparedStatementList is empty, it will understand that it is the first clause being added so it
     * will add "WHERE" to the query and otherwise it will
     *
     * @param query            query to which Clause is to be added.
     * @param preparedStmtList prepared statement list to check which clause to add.
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
     * @param size number of placeholders to be added.
     * @return a string with provided number of placeholders.
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
     * This method adds a set of String values into preparedStatementList.
     *
     * @param preparedStmtList prepared statement list to add Ids.
     * @param ids              set of Ids to be added.
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
     * This method appends order by clause to the query.
     *
     * @param query         the query to which orderBy clause is to be added.
     * @param orderByClause the orderBy clause to be added.
     * @return a query with orderBy clause.
     */
    public String addOrderByClause(String query, String orderByClause) {
        return query + orderByClause;
    }

    /**
     * This method appends pagination i.e. limit and offset to the query.
     *
     * @param query            the query to which pagination is to be added.
     * @param preparedStmtList prepared statement list to add limit and offset.
     * @return a query with pagination
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
     * This method is used to extract and parse JSON data into a JsonNode object.
     *
     * @param pgObject postgreSQL specific object.
     * @return returns a JsonNode.
     */
    public JsonNode parseJson(PGobject pgObject) {

        try {
            if (!ObjectUtils.isEmpty(pgObject)) {
                return objectMapper.readTree(pgObject.getValue());
            }
        } catch (IOException e) {
            log.error("Error parsing PGobject value: " + pgObject, e);
            throw new CustomException(PARSING_ERROR_CODE, PARSING_ERROR_MESSAGE);
        }

        // Return empty JsonNode if pgObject is empty
        return objectMapper.createObjectNode();
    }


}
