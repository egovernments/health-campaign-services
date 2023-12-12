package org.egov.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.repository.querybuilder.AttendanceLogQueryBuilder;
import org.egov.repository.rowmapper.AttendanceLogRowMapper;
import org.egov.web.models.AttendanceLog;
import org.egov.web.models.AttendanceLogSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class AttendanceLogRepository {
    @Autowired
    private AttendanceLogRowMapper rowMapper;

    @Autowired
    private AttendanceLogQueryBuilder queryBuilder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<AttendanceLog> getAttendanceLogs(AttendanceLogSearchCriteria searchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        log.info("Fetching Attendance Log list. RegisterId ["+searchCriteria.getRegisterId()+"]");
        String query = queryBuilder.getAttendanceLogSearchQuery(searchCriteria, preparedStmtList);
        log.info("Query build successfully. RegisterId ["+searchCriteria.getRegisterId()+"]");
        List<AttendanceLog> attendanceLogList = jdbcTemplate.query(query, rowMapper, preparedStmtList.toArray());
        log.info("Fetched Attendance Log list. RegisterId ["+searchCriteria.getRegisterId()+"]");
        return attendanceLogList;
    }
}
