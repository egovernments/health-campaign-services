package org.egov.excelingestion.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.models.AuditDetails;
import org.egov.excelingestion.config.ErrorConstants;
import org.egov.excelingestion.constants.GenerationConstants;
import org.egov.tracer.model.CustomException;
import org.egov.excelingestion.web.models.GenerateResource;
import org.egov.excelingestion.web.models.GenerationSearchCriteria;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.common.exception.InvalidTenantIdException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

@Repository
@Slf4j
public class GeneratedFileRepository {

    private static final Pattern ADDITIONAL_DETAILS_KEY_PATTERN = Pattern.compile(ErrorConstants.ADDITIONAL_DETAILS_KEY_PATTERN);

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MultiStateInstanceUtil multiStateInstanceUtil;

    public GeneratedFileRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                 ObjectMapper objectMapper,
                                 MultiStateInstanceUtil multiStateInstanceUtil) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.objectMapper = objectMapper;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
    }

    public List<GenerateResource> search(GenerationSearchCriteria criteria) throws InvalidTenantIdException {
        Map<String, Object> preparedStmtList = new HashMap<>();
        
        // Build base query without SELECT columns and pagination
        StringBuilder baseQuery = buildBaseQuery(criteria, preparedStmtList);
        
        // Create data query with SELECT * and pagination
        StringBuilder dataQuery = new StringBuilder("SELECT * FROM (");
        dataQuery.append(baseQuery);
        dataQuery.append(") AS base_result");
        dataQuery.append(" ORDER BY createdTime DESC");
        
        if (criteria.getLimit() != null) {
            dataQuery.append(" LIMIT :limit");
            preparedStmtList.put("limit", criteria.getLimit());
        }
        
        if (criteria.getOffset() != null) {
            dataQuery.append(" OFFSET :offset");
            preparedStmtList.put("offset", criteria.getOffset());
        }
        
        String finalQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(dataQuery.toString(), criteria.getTenantId());
        
        try {
            return namedParameterJdbcTemplate.query(finalQuery, preparedStmtList, new GeneratedFileRowMapper());
        } catch (Exception e) {
            log.error("Error searching generated files: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search generated files", e);
        }
    }

    public Long getCount(GenerationSearchCriteria criteria) throws InvalidTenantIdException {
        Map<String, Object> preparedStmtList = new HashMap<>();
        
        // Build base query without SELECT columns and pagination
        StringBuilder baseQuery = buildBaseQuery(criteria, preparedStmtList);
        
        // Create count query using the base query
        StringBuilder countQuery = new StringBuilder("SELECT COUNT(*) FROM (");
        countQuery.append(baseQuery);
        countQuery.append(") AS base_result");
        
        String finalQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(countQuery.toString(), criteria.getTenantId());
        
        try {
            return namedParameterJdbcTemplate.queryForObject(finalQuery, preparedStmtList, Long.class);
        } catch (Exception e) {
            log.error("Error getting count of generated files: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get count of generated files", e);
        }
    }
    
    private StringBuilder buildBaseQuery(GenerationSearchCriteria criteria, Map<String, Object> preparedStmtList) {
        StringBuilder query = new StringBuilder(String.format("SELECT * FROM %s." + GenerationConstants.TABLE_NAME + " WHERE tenantId = :tenantId", SCHEMA_REPLACE_STRING));
        
        preparedStmtList.put("tenantId", criteria.getTenantId());
        addCriteria(query, criteria, preparedStmtList);
        
        return query;
    }

    private void addCriteria(StringBuilder query, GenerationSearchCriteria criteria, Map<String, Object> preparedStmtList) {
        if (!CollectionUtils.isEmpty(criteria.getIds())) {
            query.append(" AND id IN (:ids)");
            preparedStmtList.put("ids", criteria.getIds());
        }

        if (!CollectionUtils.isEmpty(criteria.getReferenceIds())) {
            query.append(" AND referenceId IN (:referenceIds)");
            preparedStmtList.put("referenceIds", criteria.getReferenceIds());
        }

        if (!CollectionUtils.isEmpty(criteria.getReferenceTypes())) {
            query.append(" AND referenceType IN (:referenceTypes)");
            preparedStmtList.put("referenceTypes", criteria.getReferenceTypes());
        }

        if (!CollectionUtils.isEmpty(criteria.getTypes())) {
            query.append(" AND type IN (:types)");
            preparedStmtList.put("types", criteria.getTypes());
        }

        if (!CollectionUtils.isEmpty(criteria.getStatuses())) {
            query.append(" AND status IN (:statuses)");
            preparedStmtList.put("statuses", criteria.getStatuses());
        }

        if (criteria.getLocale() != null && !criteria.getLocale().trim().isEmpty()) {
            query.append(" AND locale = :locale");
            preparedStmtList.put("locale", criteria.getLocale());
        }

        if (!CollectionUtils.isEmpty(criteria.getAdditionalDetails())) {
            query.append(" AND additionalDetails IS NOT NULL");
            for (Entry<String, String> entry : criteria.getAdditionalDetails().entrySet()) {
                validateAdditionalDetailsKey(entry.getKey());
                String paramKey = "ad_" + entry.getKey();
                query.append(" AND additionalDetails->>'").append(entry.getKey()).append("' = :").append(paramKey);
                preparedStmtList.put(paramKey, entry.getValue());
            }
        }
    }

    /**
     * The additionalDetails key is interpolated into the JSONB path of the SQL string (the value
     * is bound). Restricting it to a safe charset prevents SQL injection via attacker-supplied keys.
     */
    private void validateAdditionalDetailsKey(String key) {
        if (key == null || !ADDITIONAL_DETAILS_KEY_PATTERN.matcher(key).matches()) {
            throw new CustomException(ErrorConstants.INVALID_SEARCH_KEY, ErrorConstants.INVALID_SEARCH_KEY_MESSAGE);
        }
    }

    private class GeneratedFileRowMapper implements RowMapper<GenerateResource> {
        @Override
        public GenerateResource mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                JsonNode additionalDetails = null;
                String additionalDetailsStr = rs.getString("additionalDetails");
                if (additionalDetailsStr != null) {
                    additionalDetails = objectMapper.readTree(additionalDetailsStr);
                }

                AuditDetails auditDetails = AuditDetails.builder()
                        .createdBy(rs.getString("createdby"))
                        .lastModifiedBy(rs.getString("lastmodifiedby"))
                        .createdTime(rs.getLong("createdtime"))
                        .lastModifiedTime(rs.getLong("lastmodifiedtime"))
                        .build();

                return GenerateResource.builder()
                    .id(rs.getString("id"))
                    .referenceId(rs.getString("referenceId"))
                    .referenceType(rs.getString("referencetype"))
                    .tenantId(rs.getString("tenantId"))
                    .type(rs.getString("type"))
                    .fileStoreId(rs.getString("fileStoreId"))
                    .status(rs.getString("status"))
                    .locale(rs.getString("locale"))
                    .additionalDetails(additionalDetails != null ? objectMapper.convertValue(additionalDetails, java.util.Map.class) : null)
                    .auditDetails(auditDetails)
                    .build();
            } catch (Exception e) {
                log.error("Error mapping row to GenerateResource: {}", e.getMessage(), e);
                throw new SQLException("Failed to map row to GenerateResource", e);
            }
        }
    }
}