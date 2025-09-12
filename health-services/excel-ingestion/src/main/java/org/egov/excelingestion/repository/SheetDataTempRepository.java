package org.egov.excelingestion.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.common.exception.InvalidTenantIdException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

/**
 * Repository for sheet data temp table operations with multi-tenancy support
 */
@Repository
@Slf4j
public class SheetDataTempRepository {

    private static final String TABLE_NAME = "eg_ex_in_sheet_data_temp";
    
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final MultiStateInstanceUtil multiStateInstanceUtil;
    private final ObjectMapper objectMapper;

    public SheetDataTempRepository(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                  MultiStateInstanceUtil multiStateInstanceUtil,
                                  ObjectMapper objectMapper) {
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.multiStateInstanceUtil = multiStateInstanceUtil;
        this.objectMapper = objectMapper;
    }

    /**
     * Search sheet data by referenceId and fileStoreId
     */
    public List<Map<String, Object>> searchSheetData(String tenantId, String referenceId, String fileStoreId, 
                                                     String sheetName, Integer limit, Integer offset) throws InvalidTenantIdException {
        Map<String, Object> params = new HashMap<>();
        
        StringBuilder query = new StringBuilder(String.format("SELECT * FROM %s." + TABLE_NAME + " WHERE 1=1", SCHEMA_REPLACE_STRING));

        if (referenceId != null) {
            query.append(" AND referenceId = :referenceId");
            params.put("referenceId", referenceId);
        }

        if (fileStoreId != null) {
            query.append(" AND fileStoreId = :fileStoreId");
            params.put("fileStoreId", fileStoreId);
        }

        if (sheetName != null) {
            query.append(" AND sheetName = :sheetName");
            params.put("sheetName", sheetName);
        }

        query.append(" ORDER BY sheetName, rowNumber");

        if (limit != null) {
            query.append(" LIMIT :limit");
            params.put("limit", limit);
        }

        if (offset != null) {
            query.append(" OFFSET :offset");
            params.put("offset", offset);
        }

        String finalQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(query.toString(), tenantId);
        
        log.info("Executing sheet data search query for tenant: {}", tenantId);
        List<Map<String, Object>> results = namedParameterJdbcTemplate.queryForList(finalQuery, params);
        
        // Parse JSONB rowjson field to actual JSON object
        for (Map<String, Object> row : results) {
            Object rowJsonValue = row.get("rowjson");
            if (rowJsonValue != null) {
                try {
                    Map<String, Object> parsedJson = objectMapper.readValue(rowJsonValue.toString(), 
                            new TypeReference<Map<String, Object>>() {});
                    row.put("rowjson", parsedJson);
                } catch (Exception e) {
                    log.warn("Failed to parse rowjson for row: {}", e.getMessage());
                }
            }
        }
        
        return results;
    }

    /**
     * Count total records for given criteria
     */
    public Integer countSheetData(String tenantId, String referenceId, String fileStoreId, String sheetName) throws InvalidTenantIdException {
        Map<String, Object> params = new HashMap<>();
        
        StringBuilder query = new StringBuilder(String.format("SELECT COUNT(*) FROM %s." + TABLE_NAME + " WHERE 1=1", SCHEMA_REPLACE_STRING));

        if (referenceId != null) {
            query.append(" AND referenceId = :referenceId");
            params.put("referenceId", referenceId);
        }

        if (fileStoreId != null) {
            query.append(" AND fileStoreId = :fileStoreId");
            params.put("fileStoreId", fileStoreId);
        }

        if (sheetName != null) {
            query.append(" AND sheetName = :sheetName");
            params.put("sheetName", sheetName);
        }

        String finalQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(query.toString(), tenantId);
        
        return namedParameterJdbcTemplate.queryForObject(finalQuery, params, Integer.class);
    }

    /**
     * Get sheet-wise count for given referenceId and fileStoreId
     */
    public List<Map<String, Object>> getSheetWiseCount(String tenantId, String referenceId, String fileStoreId) throws InvalidTenantIdException {
        String query = String.format("SELECT sheetName, COUNT(*) as recordCount FROM %s." + TABLE_NAME + 
                      " WHERE referenceId = :referenceId AND fileStoreId = :fileStoreId " +
                      "GROUP BY sheetName ORDER BY sheetName", SCHEMA_REPLACE_STRING);
        
        Map<String, Object> params = new HashMap<>();
        params.put("referenceId", referenceId);
        params.put("fileStoreId", fileStoreId);
        
        String finalQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        
        return namedParameterJdbcTemplate.queryForList(finalQuery, params);
    }
}