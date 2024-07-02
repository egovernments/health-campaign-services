package org.egov.referralmanagement.repository;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralSearch;
import org.egov.common.producer.Producer;
import org.egov.referralmanagement.repository.rowmapper.HFReferralRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdMethod;

/**
 * Repository class for managing the persistence and retrieval of HFReferral entities.
 * This class extends GenericRepository for common CRUD operations.
 *
 * @author kanishq-egov
 */
@Repository
@Slf4j
public class HFReferralRepository extends GenericRepository<HFReferral> {

    @Autowired
    private HFReferralRowMapper rowMapper;

    /**
     * Constructor for HFReferralRepository.
     *
     * @param producer                       The producer for publishing messages.
     * @param namedParameterJdbcTemplate     JDBC template for named parameters.
     * @param redisTemplate                  Template for Redis operations.
     * @param selectQueryBuilder             Builder for creating SELECT queries.
     * @param rowMapper                      Mapper for converting rows to HFReferral objects.
     */
    @Autowired
    protected HFReferralRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                   RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder,
                                   HFReferralRowMapper rowMapper) {
        // Call the constructor of the GenericRepository with necessary parameters.
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, rowMapper, Optional.of("hf_referral"));
    }

    /**
     * Retrieves a list of HFReferrals based on the provided search criteria.
     *
     * @param searchObject      The search criteria for filtering HFReferrals.
     * @param limit             The maximum number of records to retrieve.
     * @param offset            The offset for pagination.
     * @param tenantId          The tenant ID for filtering.
     * @param lastChangedSince  Timestamp for filtering records changed since this time.
     * @param includeDeleted    Flag indicating whether to include deleted records.
     * @return                  A list of HFReferral entities matching the search criteria.
     */
    public List<HFReferral> find(HFReferralSearch searchObject, Integer limit, Integer offset, String tenantId,
                                 Long lastChangedSince, Boolean includeDeleted) {
        // Initial query to select HFReferral fields from the table.
        String query = "SELECT hf.id, hf.clientreferenceid, hf.tenantid, hf.projectid, hf.projectfacilityid, hf.symptom, hf.symptomsurveyid,  hf.beneficiaryid,  hf.referralcode,  hf.nationallevelid,  hf.createdby,  hf.createdtime,  hf.lastmodifiedby,  hf.lastmodifiedtime,  hf.clientcreatedby,  hf.clientcreatedtime,  hf.clientlastmodifiedby,  hf.clientlastmodifiedtime,  hf.rowversion,  hf.isdeleted,  hf.additionaldetails from hf_referral hf";
        Map<String, Object> paramsMap = new HashMap<>();

        // Generate WHERE conditions based on non-null fields in the search object.
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject,
                QueryFieldChecker.isNotNull, paramsMap);

        // Apply the WHERE conditions to the query.
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "hf.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "hf.clientReferenceId IN (:clientReferenceId)");

        // Add additional conditions based on tenant ID, includeDeleted, and lastChangedSince.
        if(CollectionUtils.isEmpty(whereFields)) {
            query = query + " where hf.tenantId=:tenantId ";
        } else {
            query = query + " and hf.tenantId=:tenantId ";
        }
        if (Boolean.FALSE.equals(includeDeleted)) {
            query = query + "and hf.isDeleted=:isDeleted ";
        }

        if (lastChangedSince != null) {
            query = query + "and hf.lastModifiedTime>=:lastModifiedTime ";
        }

        // Add ORDER BY, LIMIT, and OFFSET clauses to the query.
        query = query + "ORDER BY hf.createdtime DESC LIMIT :limit OFFSET :offset";
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);

        // Execute the query and retrieve the list of HFReferral entities.
        List<HFReferral> hfReferralList = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);
        return hfReferralList;
    }

    /**
     * Retrieves a list of HFReferrals based on a list of IDs.
     *
     * @param ids               The list of IDs to search for.
     * @param includeDeleted    Flag indicating whether to include deleted records.
     * @param columnName        The column name to search for IDs.
     * @return                  A list of HFReferral entities matching the provided IDs.
     */
    public List<HFReferral> findById(List<String> ids, Boolean includeDeleted, String columnName) {
        // Find objects in the cache based on the provided IDs.
        List<HFReferral> objFound = findInCache(ids);
        if (!includeDeleted) {
            objFound = objFound.stream()
                    .filter(entity -> entity.getIsDeleted().equals(false))
                    .collect(Collectors.toList());
        }

        // If objects are found in the cache, check if there are any IDs remaining to be retrieved.
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));

            // If no IDs are remaining, return the objects found in the cache.
            if (ids.isEmpty()) {
                return objFound;
            }
        }

        // Generate a SELECT query based on the provided IDs and column name.
        String query = String.format("SELECT hf.id, hf.clientreferenceid, hf.tenantid, hf.projectid, hf.projectfacilityid, hf.symptom, hf.symptomsurveyid,  hf.beneficiaryid,  hf.referralcode,  hf.nationallevelid,  hf.createdby,  hf.createdtime,  hf.lastmodifiedby,  hf.lastmodifiedtime,  hf.clientcreatedby,  hf.clientcreatedtime,  hf.clientlastmodifiedby,  hf.clientlastmodifiedtime,  hf.rowversion,  hf.isdeleted,  hf.additionaldetails from hf_referral hf WHERE hf.%s IN (:ids) ", columnName);

        // Add conditions to exclude deleted records if includeDeleted is false.
        if (includeDeleted == null || !includeDeleted) {
            query += " AND hf.isDeleted = false ";
        }

        // Create parameter map for the query and execute it to retrieve HFReferral entities.
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        List<HFReferral> hfReferralList = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        // Add the retrieved entities to the cache.
        objFound.addAll(hfReferralList);
        putInCache(objFound);
        return objFound;
    }
}
