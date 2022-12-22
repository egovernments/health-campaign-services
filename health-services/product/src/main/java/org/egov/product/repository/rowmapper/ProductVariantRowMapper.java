package org.egov.product.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.product.web.models.AdditionalFields;
import org.egov.product.web.models.ProductVariant;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ProductVariantRowMapper implements RowMapper<ProductVariant> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ProductVariant mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            return ProductVariant.builder()
                    .id(resultSet.getString("id"))
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .tenantId(resultSet.getString("tenantid"))
                    .productId(resultSet.getString("productid"))
                    .sku(resultSet.getString("sku"))
                    .variation(resultSet.getString("variation"))
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdby"))
                            .createdTime(resultSet.getLong("createdtime"))
                            .lastModifiedBy(resultSet.getString("lastmodifiedby"))
                            .lastModifiedTime(resultSet.getLong("lastmodifiedtime"))
                            .build())
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null : objectMapper.readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }
    }
}
