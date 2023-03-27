package org.egov.individual.repository.rowmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import digit.models.coremodels.AuditDetails;
import org.egov.individual.web.models.AdditionalFields;
import org.egov.individual.web.models.BloodGroup;
import org.egov.individual.web.models.Gender;
import org.egov.individual.web.models.Individual;
import org.egov.individual.web.models.Name;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

@Component
public class IndividualRowMapper implements RowMapper<Individual> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Individual mapRow(ResultSet resultSet, int i) throws SQLException {
        try {
            return Individual.builder().id(resultSet.getString("id"))
                    .individualId(resultSet.getString("individualid"))
                    .userId(resultSet.getString("userId"))
                    .clientReferenceId(resultSet.getString("clientReferenceId"))
                    .tenantId(resultSet.getString("tenantId"))
                    .name(Name.builder().givenName(resultSet.getString("givenName"))
                            .familyName(resultSet.getString("familyName"))
                            .otherNames(resultSet.getString("otherNames")).build())
                    .dateOfBirth(resultSet.getDate("dateOfBirth") != null ?
                            resultSet.getDate("dateOfBirth"): null)
                    .gender(Gender.fromValue(resultSet.getString("gender")))
                    .bloodGroup(BloodGroup.fromValue(resultSet.getString("bloodGroup")))
                    .mobileNumber(resultSet.getString("mobileNumber"))
                    .altContactNumber(resultSet.getString("altContactNumber"))
                    .email(resultSet.getString("email"))
                    .fatherName(resultSet.getString("fatherName"))
                    .husbandName(resultSet.getString("husbandName"))
                    .relationship(resultSet.getString("relationship"))
                    .photo(resultSet.getString("photo"))
                    .additionalFields(resultSet.getString("additionalDetails") == null ? null :
                            objectMapper.readValue(resultSet.getString("additionalDetails"),
                                    AdditionalFields.class))
                            .auditDetails(AuditDetails.builder()
                                    .createdBy(resultSet.getString("createdBy"))
                                    .lastModifiedBy(resultSet.getString("lastModifiedBy"))
                                    .createdTime(resultSet.getLong("createdTime"))
                                    .lastModifiedTime(resultSet.getLong("lastModifiedTime"))
                                    .build())
                    .rowVersion(resultSet.getInt("rowVersion"))
                    .isDeleted(resultSet.getBoolean("isDeleted"))
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
