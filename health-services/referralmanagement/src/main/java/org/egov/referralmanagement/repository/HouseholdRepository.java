package org.egov.referralmanagement.repository;

import org.egov.common.ds.Tuple;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.models.household.Household;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.referralmanagement.repository.rowmapper.HouseholdRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.egov.common.utils.MultiStateInstanceUtil.SCHEMA_REPLACE_STRING;

@Repository
public class HouseholdRepository {

	@Autowired
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	
	@Autowired
	private HouseholdRowMapper householdRowMapper;

    @Autowired
    private MultiStateInstanceUtil multiStateInstanceUtil;
	
	 public Tuple<Long, List<Household>> findByView (String localityCode, Integer limit, Integer offset, String tenantId) throws InvalidTenantIdException {
	    	
	    	String totalCountQuery = multiStateInstanceUtil.replaceSchemaPlaceholder("select max(rank) from  " + SCHEMA_REPLACE_STRING + ".household_address_mv where localitycode=:localitycode", tenantId);
	    	String query = multiStateInstanceUtil.replaceSchemaPlaceholder("select * from " + SCHEMA_REPLACE_STRING + ".household_address_mv where localitycode=:localitycode and rank between :start and :end ", tenantId);

	        Map<String, Object> paramsMap = new HashMap<>();
	        paramsMap.put("start", offset);
	        paramsMap.put("end", offset+limit);
	        paramsMap.put("localitycode", localityCode);

	        Map<String, Object> paramsMapCount = new HashMap<>();
	        paramsMapCount.put("localitycode", localityCode);
	        Integer maxRank = namedParameterJdbcTemplate.queryForObject(totalCountQuery, paramsMapCount, Integer.class);
	        Long totalCount = maxRank == null ? 0L : Long.valueOf(maxRank);
	        return new Tuple<>(totalCount, this.namedParameterJdbcTemplate.query(query, paramsMap, householdRowMapper));
	    }
}
