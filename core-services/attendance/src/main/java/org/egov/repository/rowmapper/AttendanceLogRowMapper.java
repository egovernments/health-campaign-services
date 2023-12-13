package org.egov.repository.rowmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.egov.tracer.model.CustomException;
import org.egov.web.models.AttendanceLog;
import org.egov.web.models.Document;
import org.egov.web.models.Status;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class AttendanceLogRowMapper implements ResultSetExtractor<List<AttendanceLog>> {

    @Autowired
    private ObjectMapper mapper;

    @Override
    public List<AttendanceLog> extractData(ResultSet rs) throws SQLException, DataAccessException {

        Map<String, AttendanceLog> attendanceLogMap = new LinkedHashMap<>();

        while (rs.next()) {
            String id = rs.getString("logid");
            String individualId = rs.getString("logIndividualId");
            String tenantId = rs.getString("logTenantId");
            String registerId = rs.getString("logRegisterId");
            String status = rs.getString("logStatus");
            BigDecimal time = rs.getBigDecimal("logTime");
            String eventType = rs.getString("logEventType");
            String createdby = rs.getString("logCreatedBy");
            String lastmodifiedby = rs.getString("logLastModifiedBy");
            Long createdtime = rs.getLong("logCreatedTime");
            Long lastmodifiedtime = rs.getLong("logLastModifiedTime");

            AuditDetails auditDetails = AuditDetails.builder().createdBy(createdby).createdTime(createdtime)
                    .lastModifiedBy(lastmodifiedby).lastModifiedTime(lastmodifiedtime)
                    .build();

            JsonNode additionalDetails = getAdditionalDetail("logAdditionalDetails", rs);

            AttendanceLog attendanceLog = AttendanceLog.builder()
                    .id(id)
                    .individualId(individualId)
                    .tenantId(tenantId)
                    .registerId(registerId)
                    .status(Status.fromValue(status))
                    .time(time)
                    .type(eventType)
                    .additionalDetails(additionalDetails)
                    .auditDetails(auditDetails)
                    .build();

            if (!attendanceLogMap.containsKey(id)) {
                attendanceLogMap.put(id, attendanceLog);
            }

            addDocumentsDetails(rs, attendanceLogMap.get(id));
        }
        return new ArrayList<>(attendanceLogMap.values());
    }

    private void addDocumentsDetails(ResultSet rs, AttendanceLog attendanceLog) throws SQLException {
        String documentId = rs.getString("docId");
        String attendanceLogId = rs.getString("docAttendanceLogId");
        if (StringUtils.isNotBlank(documentId) && attendanceLogId.equalsIgnoreCase(attendanceLog.getId().toString())) {
            Document document = Document.builder()
                    .id(documentId)
                    .documentType(rs.getString("docDocumentType"))
                    .fileStore(rs.getString("docFileStoreId"))
                    .documentUid(rs.getString("docFileStoreId"))
                    .status(Status.fromValue(rs.getString("docStatus")))
                    .build();

            JsonNode additionalDetails = getAdditionalDetail("docAdditionalDetails", rs);
            document.setAdditionalDetails(additionalDetails);

            if (attendanceLog.getDocumentIds() == null || attendanceLog.getDocumentIds().isEmpty()) {
                List<Document> documentIdList = new LinkedList<>();
                documentIdList.add(document);
                attendanceLog.setDocumentIds(documentIdList);
            } else {
                attendanceLog.getDocumentIds().add(document);
            }
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
            log.error("Failed to parse additionalDetail object");
            throw new CustomException("PARSING_ERROR", "Failed to parse additionalDetail object");
        }
        if (additionalDetails.isEmpty())
            additionalDetails = null;
        return additionalDetails;
    }
}
