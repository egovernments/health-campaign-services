package org.egov.servicerequest.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.servicerequest.repository.querybuilder.ServiceQueryBuilder;
import org.egov.servicerequest.repository.rowmapper.ServiceRowMapper;
import org.egov.servicerequest.web.models.Service;
import org.egov.servicerequest.web.models.ServiceCriteria;
import org.egov.servicerequest.web.models.ServiceSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Repository
public class ServiceRequestRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MultiStateInstanceUtil multiStateInstanceUtil;

    @Autowired
    private ServiceRowMapper serviceRowMapper;

    @Autowired
    private ServiceQueryBuilder serviceQueryBuilder;


    public List<Service> getService(ServiceSearchRequest serviceSearchRequest) throws InvalidTenantIdException {
        ServiceCriteria criteria = serviceSearchRequest.getServiceCriteria();

        List<Object> preparedStmtList = new ArrayList<>();

        if(CollectionUtils.isEmpty(criteria.getIds()) && ObjectUtils.isEmpty(criteria.getTenantId()) && CollectionUtils.isEmpty(criteria.getServiceDefIds()) && CollectionUtils.isEmpty(criteria.getReferenceIds()))
            return new ArrayList<>();

        // Fetch ids based on criteria if ids are not present
        if(CollectionUtils.isEmpty(criteria.getIds())){
            // Fetch ids according to given criteria
            String idQuery = serviceQueryBuilder.getServiceIdsQuery(serviceSearchRequest, preparedStmtList);
            idQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(idQuery, criteria.getTenantId());
            log.info("Service ids query: {}", idQuery);
            log.info("Service ids Parameters: {}", preparedStmtList);
            List<String> serviceIds = jdbcTemplate.query(idQuery, preparedStmtList.toArray(), new SingleColumnRowMapper<>(String.class));

            if(CollectionUtils.isEmpty(serviceIds))
                return new ArrayList<>();

            // Set ids in criteria
            criteria.setIds(serviceIds);
            preparedStmtList.clear();
        }


        // Search based on the ids found out/ ids been explicitly provided in the request.
        String query = serviceQueryBuilder.getServiceSearchQuery(criteria, preparedStmtList);
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, criteria.getTenantId());
        log.info("query for search: {} params: {}", query, preparedStmtList);
        return jdbcTemplate.query(query, preparedStmtList.toArray(), serviceRowMapper);
    }
}
