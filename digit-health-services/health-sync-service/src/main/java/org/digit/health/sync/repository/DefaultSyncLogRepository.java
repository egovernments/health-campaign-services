package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.dao.SyncLogData;
import org.digit.health.sync.web.models.request.SyncLogSearchDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("defaultSyncLogRepository")
public class DefaultSyncLogRepository implements SyncLogRepository{

    private final JdbcTemplate jdbcTemplate;

    private final SyncLogQueryBuilder syncLogQueryBuilder;

    @Autowired
    public DefaultSyncLogRepository(JdbcTemplate jdbcTemplate, @Qualifier("defaultSyncLogQueryBuilder") SyncLogQueryBuilder defaultSyncLogQueryBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.syncLogQueryBuilder = defaultSyncLogQueryBuilder;
    }

    @Override
    public List<SyncLogData> findByCriteria(SyncLogSearchDto syncLogSearchDto) {
        return jdbcTemplate.query(syncLogQueryBuilder.getSQlBasedOn(syncLogSearchDto), new BeanPropertyRowMapper<>(SyncLogData.class));
    }

}
