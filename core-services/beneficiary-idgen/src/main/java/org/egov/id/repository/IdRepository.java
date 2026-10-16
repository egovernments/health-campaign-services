package org.egov.id.repository;

import org.egov.common.ds.Tuple;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdStatus;
import org.egov.common.models.idgen.IdTransactionLog;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.id.config.PropertiesManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.ObjectUtils;

import java.util.*;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

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
    private final PropertiesManager propertiesManager;
    private final MultiStateInstanceUtil multiStateInstanceUtil;

    /**
     * Constructs a new IdRepository instance with required dependencies for database operations.
     *
     * @param jdbcTemplate The Spring JdbcTemplate for executing SQL queries using standard parameters
     * @param namedParameterJdbcTemplate The Spring NamedParameterJdbcTemplate for executing SQL queries with named parameters
     * @param idRecordRowMapper Custom row mapper for converting database rows to IdRecord objects
     * @param idTransactionLogRowMapper Custom row mapper for converting database rows to IdTransactionLog objects
     */
    public IdRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate, IdRecordRowMapper idRecordRowMapper, IdTransactionLogRowMapper idTransactionLogRowMapper, PropertiesManager propertiesManager, MultiStateInstanceUtil multiStateInstanceUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.idRecordRowMapper = idRecordRowMapper;
        this.idTransactionLogRowMapper = idTransactionLogRowMapper;
        this.propertiesManager = propertiesManager;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
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
    public List<IdRecord> fetchUnassigned(String tenantId, String userUuid, int count) throws InvalidTenantIdException {
        /**
         * This SQL query performs an atomic update operation to fetch and mark unassigned IDs as dispatched:
         * 1. Uses FOR UPDATE SKIP LOCKED to prevent concurrent access to the same rows
         * 2. Inner SELECT finds the oldest unassigned IDs for the tenant
         * 3. UPDATE marks selected IDs as dispatched and updates audit fields
         * 4. RETURNING clause fetches the complete updated records
         * This approach ensures thread-safe ID allocation without deadlocks
         */
        /* ANY(ARRAY(...)) forces the locking subquery into an InitPlan evaluated exactly once;
           a plain IN-subselect with FOR UPDATE SKIP LOCKED may be re-executed as rows lock,
           cascading past the LIMIT, while staying as fast as the original plan */
        String query = String.format(
                "UPDATE %s.id_pool p SET status = :updatedStatus, rowVersion = rowVersion + 1, " +
                        "lastModifiedBy = :lastModifiedBy, lastModifiedTime = :lastModifiedTime " +
                        "WHERE p.id = ANY(ARRAY(SELECT id FROM %s.id_pool WHERE tenantId = :tenantId AND status = :status " +
                        "ORDER BY id ASC LIMIT :limit FOR UPDATE SKIP LOCKED)) AND p.status = :status RETURNING p.*",
                SCHEMA_REPLACE_STRING, SCHEMA_REPLACE_STRING);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("status", IdStatus.UNASSIGNED.name())
                .addValue("updatedStatus", IdStatus.DISPATCHED.name())
                .addValue("lastModifiedBy", userUuid)
                .addValue("lastModifiedTime", System.currentTimeMillis())
                .addValue("limit", count);

        return namedParameterJdbcTemplate.query(query, params, this.idRecordRowMapper);
    }

    public Tuple<String, Map<String, Object>> getIDsUserDeviceQuery(String tenantId, String deviceUuid, String userUuid, IdStatus idStatus, boolean restrictToday, Integer limit, Integer offset, boolean isCountQuery) {
        StringBuilder queryBuilder = new StringBuilder(isCountQuery ? "SELECT count(*) " : "SELECT * ");
        queryBuilder.append(String.format("FROM %s.id_transaction_log", SCHEMA_REPLACE_STRING));
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
            String userTimeZone = propertiesManager.getUserTimeZone();  // e.g., "Asia/Kolkata"
            String appTimeZone = propertiesManager.getAppTimeZone();
            // Restrict query to today's date using timestamp range
            conditions.add("createdTime >= (EXTRACT(EPOCH FROM timezone('" + appTimeZone + "', date_trunc('day', CURRENT_TIMESTAMP AT TIME ZONE '" + userTimeZone + "'))) * 1000)::bigint");
            conditions.add("createdTime < (EXTRACT(EPOCH FROM timezone('" + appTimeZone + "', date_trunc('day', (CURRENT_TIMESTAMP AT TIME ZONE '" + userTimeZone + "') + interval '1 day'))) * 1000)::bigint");
        }

        // Add WHERE clause only if filters exist
        if (!conditions.isEmpty()) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(String.join(" AND ", conditions));
        }

        // Always order by latest createdTime
        if(!isCountQuery) {
            queryBuilder.append(" ORDER BY createdTime DESC LIMIT :limit OFFSET :offset");
            paramMap.put("limit", limit);
            paramMap.put("offset", offset);
        }

        return new Tuple<>(queryBuilder.toString(), paramMap);
    }

    /**
     * Fetches a list of dispatched IDs for a given tenant, user, and device.
     * Filters only for today’s records and orders by recent creation time.
     */
    public Tuple<List<IdTransactionLog>, Long> selectIDsForUserDevice(
            String tenantId, String deviceUuid, String userUuid, IdStatus idStatus, Integer limit, Integer offset, boolean restrictToday) throws InvalidTenantIdException {

        Tuple<String, Map<String, Object>> queryAndParams = getIDsUserDeviceQuery(tenantId, deviceUuid, userUuid, idStatus, restrictToday, limit, offset, false);
        String query = multiStateInstanceUtil.replaceSchemaPlaceholder(queryAndParams.getX(), tenantId);
        List<IdTransactionLog> idTransactionLogs = namedParameterJdbcTemplate.query(query, queryAndParams.getY(), this.idTransactionLogRowMapper);

        long totalCount = selectIDsForUserDeviceCount(tenantId, deviceUuid, userUuid, idStatus, limit, offset, restrictToday);

        return new Tuple<>(idTransactionLogs, totalCount);
    }

    public long selectIDsForUserDeviceCount(
            String tenantId, String deviceUuid, String userUuid, IdStatus idStatus, Integer limit, Integer offset, boolean restrictToday) throws InvalidTenantIdException {

        Tuple<String, Map<String, Object>> queryAndParams = getIDsUserDeviceQuery(tenantId, deviceUuid, userUuid, idStatus, restrictToday, limit, offset, true);
        String query = multiStateInstanceUtil.replaceSchemaPlaceholder(queryAndParams.getX(), tenantId);

        return namedParameterJdbcTemplate.queryForObject(query, queryAndParams.getY(), Long.class);
    }

    /**
     * Fetches ID records for a specific set of IDs filtered by status and tenant.
     */
    public List<IdRecord> findByIDsAndStatus(List<String> ids, IdStatus idStatus, String tenantId) throws InvalidTenantIdException {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("tenantId", tenantId);
        paramMap.put("ids", ids);

        String query = String.format("SELECT * FROM %s.id_pool WHERE tenantId = :tenantId ", SCHEMA_REPLACE_STRING);

        // Optionally filter by status if provided
        if (!ObjectUtils.isEmpty(idStatus)) {
            query += "AND status = :status ";
            paramMap.put("status", idStatus.name());
        }

        // Match only IDs in the provided list
        query += "AND id IN (:ids) ORDER BY createdTime ASC";
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);

        return namedParameterJdbcTemplate.query(query, paramMap, this.idRecordRowMapper);
    }

}
