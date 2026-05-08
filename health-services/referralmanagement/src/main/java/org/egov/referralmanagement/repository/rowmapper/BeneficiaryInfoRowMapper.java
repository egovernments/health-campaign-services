package org.egov.referralmanagement.repository.rowmapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdMember;
import org.egov.common.models.individual.Address;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.referralmanagement.beneficiarydownsync.BeneficiaryInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * RowMapper implementation for mapping ResultSet rows to BeneficiaryInfo objects.
 * Column names expected in the ResultSet are derived from the JOIN query across
 * INDIVIDUAL, HOUSEHOLD_MEMBER, HOUSEHOLD, ADDRESS, and INDIVIDUAL_IDENTIFIER tables.
 * The taskStatus field is not mapped here — it is resolved separately after the query.
 *
 * Also provides {@link #toBeneficiaryInfo} for constructing BeneficiaryInfo directly
 * from in-memory domain objects when no DB query is involved.
 */
@Slf4j
@Component
public class BeneficiaryInfoRowMapper implements RowMapper<BeneficiaryInfo> {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Maps a ResultSet row to a BeneficiaryInfo object.
     *
     * @param resultSet the ResultSet containing joined query data
     * @param rowNum    the current row number
     * @return a BeneficiaryInfo object mapped from the ResultSet row
     * @throws SQLException if there is an issue accessing ResultSet data
     */
    @Override
    public BeneficiaryInfo mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        AuditData audit = readAuditData(resultSet);

        return BeneficiaryInfo.builder()
                .id(resultSet.getString("id"))
                .clientReferenceId(resultSet.getString("clientReferenceId"))
                .tenantId(resultSet.getString("tenantId"))
                .householdClientReferenceId(resultSet.getString("householdClientReferenceId"))
                .givenName(resultSet.getString("givenName"))
                .mobileNumber(resultSet.getString("mobileNumber"))
                .identifierType(resultSet.getString("identifierType"))
                .identifierId(resultSet.getString("identifierId"))
                .isHead(resultSet.getBoolean("isHead"))
                .latitude(getNullableDouble(resultSet, "latitude"))
                .longitude(getNullableDouble(resultSet, "longitude"))
                .auditCreatedBy(audit.createdBy)
                .auditCreatedTime(audit.createdTime)
                .auditModifiedBy(audit.lastModifiedBy)
                .auditModifiedTime(audit.lastModifiedTime)
                .clientCreatedBy(audit.clientCreatedBy)
                .clientCreatedTime(audit.clientCreatedTime)
                .clientModifiedBy(audit.clientLastModifiedBy)
                .clientModifiedTime(audit.clientLastModifiedTime)
                .isDeleted(resultSet.getBoolean("isDeleted"))
                .rowVersion(resultSet.getInt("rowVersion"))
                .additionalFields(resultSet.getString("additionalFields"))
                .build();
    }

    /** Maps a household member and its related individual, household, and pre-resolved taskStatus
     * into a flattened BeneficiaryInfo. Returns null if the individual is not found.
     *
     * @param householdMember household member record
     * @param individual      corresponding individual; null if not found
     * @param household       corresponding household; null if not found
     * @param taskStatus      pre-resolved task status string; may be null
     * @return populated BeneficiaryInfo, or null if individual is absent
     */
    public BeneficiaryInfo toBeneficiaryInfo(HouseholdMember householdMember,
                                             Individual individual,
                                             Household household,
                                             String taskStatus) {
        if (individual == null) {
            return null;
        }

        Identifier identifier = getPrimaryIdentifier(individual);
        AuditDetails auditDetails = individual.getAuditDetails();
        AuditDetails clientAuditDetails = individual.getClientAuditDetails();

        var householdAddress = household == null ? null : household.getAddress();
        Address individualAddress = getPrimaryAddress(individual);

        Double latitude = householdAddress == null ? null : householdAddress.getLatitude();
        Double longitude = householdAddress == null ? null : householdAddress.getLongitude();
        if (latitude == null) {
            latitude = individualAddress == null ? null : individualAddress.getLatitude();
        }
        if (longitude == null) {
            longitude = individualAddress == null ? null : individualAddress.getLongitude();
        }

        return BeneficiaryInfo.builder()
                .id(individual.getId())
                .clientReferenceId(individual.getClientReferenceId())
                .tenantId(individual.getTenantId())
                .householdClientReferenceId(householdMember.getHouseholdClientReferenceId())
                .givenName(individual.getName() == null ? null : individual.getName().getGivenName())
                .mobileNumber(individual.getMobileNumber())
                .identifierType(identifier == null ? null : identifier.getIdentifierType())
                .identifierId(identifier == null ? null : identifier.getIdentifierId())
                .isHead(householdMember.getIsHeadOfHousehold())
                .taskStatus(taskStatus)
                .latitude(latitude)
                .longitude(longitude)
                .auditCreatedBy(auditDetails == null ? null : auditDetails.getCreatedBy())
                .auditCreatedTime(auditDetails == null ? null : auditDetails.getCreatedTime())
                .auditModifiedBy(auditDetails == null ? null : auditDetails.getLastModifiedBy())
                .auditModifiedTime(auditDetails == null ? null : auditDetails.getLastModifiedTime())
                .clientCreatedBy(clientAuditDetails == null ? null : clientAuditDetails.getCreatedBy())
                .clientCreatedTime(clientAuditDetails == null ? null : clientAuditDetails.getCreatedTime())
                .clientModifiedBy(clientAuditDetails == null ? null : clientAuditDetails.getLastModifiedBy())
                .clientModifiedTime(clientAuditDetails == null ? null : clientAuditDetails.getLastModifiedTime())
                .isDeleted(isDeleted(individual, householdMember, household))
                .rowVersion(individual.getRowVersion())
                .additionalFields(serializeAdditionalFields(individual.getAdditionalFields()))
                .build();
    }

    /** Returns the first non-deleted identifier for the individual, or the first one if all are deleted.
     *
     * @param individual individual whose identifiers to search
     * @return preferred identifier, or null if the individual has none
     */
    private Identifier getPrimaryIdentifier(Individual individual) {
        if (CollectionUtils.isEmpty(individual.getIdentifiers())) {
            return null;
        }
        return individual.getIdentifiers().stream()
                .filter(identifier -> !Boolean.TRUE.equals(identifier.getIsDeleted()))
                .findFirst()
                .orElse(individual.getIdentifiers().get(0));
    }

    /** Returns the first address for the individual, or null if the individual has none.
     *
     * @param individual individual whose addresses to search
     * @return first address, or null
     */
    private Address getPrimaryAddress(Individual individual) {
        if (CollectionUtils.isEmpty(individual.getAddress())) {
            return null;
        }
        return individual.getAddress().get(0);
    }

    /** Returns true if any of the three entities are soft-deleted.
     *
     * @param individual      individual to check
     * @param householdMember household member to check
     * @param household       household to check; may be null
     * @return true if any entity is marked as deleted
     */
    private Boolean isDeleted(Individual individual, HouseholdMember householdMember, Household household) {
        return Boolean.TRUE.equals(individual.getIsDeleted())
                || Boolean.TRUE.equals(householdMember.getIsDeleted())
                || (household != null && Boolean.TRUE.equals(household.getIsDeleted()));
    }

    /** Serializes the given object to a JSON string, returning null on failure.
     * Warns on serialization error rather than propagating so that a bad additionalFields
     * value does not abort the entire response.
     *
     * @param additionalFields the object to serialize; may be null
     * @return JSON string, or null if the input is null or serialization fails
     */
    private String serializeAdditionalFields(Object additionalFields) {
        if (additionalFields == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(additionalFields);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize beneficiary info additionalFields", e);
            return null;
        }
    }

    /**
     * Reads all audit columns from the ResultSet into a value holder.
     * Grouped to mirror the auditDetails / clientAuditDetails split in toBeneficiaryInfo.
     *
     * @param rs the current ResultSet row
     * @return populated AuditData holder
     * @throws SQLException if there is an issue accessing ResultSet data
     */
    private AuditData readAuditData(ResultSet rs) throws SQLException {
        AuditData audit = new AuditData();
        audit.createdBy              = rs.getString("auditCreatedBy");
        audit.createdTime            = getNullableLong(rs, "auditCreatedTime");
        audit.lastModifiedBy         = rs.getString("auditModifiedBy");
        audit.lastModifiedTime       = getNullableLong(rs, "auditModifiedTime");
        audit.clientCreatedBy        = rs.getString("clientCreatedBy");
        audit.clientCreatedTime      = getNullableLong(rs, "clientCreatedTime");
        audit.clientLastModifiedBy   = rs.getString("clientModifiedBy");
        audit.clientLastModifiedTime = getNullableLong(rs, "clientModifiedTime");
        return audit;
    }

    /**
     * Returns null instead of 0 when the column value is SQL NULL.
     *
     * @param rs     the current ResultSet row
     * @param column column name
     * @return column value, or null if the SQL value was NULL
     * @throws SQLException if there is an issue accessing ResultSet data
     */
    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * Returns null instead of 0 when the column value is SQL NULL.
     *
     * @param rs     the current ResultSet row
     * @param column column name
     * @return column value, or null if the SQL value was NULL
     * @throws SQLException if there is an issue accessing ResultSet data
     */
    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static class AuditData {
        String createdBy;
        Long   createdTime;
        String lastModifiedBy;
        Long   lastModifiedTime;
        String clientCreatedBy;
        Long   clientCreatedTime;
        String clientLastModifiedBy;
        Long   clientLastModifiedTime;
    }
}
