package org.egov.referralmanagement.repository;

import org.egov.common.ds.Tuple;
import org.egov.common.models.household.Household;
import org.egov.referralmanagement.repository.rowmapper.HouseholdRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class HouseholdRepository {

	@Autowired
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	
	@Autowired
	private HouseholdRowMapper householdRowMapper;
	
	 public Tuple<Long, List<Household>> findByView (String localityCode, Integer limit, Integer offset, String tenantId, Long lastModifiedTime) {
	    	
	    	
	    	String query = "select * from household_address_mv where localitycode=:localitycode and rank between :start and :end ";

	        Map<String, Object> paramsMap = new HashMap<>();
	        paramsMap.put("start", offset);
	        paramsMap.put("end", offset+limit);
	        paramsMap.put("localitycode", localityCode);

	        Map<String, Object> paramsMapCount = new HashMap<>();
	        paramsMapCount.put("localitycode", localityCode);
	        paramsMapCount.put("lastModifiedTime", lastModifiedTime);
	        Integer maxRank = namedParameterJdbcTemplate.queryForObject("select max(rank) from  household_address_mv where localitycode=:localitycode and lastModifiedTime>=:lastModifiedTime", paramsMapCount, Integer.class);
	        Long totalCount = maxRank == null ? 0L : Long.valueOf(maxRank);
	        return new Tuple<>(totalCount, this.namedParameterJdbcTemplate.query(query, paramsMap, householdRowMapper));
	    }

	public Tuple<Long, List<Household>> findByViewCFL (String localityCode, Integer limit, Integer offset, String tenantId, Long lastModifiedTime, String householdId) {
		String query = null;
		if (householdId == null) {
			query = "select * from household_address_cfl_mv where localitycode=:localitycode and rank between :start and :end ";
		} else {
			query = "select * from household_address_cfl_mv where localitycode=:localitycode and rank between :start and :end and householdId=:householdId";
		}


		Map<String, Object> paramsMap = new HashMap<>();
		paramsMap.put("start", offset);
		paramsMap.put("end", offset+limit);
		paramsMap.put("localitycode", localityCode);
		paramsMap.put("householdId", householdId);

		Map<String, Object> paramsMapCount = new HashMap<>();
		paramsMapCount.put("localitycode", localityCode);
		paramsMapCount.put("lastModifiedTime", lastModifiedTime);
		Integer maxRank = namedParameterJdbcTemplate.queryForObject("select max(rank) from  household_address_cfl_mv where localitycode=:localitycode and lastModifiedTime>=:lastModifiedTime", paramsMapCount, Integer.class);
		Long totalCount = maxRank == null ? 0L : Long.valueOf(maxRank);
		return new Tuple<>(totalCount, this.namedParameterJdbcTemplate.query(query, paramsMap, householdRowMapper));

	}
}
