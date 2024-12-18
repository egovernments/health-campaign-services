package org.egov.repository.rowmapper;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.egov.common.contract.models.AuditDetails;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.AttendanceEntry;
import org.egov.web.models.IndividualEntry;
import org.egov.web.models.MusterRoll;
import org.egov.works.services.common.models.musterroll.Status;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class MusterRollRowMapper implements ResultSetExtractor<List<MusterRoll>> {

    private final ObjectMapper mapper;

    @Autowired
    public MusterRollRowMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<MusterRoll> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String, MusterRoll> musterRollMap = new LinkedHashMap<>();
        Map<String, IndividualEntry> individualMap = new LinkedHashMap<>();
        while (rs.next()) {
            String id = rs.getString("id");
            String tenantId = rs.getString("tenant_id");
            String musterRollNumber = rs.getString("musterroll_number");
            String registerId = rs.getString("attendance_register_id");
            String status = rs.getString("status");
            String musterRollStatus = rs.getString("musterroll_status");
            BigDecimal startDate = rs.getBigDecimal("start_date");
            BigDecimal endDate = rs.getBigDecimal("end_date");
            String referenceId = rs.getString("reference_id");
            String serviceCode = rs.getString("service_code");

            String createdby = rs.getString("createdby");
            String lastmodifiedby = rs.getString("lastmodifiedby");
            Long createdtime = rs.getLong("createdtime");
            Long lastmodifiedtime = rs.getLong("lastmodifiedtime");

            AuditDetails auditDetails = AuditDetails.builder().createdBy(createdby).createdTime(createdtime)
                    .lastModifiedBy(lastmodifiedby).lastModifiedTime(lastmodifiedtime)
                    .build();

            JsonNode additionalDetails = getAdditionalDetail("additionaldetails", rs);

            MusterRoll musterRoll = MusterRoll.builder().id(id).tenantId(tenantId).musterRollNumber(musterRollNumber)
                    .registerId(registerId).status(Status.fromValue(status)).musterRollStatus(musterRollStatus).startDate(startDate)
                    .endDate(endDate).referenceId(referenceId).serviceCode(serviceCode)
                    .additionalDetails(additionalDetails).auditDetails(auditDetails).build();

            if (!musterRollMap.containsKey(id)) {
                musterRollMap.put(id, musterRoll);
            }
            addIndividualChildrenToProperty(rs, musterRollMap.get(id),individualMap);
        }
        return new ArrayList<>(musterRollMap.values());
    }

    private void addIndividualChildrenToProperty(ResultSet rs, MusterRoll musterRoll,Map<String, IndividualEntry> individualMap)
            throws SQLException {
        addIndividualEntry(rs, musterRoll,individualMap);
    }

    private void addIndividualEntry(ResultSet rs, MusterRoll musterRoll,Map<String, IndividualEntry> individualMap) throws SQLException {
        boolean isExist = true;
        String id = rs.getString("summaryId");
        String musterId = rs.getString("indMusterId");
        String individualId = rs.getString("IndividualId");
        BigDecimal actualTotalAttendance = rs.getBigDecimal("actualTotalAttendance");
        BigDecimal modifiedtotalAttendance = rs.getBigDecimal("modifiedTotalAttendance");

        String createdby = rs.getString("indCreatedBy");
        String lastmodifiedby = rs.getString("indModifiedBy");
        Long createdtime = rs.getLong("indCreatedTime");
        Long lastmodifiedtime = rs.getLong("indModifiedTime");

        if (StringUtils.isNotBlank(musterId) && musterId.equalsIgnoreCase(musterRoll.getId())) {

            JsonNode additionalDetails = getAdditionalDetail("indAddlDetails", rs);
            AuditDetails auditDetails = AuditDetails.builder().createdBy(createdby).createdTime(createdtime)
                    .lastModifiedBy(lastmodifiedby).lastModifiedTime(lastmodifiedtime)
                    .build();

            IndividualEntry individualEntry = IndividualEntry.builder().id(id).individualId(individualId)
                    .actualTotalAttendance(actualTotalAttendance).modifiedTotalAttendance(modifiedtotalAttendance).additionalDetails(additionalDetails).auditDetails(auditDetails).build();

            if (!individualMap.containsKey(id)) {
                individualMap.put(id, individualEntry);
                isExist = false;
            }
            addAttendanceChildrenToProperty(rs, individualMap.get(id));

            if (!isExist) {
                musterRoll.addIndividualEntriesItem(individualEntry);
            }

        }
    }

    private void addAttendanceChildrenToProperty(ResultSet rs, IndividualEntry individualEntry)
            throws SQLException {
        addAttendanceEntry(rs, individualEntry);
    }

    private void addAttendanceEntry(ResultSet rs, IndividualEntry individualEntry) throws SQLException {
        String id = rs.getString("attendanceId");
        String summaryId = rs.getString("attnSummaryId");
        BigDecimal date = rs.getBigDecimal("AttnDate");
        BigDecimal attendance = rs.getBigDecimal("attendance");

        String createdby = rs.getString("attnCreatedBy");
        String lastmodifiedby = rs.getString("attnModifiedBy");
        Long createdtime = rs.getLong("attnCreatedTime");
        Long lastmodifiedtime = rs.getLong("attnModifiedTime");

        if (StringUtils.isNotBlank(summaryId) && summaryId.equalsIgnoreCase(individualEntry.getId())) {

            JsonNode additionalDetails = getAdditionalDetail("attnAddlDetails", rs);
            AuditDetails auditDetails = AuditDetails.builder().createdBy(createdby).createdTime(createdtime)
                    .lastModifiedBy(lastmodifiedby).lastModifiedTime(lastmodifiedtime)
                    .build();

            AttendanceEntry attendanceEntry = AttendanceEntry.builder().id(id).time(date)
                    .attendance(attendance).additionalDetails(additionalDetails).auditDetails(auditDetails).build();

            individualEntry.addAttendanceEntriesItem(attendanceEntry);

        }
    }

    private JsonNode getAdditionalDetail(String columnName, ResultSet rs) throws SQLException {
        JsonNode additionalDetails = null;
        try {
            PGobject obj = (PGobject) rs.getObject(columnName);
            if (obj != null) {
                additionalDetails = mapper.readTree(obj.getValue());
            }
        } catch (IOException e) {
            throw new CustomException("PARSING ERROR", "Failed to parse additionalDetail object");
        }
        if (additionalDetails.isEmpty())
            additionalDetails = null;
        return additionalDetails;
    }

}
