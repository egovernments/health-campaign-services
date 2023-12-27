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
import org.springframework.util.ReflectionUtils;

import static org.egov.common.utils.CommonUtils.getIdMethod;

@Repository
@Slf4j
public class HFReferralRepository extends GenericRepository<HFReferral> {
    @Autowired
    private HFReferralRowMapper rowMapper;

    @Autowired
    protected HFReferralRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                   RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder,
                                   HFReferralRowMapper rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, rowMapper, Optional.of("hf_referral"));
    }

    public List<HFReferral> find(HFReferralSearch searchObject, Integer limit, Integer offset, String tenantId,
                                 Long lastChangedSince, Boolean includeDeleted) {
      
        String query = "SELECT hf.id, hf.clientreferenceid, hf.tenantid, hf.projectid, hf.facilityid, hf.symptom, hf.symptomsurveyid,  hf.beneficiaryid,  hf.referralcode,  hf.nationallevelid,  hf.createdby,  hf.createdtime,  hf.lastmodifiedby,  hf.lastmodifiedtime,  hf.clientcreatedby,  hf.clientcreatedtime,  hf.clientlastmodifiedby,  hf.clientlastmodifiedtime,  hf.rowversion,  hf.isdeleted,  hf.additionaldetails from hf_referral hf";
        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject,
                QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "hf.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "hf.clientReferenceId IN (:clientReferenceId)");

        query = query + " and hf.tenantId=:tenantId ";
        if (Boolean.FALSE.equals(includeDeleted)) {
            query = query + "and hf.isDeleted=:isDeleted ";
        }

        if (lastChangedSince != null) {
            query = query + "and hf.lastModifiedTime>=:lastModifiedTime ";
        }
        query = query + "ORDER BY hf.id ASC LIMIT :limit OFFSET :offset";
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        List<HFReferral> hfReferralList = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);
        return hfReferralList;
    }

    public List<HFReferral> findById(List<String> ids, Boolean includeDeleted, String columnName) {
        List<HFReferral> objFound = findInCache(ids).stream()
                .filter(entity -> entity.getIsDeleted().equals(includeDeleted))
                .collect(Collectors.toList());
        if (!objFound.isEmpty()) {
            Method idMethod = getIdMethod(objFound, columnName);
            ids.removeAll(objFound.stream()
                    .map(obj -> (String) ReflectionUtils.invokeMethod(idMethod, obj))
                    .collect(Collectors.toList()));
            if (ids.isEmpty()) {
                return objFound;
            }
        }

        String query = String.format("SELECT hf.id, hf.clientreferenceid, hf.tenantid, hf.projectid, hf.facilityid, hf.symptom, hf.symptomsurveyid,  hf.beneficiaryid,  hf.referralcode,  hf.nationallevelid,  hf.createdby,  hf.createdtime,  hf.lastmodifiedby,  hf.lastmodifiedtime,  hf.clientcreatedby,  hf.clientcreatedtime,  hf.clientlastmodifiedby,  hf.clientlastmodifiedtime,  hf.rowversion,  hf.isdeleted,  hf.additionaldetails from hf_referral hf WHERE hf.%s IN (:ids) ", columnName);
        if (includeDeleted == null || !includeDeleted) {
            query += " AND hf.isDeleted = false ";
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        List<HFReferral> hfReferralList = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        objFound.addAll(hfReferralList);
        putInCache(objFound);
        return objFound;
    }
}
