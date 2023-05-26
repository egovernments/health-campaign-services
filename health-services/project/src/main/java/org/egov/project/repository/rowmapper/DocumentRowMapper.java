package org.egov.project.repository.rowmapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.project.Document;
import org.egov.tracer.model.CustomException;
import org.postgresql.util.PGobject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DocumentRowMapper implements ResultSetExtractor<List<Document>> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<Document> extractData(ResultSet rs) throws SQLException, DataAccessException {
        Map<String, Document> documentMap = new LinkedHashMap<>();

        while (rs.next()) {

            String document_id = rs.getString("documentId");
            String document_projectId = rs.getString("document_projectId");
            String document_documentType = rs.getString("document_documentType");
            String document_fileStoreId = rs.getString("document_fileStoreId");
            String document_documentUid = rs.getString("document_documentUid");
            JsonNode document_additionalDetails = getAdditionalDetail("document_additionalDetails", rs);
            String document_status = rs.getString("document_status");
            String document_createdBy = rs.getString("document_createdBy");
            String document_lastModifiedBy = rs.getString("document_lastModifiedBy");
            Long document_createdTime = rs.getLong("document_createdTime");
            Long document_lastModifiedTime = rs.getLong("document_lastModifiedTime");

            AuditDetails documenttAuditDetails = AuditDetails.builder().createdBy(document_createdBy).createdTime(document_createdTime)
                    .lastModifiedBy(document_lastModifiedBy).lastModifiedTime(document_lastModifiedTime)
                    .build();

            Document document = Document.builder()
                    .id(document_id)
                    .projectid(document_projectId)
                    .documentType(document_documentType)
                    .fileStoreId(document_fileStoreId)
                    .documentUid(document_documentUid)
                    .additionalDetails(document_additionalDetails)
                    .status(document_status)
                    .auditDetails(documenttAuditDetails)
                    .build();

            if (!documentMap.containsKey(document_id)) {
                documentMap.put(document_id, document);
            }
        }

        return new ArrayList<>(documentMap.values());
    }

    private JsonNode getAdditionalDetail(String columnName, ResultSet rs) throws SQLException {
        JsonNode additionalDetails = null;
        try {
            PGobject obj = (PGobject) rs.getObject(columnName);
            if (obj != null) {
                additionalDetails = objectMapper.readTree(obj.getValue());
            }
        } catch (IOException e) {
            throw new CustomException("PARSING ERROR", "Failed to parse additionalDetail object");
        }
        if (additionalDetails != null && additionalDetails.isEmpty())
            additionalDetails = null;
        return additionalDetails;
    }
}
