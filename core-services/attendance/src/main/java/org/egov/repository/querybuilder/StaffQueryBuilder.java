package org.egov.repository.querybuilder;

import org.egov.web.models.StaffSearchCriteria;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Component
public class StaffQueryBuilder {

    private static final String ATTENDANCE_STAFF_SELECT_QUERY = " SELECT stf.id, " +
            "stf.individual_id, " +
            "stf.register_id, " +
            "stf.tenantid, " +
            "stf.enrollment_date , " +
            "stf.deenrollment_date, " +
            "stf.additionaldetails, " +
            "stf.createdby, " +
            "stf.lastmodifiedby, " +
            "stf.createdtime, " +
            "stf.lastmodifiedtime, " +
            "stf.tenantid " +
            "FROM eg_wms_attendance_staff stf ";

    public String getActiveAttendanceStaffSearchQuery(StaffSearchCriteria criteria, List<Object> preparedStmtList) {
        StringBuilder query = new StringBuilder(getAttendanceStaffSearchQuery(criteria, preparedStmtList));
        addClauseIfRequired(query, preparedStmtList);
        query.append(" stf.deenrollment_date is null ");

        return query.toString();
    }

    public String getAttendanceStaffSearchQuery(StaffSearchCriteria criteria, List<Object> preparedStmtList) {
        StringBuilder query = new StringBuilder(ATTENDANCE_STAFF_SELECT_QUERY);

        List<String> staffUserIds = criteria.getIndividualIds();
        if (staffUserIds != null && !staffUserIds.isEmpty()) {
            addClauseIfRequired(query, preparedStmtList);
            query.append(" stf.individual_id IN (").append(createQuery(staffUserIds)).append(")");
            preparedStmtList.addAll(criteria.getIndividualIds());
        }

        List<String> registerIds = criteria.getRegisterIds();
        if (registerIds != null && !registerIds.isEmpty()) {
            addClauseIfRequired(query, preparedStmtList);
            query.append(" stf.register_id IN (").append(createQuery(registerIds)).append(")");
            preparedStmtList.addAll(criteria.getRegisterIds());
        }

        String tenantId = criteria.getTenantId();
        if (tenantId != null && !tenantId.isEmpty()) {
            addClauseIfRequired(query, preparedStmtList);
            query.append(" stf.tenantid IN (").append(createQuery(Collections.singletonList(tenantId))).append(")");
            preparedStmtList.add(criteria.getTenantId());
        }
        return query.toString();
    }
    private void addClauseIfRequired(StringBuilder query, List<Object> preparedStmtList) {
        if (preparedStmtList.isEmpty()) {
            query.append(" WHERE ");
        } else {
            query.append(" AND ");
        }
    }

    private String createQuery(Collection<String> ids) {
        StringBuilder builder = new StringBuilder();
        int length = ids.size();
        for (int i = 0; i < length; i++) {
            builder.append(" ? ");
            if (i != length - 1) builder.append(",");
        }
        return builder.toString();
    }


    private void addLimitAndOffset(StringBuilder query, StaffSearchCriteria criteria, List<Object> preparedStmtList) {
        query.append(" OFFSET ? ");
        preparedStmtList.add(criteria.getOffset());

        query.append(" LIMIT ? ");
        preparedStmtList.add(criteria.getLimit());

    }
}
