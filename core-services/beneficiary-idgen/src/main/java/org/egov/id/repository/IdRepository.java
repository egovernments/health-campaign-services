package org.egov.id.repository;

import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdStatus;
import org.egov.common.models.idgen.IdTransactionLog;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.ObjectUtils;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

/**
 * IdRepository handles interactions with the PostgreSQL database
 * for fetching, filtering, and updating ID records.
 *
 * Responsibilities:
 * - Fetch unassigned or dispatched IDs
 * - Filter by status, tenant, device/user
 * - Bulk update statuses
 */
@Repository
public class IdRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final IdRecordRowMapper idRecordRowMapper;
    private final IdTransactionLogRowMapper idTransactionLogRowMapper;

    // SQL template for updating ID status in bulk
    private static final String UPDATE_STATUS_DISPATCHED_QUERY =
            "UPDATE id_pool SET status = 'DISPATCHED' WHERE id = ?";

    /**
     * Constructs a new IdRepository instance with required dependencies for database operations.
     *
     * @param jdbcTemplate The Spring JdbcTemplate for executing SQL queries using standard parameters
     * @param namedParameterJdbcTemplate The Spring NamedParameterJdbcTemplate for executing SQL queries with named parameters
     * @param idRecordRowMapper Custom row mapper for converting database rows to IdRecord objects
     * @param idTransactionLogRowMapper Custom row mapper for converting database rows to IdTransactionLog objects
     */
    public IdRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate, IdRecordRowMapper idRecordRowMapper, IdTransactionLogRowMapper idTransactionLogRowMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.idRecordRowMapper = idRecordRowMapper;
        this.idTransactionLogRowMapper = idTransactionLogRowMapper;
    }

    /**
     * Fetches up to `count` unassigned IDs for a given tenant.
     * Orders them by creation time (oldest first).
     */
    public List<IdRecord> fetchUnassigned(String tenantId, int count) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("tenantId", tenantId);
        paramMap.put("status", IdStatus.UNASSIGNED.name());
        paramMap.put("limit", count);

        String query =
                "SELECT * FROM id_pool " +
                        "WHERE status = :status " +
                        "AND tenantId = :tenantId " +
                        "ORDER BY createdTime ASC " +
                        "LIMIT :limit";

        return namedParameterJdbcTemplate.query(query, paramMap, this.idRecordRowMapper);
    }

    /**
     * Fetches a list of dispatched IDs for a given tenant, user, and device.
     * Filters only for today’s records and orders by recent creation time.
     */
    public List<IdTransactionLog> selectClientDispatchedIds(
            String tenantId, String deviceUuid, String userUuid, IdStatus idStatus) {

        StringBuilder queryBuilder = new StringBuilder("SELECT * FROM id_transaction_log");
        Map<String, Object> paramMap = new HashMap<>();
        List<String> conditions = new ArrayList<>();

        // Add optional filters based on device, user, tenant
        if (!ObjectUtils.isEmpty(deviceUuid)) {
            conditions.add("device_uuid = :deviceUuid");
            paramMap.put("deviceUuid", deviceUuid);
        }

        if (!ObjectUtils.isEmpty(userUuid)) {
            conditions.add("user_uuid = :userUuid");
            paramMap.put("userUuid", userUuid);
        }

        if (!ObjectUtils.isEmpty(tenantId)) {
            conditions.add("tenantId = :tenantId");
            paramMap.put("tenantId", tenantId);
        }

        if (!ObjectUtils.isEmpty(idStatus)) {
            conditions.add("status = :status");
            paramMap.put("status", idStatus.name());
        }

        // Restrict query to today's date (ignores time)
        conditions.add("to_char(to_timestamp(createdTime / 1000), 'YYYY-MM-DD') = to_char(current_date, 'YYYY-MM-DD')");

        // Add WHERE clause only if filters exist
        if (!conditions.isEmpty()) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(String.join(" AND ", conditions));
        }

        // Always order by latest createdTime
        queryBuilder.append(" ORDER BY createdTime DESC");

        return namedParameterJdbcTemplate.query(queryBuilder.toString(), paramMap, this.idTransactionLogRowMapper);
    }

    /**
     * Fetches ID records for a specific set of IDs filtered by status and tenant.
     */
    public List<IdRecord> findByIDsAndStatus(List<String> ids, IdStatus idStatus, String tenantId) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("tenantId", tenantId);
        paramMap.put("ids", ids);

        String query = "SELECT * FROM id_pool WHERE tenantId = :tenantId ";

        // Optionally filter by status if provided
        if (!ObjectUtils.isEmpty(idStatus)) {
            query += "AND status = :status ";
            paramMap.put("status", idStatus.name());
        }

        // Match only IDs in the provided list
        query += "AND id IN (:ids) ORDER BY createdTime ASC";

        return namedParameterJdbcTemplate.query(query, paramMap, this.idRecordRowMapper);
    }

    /**
     * Bulk updates the status of given ID records to 'DISPATCHED' using a single SQL query.
     * Skips execution if the input list is null or empty.
     */
    public void updateStatusToDispatched(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;

        // Build placeholders for SQL IN clause (?, ?, ?, ...)
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));

        // Execute bulk update
        jdbcTemplate.batchUpdate(UPDATE_STATUS_DISPATCHED_QUERY, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(@NotNull PreparedStatement ps, int i) throws SQLException {
                ps.setString(1, ids.get(i));
            }

            @Override
            public int getBatchSize() {
                return ids.size();
            }
        });
    }
}
