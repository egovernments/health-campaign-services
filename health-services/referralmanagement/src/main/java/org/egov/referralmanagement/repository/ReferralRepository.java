package org.egov.referralmanagement.repository;

import static org.egov.common.utils.CommonUtils.getIdMethod;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.ReferralSearch;
import org.egov.common.producer.Producer;
import org.egov.referralmanagement.repository.rowmapper.ReferralRowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.ReflectionUtils;

import lombok.extern.slf4j.Slf4j;

@Repository
@Slf4j
public class ReferralRepository extends GenericRepository<Referral> {
    @Autowired
    private ReferralRowMapper rowMapper;

    @Autowired
    protected ReferralRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                 RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder,
                                 ReferralRowMapper rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, rowMapper, Optional.of("referral"));
    }

    public List<Referral> find(ReferralSearch searchObject, Integer limit, Integer offset, String tenantId,
                               Long lastChangedSince, Boolean includeDeleted) {
      
        String query = "SELECT r.id, r.clientreferenceid, r.tenantid, r.projectbeneficiaryid, r.projectbeneficiaryclientreferenceid, r.referrerid, r.recipientid, r.recipienttype, r.reasons, r.sideeffectid, r.sideeffectclientreferenceid, r.createdby, r.createdtime, r.lastmodifiedby, r.lastmodifiedtime, r.clientcreatedby, r.clientcreatedtime, r.clientlastmodifiedby, r.clientlastmodifiedtime, r.rowversion, r.isdeleted, r.additionaldetails, se.id sId, se.clientreferenceid sClientReferenceId, se.tenantid sTenantId, se.taskid sTaskId, se.taskclientreferenceid sTaskClientReferenceId, se.projectbeneficiaryId sProjectBeneficiaryId, se.projectBeneficiaryClientReferenceId sProjectBeneficiaryClientReferenceId, se.symptoms sSymptoms, se.additionalDetails sAdditionalDetails, se.createdby sCreatedBy, se.createdtime sCreatedTime, se.lastmodifiedby sLastModifiedBy, se.lastmodifiedtime sLastModifiedTime, se.clientCreatedBy sClientCreatedBy, se.clientcreatedtime sClientCreatedTime, se.clientlastmodifiedby sClientLastModifiedBy, se.clientlastmodifiedtime sClientLastModifiedTime, se.rowversion sRowVersion, se.isdeleted sIsDeleted FROM referral r left join side_effect se on r.sideEffectClientReferenceid = se.clientreferenceid";
        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject,
                QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "r.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "r.clientReferenceId IN (:clientReferenceId)");
        query = query.replace("projectBeneficiaryClientReferenceId IN (:projectBeneficiaryClientReferenceId)", "r.projectBeneficiaryClientReferenceId IN (:projectBeneficiaryClientReferenceId)");
        query = query.replace("projectBeneficiaryId IN (:projectBeneficiaryId)", "r.projectBeneficiaryId IN (:projectBeneficiaryId)");
        
        query = query + " and r.tenantId=:tenantId ";
        if (Boolean.FALSE.equals(includeDeleted)) {
            query = query + "and r.isDeleted=:isDeleted ";
        }

        if (lastChangedSince != null) {
            query = query + "and r.lastModifiedTime>=:lastModifiedTime ";
        }
        query = query + "ORDER BY r.id ASC LIMIT :limit OFFSET :offset";
        paramsMap.put("tenantId", tenantId);
        paramsMap.put("isDeleted", includeDeleted);
        paramsMap.put("lastModifiedTime", lastChangedSince);
        paramsMap.put("limit", limit);
        paramsMap.put("offset", offset);
        List<Referral> referralList = this.namedParameterJdbcTemplate.query(query, paramsMap, this.rowMapper);
        return referralList;
    }

    public List<Referral> findById(List<String> ids, Boolean includeDeleted, String columnName) {
        List<Referral> objFound = findInCache(ids).stream()
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

        String query = String.format("SELECT r.id, r.clientreferenceid, r.tenantid, r.projectbeneficiaryid, r.projectbeneficiaryclientreferenceid, r.referrerid, r.recipientid, r.recipienttype, r.reasons, r.sideeffectid, r.sideeffectclientreferenceid, r.createdby, r.createdtime, r.lastmodifiedby, r.lastmodifiedtime, r.clientcreatedby, r.clientcreatedtime, r.clientlastmodifiedby, r.clientlastmodifiedtime, r.rowversion, r.isdeleted, r.additionaldetails, se.id sId, se.clientreferenceid sClientReferenceId, se.tenantid sTenantId, se.taskid sTaskId, se.taskclientreferenceid sTaskClientReferenceId, se.projectbeneficiaryId sProjectBeneficiaryId, se.projectBeneficiaryClientReferenceId sProjectBeneficiaryClientReferenceId, se.symptoms sSymptoms, se.additionalDetails sAdditionalDetails, se.createdby sCreatedBy, se.createdtime sCreatedTime, se.lastmodifiedby sLastModifiedBy, se.lastmodifiedtime sLastModifiedTime, se.clientCreatedBy sClientCreatedBy, se.clientcreatedtime sClientCreatedTime, se.clientlastmodifiedby sClientLastModifiedBy, se.clientlastmodifiedtime sClientLastModifiedTime, se.rowversion sRowVersion, se.isdeleted sIsDeleted FROM referral r left join side_effect se on r.sideEffectClientReferenceid = se.clientreferenceid WHERE r.%s IN (:ids) ", columnName);
        if (includeDeleted == null || !includeDeleted) {
            query += " AND r.isDeleted = false ";
        }
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("ids", ids);
        List<Referral> referralList = this.namedParameterJdbcTemplate.query(query, paramMap, this.rowMapper);

        objFound.addAll(referralList);
        putInCache(objFound);
        return objFound;
    }
}
