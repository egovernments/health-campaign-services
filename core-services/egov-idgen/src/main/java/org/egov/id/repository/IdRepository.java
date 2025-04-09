package org.egov.id.repository;

import org.egov.common.models.idgen.IdRecord;
import org.egov.common.models.idgen.IdStatus;
import org.egov.common.models.idgen.IdTransactionLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.ObjectUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class IdRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private IdRecordRowMapper idRecordRowMapper;

    @Autowired
    private IdTransactionLogRowMapper idTransactionLogRowMapper;

    private static final String BULK_UPDATE_STATUS_SQL_BASE =
            "UPDATE id_pool SET status = 'DISPATCHED' WHERE id IN (%s)";

    /**
     * Fetches a list of unassigned ID records with a given limit.
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
     * Fetches a list of dispatched ID records with a given limit.
     */
    public List<IdTransactionLog> selectClientDispatchedIds (String tenantId, String deviceUuid , String userUuid , IdStatus idStatus) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("tenantId", tenantId);
        paramMap.put("userUuid", userUuid);
        paramMap.put("deviceUuid", deviceUuid);

        String query = "Select * From id_transaction_log " +
                "WHERE device_uuid = :deviceUuid " +
                "AND user_uuid = :userUuid " +
                "AND tenantId = :tenantId ";

        if (!ObjectUtils.isEmpty(idStatus)) {
            query+=" AND status = :status ";
            paramMap.put("status", idStatus.name());
        }

//        else {
//            query+=" AND status IS NOT NULL ";
//        }
        query += "AND to_char(to_timestamp(createdTime / 1000), 'YYYY-MM-DD') = to_char(current_date, 'YYYY-MM-DD') ORDER BY createdTime DESC";
        return namedParameterJdbcTemplate.query(query, paramMap, this.idTransactionLogRowMapper);
    }

    /**
     * Fetches a list of unassigned ID records with a given limit.
     */
    public List<IdRecord> findByIDsAndStatus(List<String> ids, IdStatus idStatus, String tenantId) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("tenantId", tenantId);
        paramMap.put("ids", ids);
        String query = "SELECT * FROM id_pool " +
                        "WHERE tenantId = :tenantId ";
        if (!ObjectUtils.isEmpty(idStatus)) {
            query += "AND status = :status ";
            paramMap.put("status", idStatus.name());
        }
        query += "AND id in (:ids) " +
                        "ORDER BY createdTime ASC";
        return namedParameterJdbcTemplate.query(query, paramMap, this.idRecordRowMapper);
    }

    /**
     * Updates the status of given ID records to 'DISPATCHED'.
     */
    public void updateStatusToDispatched(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;

        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = String.format(BULK_UPDATE_STATUS_SQL_BASE, placeholders);

        jdbcTemplate.update(sql, ids.toArray());
    }
}
