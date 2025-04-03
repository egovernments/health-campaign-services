package org.egov.id.repository;

import org.egov.id.model.IdRecord;
import org.egov.id.model.IdStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class IdRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String FETCH_UNASSIGNED_SQL =
            "SELECT id, status, created_at FROM id_pool " +
                    "WHERE status = ? " +
                    "ORDER BY created_at ASC " +
                    "LIMIT ?";

    private static final String BULK_UPDATE_STATUS_SQL_BASE =
            "UPDATE id_pool SET status = 'DISPATCHED' WHERE id IN (%s)";

    /**
     * Fetches a list of unassigned ID records with a given limit.
     */
    public List<IdRecord> fetchUnassigned(int count) {
        List<Object> preparedStmtList = new ArrayList<>();
        preparedStmtList.add(IdStatus.UN_ASSIGNED);
        preparedStmtList.add(count);

        return jdbcTemplate.query(FETCH_UNASSIGNED_SQL, preparedStmtList.toArray(), new IdRecordRowMapper());
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
