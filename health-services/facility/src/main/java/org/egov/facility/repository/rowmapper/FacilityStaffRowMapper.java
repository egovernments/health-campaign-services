package org.egov.facility.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.contract.models.AuditDetails;
import org.egov.facility.web.models.staff.FacilityStaff;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class FacilityStaffRowMapper implements RowMapper<FacilityStaff> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public FacilityStaff mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            String additionalDetails = resultSet.getString("additionalDetails");
            return FacilityStaff.builder()
                    .id(resultSet.getString("id"))
                    .tenantId(resultSet.getString("tenantId"))
                    .facilityId(resultSet.getString("facilityId"))
                    .staffId(resultSet.getString("staffId"))
                    .additionalFields(additionalDetails == null ? null
                            : objectMapper.readValue(additionalDetails, AdditionalFields.class))
                    .auditDetails(AuditDetails.builder()
                            .createdBy(resultSet.getString("createdBy"))
                            .createdTime(resultSet.getLong("createdTime"))
                            .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                            .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                            .build())
                    .rowVersion(resultSet.getInt("rowVersion"))
                    .isDeleted(resultSet.getBoolean("isDeleted"))
                    .build();
        } catch (JsonProcessingException e) {
            throw new SQLException(e);
        }
    }
}
