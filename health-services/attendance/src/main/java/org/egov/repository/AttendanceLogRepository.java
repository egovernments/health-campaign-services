package org.egov.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.producer.Producer;
import org.egov.repository.querybuilder.AttendanceLogQueryBuilder;
import org.egov.repository.rowmapper.AttendanceLogRowMapper;
import org.egov.web.models.AttendanceLog;
import org.egov.web.models.AttendanceLogSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@Slf4j
public class AttendanceLogRepository extends GenericRepository<AttendanceLog> {
    private final AttendanceLogRowMapper rowMapper;
    private final AttendanceLogQueryBuilder queryBuilder;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    protected AttendanceLogRepository(
            Producer producer,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            RedisTemplate<String, Object> redisTemplate,
            SelectQueryBuilder selectQueryBuilder,
            AttendanceLogRowMapper rowMapper,
            JdbcTemplate jdbcTemplate) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, null, Optional.of("abc"));
        this.rowMapper = rowMapper;
        this.queryBuilder = new AttendanceLogQueryBuilder();
        this.jdbcTemplate = jdbcTemplate;
    }


    public List<AttendanceLog> getAttendanceLogs(AttendanceLogSearchCriteria searchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        log.info("Fetching Attendance Log list. RegisterId ["+searchCriteria.getRegisterId()+"]");
        String query = queryBuilder.getAttendanceLogSearchQuery(searchCriteria, preparedStmtList);
        log.info("Query : " + query);
        log.info("Query build successfully. RegisterId ["+searchCriteria.getRegisterId()+"]");
        List<AttendanceLog> attendanceLogList = jdbcTemplate.query(query, rowMapper, preparedStmtList.toArray());
        log.info("Fetched Attendance Log list. RegisterId ["+searchCriteria.getRegisterId()+"]");
        return attendanceLogList;
    }
}
