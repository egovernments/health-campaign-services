package org.egov.stock.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.stock.ReferenceIdType;
import org.egov.common.models.stock.SenderReceiverType;
import org.egov.common.models.stock.Stock;
import org.egov.common.models.stock.TransactionReason;
import org.egov.common.models.stock.TransactionType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class StockRowMapper implements RowMapper<Stock> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Stock mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(resultSet.getString("createdBy"))
                    .createdTime(resultSet.getLong("createdTime"))
                    .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                    .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                    .build();
            AuditDetails clientAuditDetails = AuditDetails.builder()
                    .createdTime(resultSet.getLong("clientCreatedTime"))
                    .createdBy(resultSet.getString("clientCreatedBy"))
                    .lastModifiedTime(resultSet.getLong("clientLastModifiedTime"))
                    .lastModifiedBy(resultSet.getString("clientLastModifiedBy"))
                    .build();
            Long dateOfEntry = resultSet.getLong("dateOfEntry");
            if(resultSet.wasNull()){
                dateOfEntry = null;
            }
            return Stock.builder()
                    .id(resultSet.getString("id"))
                    .clientReferenceId(resultSet.getString("clientReferenceId"))
                    .tenantId(resultSet.getString("tenantId"))
                    .productVariantId(resultSet.getString("productVariantId"))
                    .quantity(resultSet.getInt("quantity"))
                    .wayBillNumber(resultSet.getString("wayBillNumber"))
                    .referenceId(resultSet.getString("referenceId"))
                    .referenceIdType(ReferenceIdType.fromValue(resultSet.getString("referenceIdType")))
                    .transactionType(TransactionType.fromValue(resultSet.getString("transactionType")))
                    .transactionReason(TransactionReason.fromValue(resultSet.getString("transactionReason")))
                    .senderId(resultSet.getString("senderId"))
                    .senderType(SenderReceiverType.fromValue(resultSet.getString("senderType")))
                    .receiverId(resultSet.getString("receiverId"))
                    .receiverType(SenderReceiverType.fromValue(resultSet.getString("receiverType")))
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper
                        .readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .auditDetails(auditDetails)
                    .clientAuditDetails(clientAuditDetails)
                    .rowVersion(resultSet.getInt("rowVersion"))
                    .isDeleted(resultSet.getBoolean("isDeleted"))
                    .dateOfEntry(dateOfEntry)
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }
    }
}
