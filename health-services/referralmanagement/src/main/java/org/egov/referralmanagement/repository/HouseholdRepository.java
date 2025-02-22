package org.egov.referralmanagement.repository;

import org.egov.common.ds.Tuple;
import org.egov.common.models.household.Household;
import org.egov.referralmanagement.repository.rowmapper.HouseholdRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class HouseholdRepository {

	@Autowired
	private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
	
	@Autowired
	private HouseholdRowMapper householdRowMapper;


	public Tuple<Long, List<Household>> findByViewCLF(String localityCode, Integer limit, Integer offset, String tenantId, Long lastModifiedTime, String householdId) {
		String query = null;
		Map<String, Object> paramsMap = new HashMap<>();
		Long totalCount = null;

		query = "select * from household_address_clf_mv where localitycode=:localitycode ";
		if (StringUtils.hasLength(householdId)) {
			query = query + " and id=:id";
			paramsMap.put("id", householdId);
		} else {
			paramsMap.put("start", offset);
			paramsMap.put("end", offset+limit);
			query = query + " and rank between :start and :end ";

			Map<String, Object> paramsMapCount = new HashMap<>();
			paramsMapCount.put("localitycode", localityCode);
			paramsMapCount.put("lastModifiedTime", lastModifiedTime);
			Integer maxRank = namedParameterJdbcTemplate.queryForObject("select max(rank) from  household_address_clf_mv where localitycode=:localitycode and lastModifiedTime>=:lastModifiedTime", paramsMapCount, Integer.class);
			totalCount = maxRank == null ? 0L : Long.valueOf(maxRank);
		}
		paramsMap.put("localitycode", localityCode);
		return new Tuple<>(totalCount, this.namedParameterJdbcTemplate.query(query, paramsMap, householdRowMapper));

	}
}
