package org.egov.repository.querybuilder;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.config.AttendanceServiceConfiguration;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.AttendanceRegisterSearchCriteria;
import org.egov.web.models.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Component
@Slf4j
public class RegisterQueryBuilder {

    @Autowired
    private AttendanceServiceConfiguration config;

    private static final String ATTENDANCE_REGISTER_SELECT_QUERY = " SELECT reg.id, " +
            "reg.tenantid, " +
            "reg.registernumber, " +
            "reg.name , " +
            "reg.startdate, " +
            "reg.enddate, " +
            "reg.status, " +
            "reg.additionaldetails, " +
            "reg.createdby, " +
            "reg.lastmodifiedby, " +
            "reg.createdtime, " +
            "reg.lastmodifiedtime, " +
            "reg.referenceid, " +
            "reg.servicecode " +
            "FROM eg_wms_attendance_register reg ";


    private final String paginationWrapper = "SELECT * FROM " +
            "(SELECT *, DENSE_RANK() OVER (ORDER BY lastmodifiedtime DESC , id) offset_ FROM " +
            "({})" +
            " result) result_offset " +
            "WHERE offset_ > ? AND offset_ <= ?";


    public String getAttendanceRegisterSearchQuery(AttendanceRegisterSearchCriteria searchCriteria, List<Object> preparedStmtList) {

        log.info("Search criteria of attendance search : " + searchCriteria.toString());
        StringBuilder query = new StringBuilder(ATTENDANCE_REGISTER_SELECT_QUERY);

        if (!ObjectUtils.isEmpty(searchCriteria.getTenantId())) {
            addClauseIfRequired(query, preparedStmtList);
            query.append(" reg.tenantid like ? ");
            preparedStmtList.add(searchCriteria.getTenantId()+"%");
        }

        List<String> registerIds = searchCriteria.getIds();
        if (registerIds != null && !registerIds.isEmpty()) {
            addClauseIfRequired(query, preparedStmtList);
            query.append(" reg.id IN (").append(createQuery(registerIds)).append(")");
            preparedStmtList.addAll(registerIds);
        }

        if (!ObjectUtils.isEmpty(searchCriteria.getRegisterNumber())) {
            String registerNumber = searchCriteria.getRegisterNumber();
            addClauseIfRequired(query, preparedStmtList);
            query.append(" reg.registernumber = ? ");
            preparedStmtList.add(registerNumber);
        }

        if (!ObjectUtils.isEmpty(searchCriteria.getReferenceId())) {
            String referenceId = searchCriteria.getReferenceId();
            addClauseIfRequired(query, preparedStmtList);
            query.append(" reg.referenceid = ? ");
            preparedStmtList.add(referenceId);
        }

        if (!ObjectUtils.isEmpty(searchCriteria.getServiceCode())) {
            String serviceCode = searchCriteria.getServiceCode();
            addClauseIfRequired(query, preparedStmtList);
            query.append(" reg.servicecode = ? ");
            preparedStmtList.add(serviceCode);
        }

        if (!ObjectUtils.isEmpty(searchCriteria.getName())) {
            String name = searchCriteria.getName();
            addClauseIfRequired(query, preparedStmtList);
            query.append(" reg.name = ? ");
            preparedStmtList.add(name);
        }

        if (searchCriteria.getFromDate() != null) {
            addClauseIfRequired(query, preparedStmtList);

            //If user does not specify toDate, take today's date as toDate by default.
            if (searchCriteria.getToDate() == null) {
                searchCriteria.setToDate(BigDecimal.valueOf(Instant.now().toEpochMilli()));
            }

            query.append(" reg.startdate BETWEEN ? AND ?");
            preparedStmtList.add(searchCriteria.getFromDate());
            preparedStmtList.add(searchCriteria.getToDate());

        } else {
            //if only toDate is provided as parameter without fromDate parameter, throw an exception.
            if (searchCriteria.getToDate() != null) {
                throw new CustomException("INVALID_SEARCH_PARAM", "Cannot specify toDate without a fromDate");
            }
        }

        if (!ObjectUtils.isEmpty(searchCriteria.getStatus())) {
            Status status = searchCriteria.getStatus();
            addClauseIfRequired(query, preparedStmtList);
            query.append(" reg.status = ? ");
            preparedStmtList.add(status.toString());
        }

        addOrderByClause(query, searchCriteria);
        //addLimitAndOffset(query, searchCriteria, preparedStmtList);
        return addPaginationWrapper(query.toString(), preparedStmtList, searchCriteria);
    }

    private String addPaginationWrapper(String query,List<Object> preparedStmtList,
                                        AttendanceRegisterSearchCriteria criteria){
        int limit = config.getAttendanceRegisterDefaultLimit();
        int offset = config.getAttendanceRegisterDefaultOffset();

        String finalQuery = paginationWrapper.replace("{}",query);

        if(criteria.getLimit()!=null && criteria.getLimit()<=config.getAttendanceRegisterMaxLimit())
            limit = criteria.getLimit();

        if(criteria.getLimit()!=null && criteria.getLimit()>config.getAttendanceRegisterMaxLimit())
            limit = config.getAttendanceRegisterMaxLimit();

        if(criteria.getOffset()!=null)
            offset = criteria.getOffset();

        preparedStmtList.add(offset);
        preparedStmtList.add(limit+offset);

        return finalQuery;
    }

    private void addOrderByClause(StringBuilder queryBuilder, AttendanceRegisterSearchCriteria criteria) {
        //default
        if (criteria.getSortBy() == null || StringUtils.isEmpty(criteria.getSortBy().name())) {
            queryBuilder.append(" ORDER BY reg.lastmodifiedtime ");
        } else {
            switch (AttendanceRegisterSearchCriteria.SortBy.valueOf(criteria.getSortBy().name())) {
                case fromDate:
                    queryBuilder.append(" ORDER BY reg.startdate ");
                    break;
                case toDate:
                    queryBuilder.append(" ORDER BY reg.enddate ");
                    break;
                default:
                    queryBuilder.append(" ORDER BY reg.lastmodifiedtime ");
                    break;
            }
        }

        if (criteria.getSortOrder() == AttendanceRegisterSearchCriteria.SortOrder.ASC)
            queryBuilder.append(" ASC ");
        else queryBuilder.append(" DESC ");
    }

    private void addLimitAndOffset(StringBuilder queryBuilder, AttendanceRegisterSearchCriteria criteria, List<Object> preparedStmtList) {
        queryBuilder.append(" OFFSET ? ");
        preparedStmtList.add(criteria.getOffset());

        queryBuilder.append(" LIMIT ? ");
        preparedStmtList.add(criteria.getLimit());

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

    private void addClauseIfRequired(StringBuilder query, List<Object> preparedStmtList) {
        if (preparedStmtList.isEmpty()) {
            query.append(" WHERE ");
        } else {
            query.append(" AND ");
        }
    }
}
