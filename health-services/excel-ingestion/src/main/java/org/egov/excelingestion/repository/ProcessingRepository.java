package org.egov.excelingestion.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.excelingestion.config.ProcessingConstants;
import org.egov.excelingestion.web.models.ProcessResource;
import org.egov.excelingestion.web.models.ProcessingSearchCriteria;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.common.exception.InvalidTenantIdException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.models.AuditDetails;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

@Repository
@Slf4j
public class ProcessingRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MultiStateInstanceUtil multiStateInstanceUtil;

    public ProcessingRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate, 
                              ObjectMapper objectMapper,
                              MultiStateInstanceUtil multiStateInstanceUtil) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.objectMapper = objectMapper;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
    }

    public List<ProcessResource> search(ProcessingSearchCriteria criteria) throws InvalidTenantIdException {
        Map<String, Object> preparedStmtList = new HashMap<>();
        
        // Build base query without SELECT columns and pagination
        StringBuilder baseQuery = buildBaseQuery(criteria, preparedStmtList);
        
        // Create data query with SELECT * and pagination
        StringBuilder dataQuery = new StringBuilder("SELECT * FROM (");
        dataQuery.append(baseQuery);
        dataQuery.append(") AS base_result");
        dataQuery.append(" ORDER BY createdtime DESC");
        
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
            return namedParameterJdbcTemplate.query(finalQuery, preparedStmtList, new ProcessingRowMapper());
        } catch (Exception e) {
            log.error("Error searching processing records: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search processing records", e);
        }
    }

    public Long getCount(ProcessingSearchCriteria criteria) throws InvalidTenantIdException {
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
            log.error("Error getting count of processing records: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get count of processing records", e);
        }
    }
    
    private StringBuilder buildBaseQuery(ProcessingSearchCriteria criteria, Map<String, Object> preparedStmtList) {
        StringBuilder query = new StringBuilder(String.format("SELECT * FROM %s." + ProcessingConstants.PROCESS_TABLE_NAME + " WHERE tenantid = :tenantId", SCHEMA_REPLACE_STRING));
        
        preparedStmtList.put("tenantId", criteria.getTenantId());
        addCriteria(query, criteria, preparedStmtList);
        
        return query;
    }

    private void addCriteria(StringBuilder query, ProcessingSearchCriteria criteria, Map<String, Object> preparedStmtList) {
        if (!CollectionUtils.isEmpty(criteria.getIds())) {
            query.append(" AND id IN (:ids)");
            preparedStmtList.put("ids", criteria.getIds());
        }

        if (!CollectionUtils.isEmpty(criteria.getReferenceIds())) {
            query.append(" AND referenceid IN (:referenceIds)");
            preparedStmtList.put("referenceIds", criteria.getReferenceIds());
        }

        if (!CollectionUtils.isEmpty(criteria.getTypes())) {
            query.append(" AND type IN (:types)");
            preparedStmtList.put("types", criteria.getTypes());
        }

        if (!CollectionUtils.isEmpty(criteria.getStatuses())) {
            query.append(" AND status IN (:statuses)");
            preparedStmtList.put("statuses", criteria.getStatuses());
        }
    }

    private class ProcessingRowMapper implements RowMapper<ProcessResource> {
        @Override
        public ProcessResource mapRow(ResultSet rs, int rowNum) throws SQLException {
            try {
                JsonNode additionalDetails = null;
                String additionalDetailsStr = rs.getString("additionaldetails");
                if (additionalDetailsStr != null) {
                    additionalDetails = objectMapper.readTree(additionalDetailsStr);
                }

                AuditDetails auditDetails = AuditDetails.builder()
                    .createdBy(rs.getString("createdby"))
                    .lastModifiedBy(rs.getString("lastmodifiedby"))
                    .createdTime(rs.getLong("createdtime"))
                    .lastModifiedTime(rs.getLong("lastmodifiedtime"))
                    .build();

                return ProcessResource.builder()
                    .id(rs.getString("id"))
                    .referenceId(rs.getString("referenceid"))
                    .tenantId(rs.getString("tenantid"))
                    .type(rs.getString("type"))
                    .hierarchyType(rs.getString("hierarchytype"))
                    .fileStoreId(rs.getString("filestoreid"))
                    .processedFileStoreId(rs.getString("processedfilestoreid"))
                    .status(rs.getString("status"))
                    .additionalDetails(additionalDetails != null ? objectMapper.convertValue(additionalDetails, java.util.Map.class) : null)
                    .auditDetails(auditDetails)
                    .processedStatus(rs.getString("processedstatus"))
                    .build();
            } catch (Exception e) {
                log.error("Error mapping row to ProcessResource: {}", e.getMessage(), e);
                throw new SQLException("Failed to map row to ProcessResource", e);
            }
        }
    }
}