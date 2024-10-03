package digit.util;

import digit.config.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Component
public class QueryUtil {

    private Configuration config;

    private QueryUtil(Configuration config) {
        this.config = config;
    }

    /**
     * This method adds "WHERE" clause and "AND" condition depending on preparedStatementList i.e.,
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
     * This method appends pagination i.e. limit and offset to the query
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

}
