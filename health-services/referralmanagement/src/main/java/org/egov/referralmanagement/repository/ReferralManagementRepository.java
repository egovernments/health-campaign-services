package org.egov.referralmanagement.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.adrm.repository.rowmapper.ReferralRowMapper;
import org.egov.common.data.query.builder.GenericQueryBuilder;
import org.egov.common.data.query.builder.QueryFieldChecker;
import org.egov.common.data.query.builder.SelectQueryBuilder;
import org.egov.common.data.query.exception.QueryBuilderException;
import org.egov.common.data.repository.GenericRepository;
import org.egov.common.models.adrm.referralmanagement.Referral;
import org.egov.common.models.adrm.referralmanagement.ReferralSearch;
import org.egov.common.models.project.ProjectBeneficiary;
import org.egov.common.models.project.Task;
import org.egov.common.models.project.TaskResource;
import org.egov.common.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.egov.common.utils.CommonUtils.getIdMethod;

@Repository
@Slf4j
public class ReferralManagementRepository extends GenericRepository<Referral> {
    @Autowired
    private ReferralRowMapper rowMapper;

    @Autowired
    protected ReferralManagementRepository(Producer producer, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                 RedisTemplate<String, Object> redisTemplate, SelectQueryBuilder selectQueryBuilder,
                                 ReferralRowMapper rowMapper) {
        super(producer, namedParameterJdbcTemplate, redisTemplate, selectQueryBuilder, rowMapper, Optional.of("adverse_event"));
    }

    public List<Referral> find(ReferralSearch searchObject, Integer limit, Integer offset, String tenantId,
                               Long lastChangedSince, Boolean includeDeleted) throws QueryBuilderException {
        String query = "SELECT r.*, ae.id, ae.clientreferenceid, ae.tenantid, ae.taskid, ae.taskclientreferenceid, ae.symptoms, ae.reattempts, ae.createdby, ae.createdtime, ae.lastmodifiedby, ae.lastmodifiedtime, ae.clientcreatedtime, ae.clientlastmodifiedtime, ae.rowversion, ae.isdeleted FROM referral r left join adverse_event ae on r.adverseEventclientReferenceid = ae.clientreferenceid";
        Map<String, Object> paramsMap = new HashMap<>();
        List<String> whereFields = GenericQueryBuilder.getFieldsWithCondition(searchObject,
                QueryFieldChecker.isNotNull, paramsMap);
        query = GenericQueryBuilder.generateQuery(query, whereFields).toString();
        query = query.replace("id IN (:id)", "r.id IN (:id)");
        query = query.replace("clientReferenceId IN (:clientReferenceId)", "r.clientReferenceId IN (:clientReferenceId)");

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

    public List<Referral> findById(List<String> ids, String columnName, Boolean includeDeleted) {
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

        String query = String.format("SELECT r.*, ae.id, ae.clientreferenceid, ae.tenantid, ae.taskid, ae.taskclientreferenceid, ae.symptoms, ae.reattempts, ae.createdby, ae.createdtime, ae.lastmodifiedby, ae.lastmodifiedtime, ae.clientcreatedtime, ae.clientlastmodifiedtime, ae.rowversion, ae.isdeleted FROM referral r left join adverse_event ae on r.adverseEventclientReferenceid = ae.clientreferenceid WHERE r.%s IN (:ids) ", columnName);
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
