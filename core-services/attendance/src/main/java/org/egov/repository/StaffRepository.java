package org.egov.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.repository.querybuilder.StaffQueryBuilder;
import org.egov.repository.rowmapper.StaffRowMapper;
import org.egov.web.models.StaffPermission;
import org.egov.web.models.StaffSearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@Slf4j
public class StaffRepository {
    @Autowired
    private StaffRowMapper rowMapper;

    @Autowired
    private StaffQueryBuilder queryBuilder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<StaffPermission> getActiveStaff(StaffSearchCriteria searchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getActiveAttendanceStaffSearchQuery(searchCriteria, preparedStmtList);
        List<StaffPermission> attendanceStaffList = jdbcTemplate.query(query, rowMapper, preparedStmtList.toArray());
        return attendanceStaffList;
    }

    public List<StaffPermission> getAllStaff(StaffSearchCriteria searchCriteria) {
        List<Object> preparedStmtList = new ArrayList<>();
        String query = queryBuilder.getAttendanceStaffSearchQuery(searchCriteria, preparedStmtList);
        List<StaffPermission> attendanceStaffList = jdbcTemplate.query(query, rowMapper, preparedStmtList.toArray());
        return attendanceStaffList;
    }
}
