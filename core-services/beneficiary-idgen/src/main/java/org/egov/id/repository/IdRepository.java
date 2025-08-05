package org.egov.id.repository;

import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdStatus;
import org.egov.common.models.idgen.IdTransactionLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.ObjectUtils;

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
     * Fetches unassigned IDs from the ID pool table for a specific tenant and marks them as dispatched.
     * The method updates the statuses of retrieved IDs to "DISPATCHED" and logs the modification details.
     *
     * @param tenantId The identifier of the tenant requesting the unassigned IDs.
     * @param userUuid The unique identifier of the user requesting the operation.
     * @param count The number of unassigned IDs to fetch.
     * @return A list of {@link IdRecord} objects representing the fetched and updated IDs.
     */
    public List<IdRecord> fetchUnassigned(String tenantId, String userUuid, int count) {
        /**
         * This SQL query performs an atomic update operation to fetch and mark unassigned IDs as dispatched:
         * 1. Uses FOR UPDATE SKIP LOCKED to prevent concurrent access to the same rows
         * 2. Inner SELECT finds the oldest unassigned IDs for the tenant
         * 3. UPDATE marks selected IDs as dispatched and updates audit fields
         * 4. RETURNING clause fetches the complete updated records
         * This approach ensures thread-safe ID allocation without deadlocks
         */
        String query =
                "UPDATE id_pool p SET status = :updatedStatus, rowVersion = rowVersion + 1, " +
                        "lastModifiedBy = :lastModifiedBy, lastModifiedTime = :lastModifiedTime " +
                        "WHERE p.id IN (SELECT id FROM id_pool WHERE status = :status AND tenantId = :tenantId " +
                        "ORDER BY id ASC LIMIT :limit FOR UPDATE SKIP LOCKED) AND p.status = :status RETURNING p.*";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("status", IdStatus.UNASSIGNED.name())
                .addValue("updatedStatus", IdStatus.DISPATCHED.name())
                .addValue("lastModifiedBy", userUuid)
                .addValue("lastModifiedTime", System.currentTimeMillis())
                .addValue("limit", count);

        return namedParameterJdbcTemplate.query(query, params, this.idRecordRowMapper);
    }

    /**
     * Fetches a list of dispatched IDs for a given tenant, user, and device.
     * Filters only for today’s records and orders by recent creation time.
     */
    public List<IdTransactionLog> selectIDsForUserDevice(
            String tenantId, String deviceUuid, String userUuid, IdStatus idStatus, boolean restrictToday) {

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

        if (restrictToday) {
            // Restrict query to today's date (ignores time)
            conditions.add("to_char(to_timestamp(createdTime / 1000), 'YYYY-MM-DD') = to_char(current_date, 'YYYY-MM-DD')");
        }

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

}
