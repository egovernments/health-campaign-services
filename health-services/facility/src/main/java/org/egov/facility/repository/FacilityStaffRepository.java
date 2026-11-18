package org.egov.facility.repository;

import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.producer.Producer;
import org.egov.facility.repository.rowmapper.FacilityStaffRowMapper;
import org.egov.facility.web.models.staff.FacilityStaff;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

@Repository
public class FacilityStaffRepository extends GenericRepository<FacilityStaff> {

    @Autowired
    public FacilityStaffRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                   RedisTemplate<String, Object> redisTemplate,
                                   SelectQueryBuilder selectQueryBuilder, FacilityStaffRowMapper rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder,
                rowMapper, Optional.of("facility_staff"));
    }

    public Set<String> findActiveFacilityIds(String tenantId, String staffId) throws InvalidTenantIdException {
        String query = String.format("SELECT facilityId FROM %s.facility_staff WHERE staffId = :staffId "
                + "AND tenantId = :tenantId AND isDeleted = false", SCHEMA_REPLACE_STRING);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        Map<String, Object> params = new HashMap<>();
        params.put("staffId", staffId);
        params.put("tenantId", tenantId);
        return new HashSet<>(namedParameterJdbcTemplate.queryForList(query, params, String.class));
    }

    public List<FacilityStaff> findActive(String tenantId, List<String> facilityIds, List<String> staffIds)
            throws InvalidTenantIdException {
        String query = String.format("SELECT * FROM %s.facility_staff WHERE facilityId IN (:facilityIds) "
                + "AND staffId IN (:staffIds) AND tenantId = :tenantId AND isDeleted = false", SCHEMA_REPLACE_STRING);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, tenantId);
        Map<String, Object> params = new HashMap<>();
        params.put("facilityIds", facilityIds);
        params.put("staffIds", staffIds);
        params.put("tenantId", tenantId);
        return namedParameterJdbcTemplate.query(query, params, rowMapper);
    }
}
