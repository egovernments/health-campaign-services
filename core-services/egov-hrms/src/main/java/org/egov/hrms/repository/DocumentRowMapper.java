package org.egov.hrms.repository;

import org.egov.hrms.model.AuditDetails;
import org.egov.hrms.model.EmployeeDocument;
import org.egov.hrms.model.enums.EmployeeDocumentReferenceType;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentRowMapper implements ResultSetExtractor<List<EmployeeDocument>> {

    @Override
    public List<EmployeeDocument> extractData(ResultSet rs) throws SQLException {

        List<EmployeeDocument> documents = new ArrayList<>();
        while (rs.next()) {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("createdby"))
                    .createdDate(rs.getLong("createddate"))
                    .lastModifiedBy(rs.getString("lastmodifiedby"))
                    .lastModifiedDate(rs.getLong("lastmodifieddate"))
                    .build();

            EmployeeDocument document = EmployeeDocument.builder()
                    .id(rs.getString("docs_uuid"))
                    .documentName(rs.getString("documentname"))
                    .documentId(rs.getString("documentid"))
                    .referenceType(EmployeeDocumentReferenceType.valueOf(rs.getString("referencetype")))
                    .referenceId(rs.getString("referenceid"))
                    .employeeId(rs.getString("employeeid"))
                    .tenantId(rs.getString("tenantid"))
                    .auditDetails(auditDetails)
                    .build();
            documents.add(document);
        }
        return documents;
    }

}
