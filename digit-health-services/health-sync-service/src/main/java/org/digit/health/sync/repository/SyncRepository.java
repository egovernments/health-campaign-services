package org.digit.health.sync.repository;

import org.digit.health.sync.web.models.dao.SyncData;
import org.digit.health.sync.web.models.request.SyncSearchDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SyncRepository  {

    private final JdbcTemplate jdbcTemplate;

    private final SyncQueryBuilder syncQueryBuilder;

    @Autowired
    public SyncRepository(JdbcTemplate jdbcTemplate, SyncQueryBuilder syncQueryBuilder) {
        this.jdbcTemplate = jdbcTemplate;
        this.syncQueryBuilder = syncQueryBuilder;
    }

    public List<SyncData> findByCriteria(SyncSearchDto syncSearchDto) {
        return jdbcTemplate.query(syncQueryBuilder.getSQlBasedOn(syncSearchDto), new BeanPropertyRowMapper<>(SyncData.class));
    }

}
