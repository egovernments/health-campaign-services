package org.egov.product.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.product.web.models.AdditionalFields;
import org.egov.product.web.models.Product;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class ProductRowMapper implements RowMapper<Product> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Product mapRow(ResultSet resultSet, int i) throws SQLException {

        try {
            return Product.builder()
                    .id(resultSet.getString("id"))
                    .rowVersion(resultSet.getInt("rowversion"))
                    .isDeleted(resultSet.getBoolean("isdeleted"))
                    .tenantId(resultSet.getString("tenantid"))
                    .name(resultSet.getString("name"))
                    .manufacturer(resultSet.getString("manufacturer"))
                    .type(resultSet.getString("type"))
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdby"))
                            .createdTime(resultSet.getLong("createdtime"))
                            .lastModifiedBy(resultSet.getString("lastmodifiedby"))
                            .lastModifiedTime(resultSet.getLong("lastmodifiedtime"))
                            .build())
                    .additionalFields(objectMapper.readValue(resultSet.getString("additionalDetails"), AdditionalFields.class))
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }
    }
}
