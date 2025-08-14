package org.egov.servicerequest.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.exception.InvalidTenantIdException;
import org.egov.common.utils.MultiStateInstanceUtil;
import org.egov.servicerequest.repository.querybuilder.ServiceDefinitionQueryBuilder;
import org.egov.servicerequest.repository.rowmapper.ServiceDefinitionRowMapper;
import org.egov.servicerequest.web.models.Service;
import org.egov.servicerequest.web.models.ServiceCriteria;
import org.egov.servicerequest.web.models.ServiceDefinition;
import org.egov.servicerequest.web.models.ServiceDefinitionCriteria;
import org.egov.servicerequest.web.models.ServiceDefinitionSearchRequest;
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
public class ServiceDefinitionRequestRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MultiStateInstanceUtil multiStateInstanceUtil;

    @Autowired
    private ServiceDefinitionRowMapper serviceDefinitionRowMapper;

    @Autowired
    private ServiceDefinitionQueryBuilder serviceDefinitionQueryBuilder;


    public List<ServiceDefinition> getServiceDefinitions(ServiceDefinitionSearchRequest serviceDefinitionSearchRequest) throws InvalidTenantIdException {
        ServiceDefinitionCriteria criteria = serviceDefinitionSearchRequest.getServiceDefinitionCriteria();

        List<Object> preparedStmtList = new ArrayList<>();

        if(CollectionUtils.isEmpty(criteria.getIds()) && ObjectUtils.isEmpty(criteria.getTenantId()) && CollectionUtils.isEmpty(criteria.getCode()))
            return new ArrayList<>();

        // Fetch ids based on criteria if ids are not present
        if(CollectionUtils.isEmpty(criteria.getIds())){
            // Fetch ids according to given criteria
            String idQuery = serviceDefinitionQueryBuilder.getServiceDefinitionsIdsQuery(serviceDefinitionSearchRequest, preparedStmtList);
            // Replacing schema placeholder with the schema name for the tenant id
            idQuery = multiStateInstanceUtil.replaceSchemaPlaceholder(idQuery, criteria.getTenantId());
            log.info("Service definition ids query: {}", idQuery);
            log.info("Parameters: {}", preparedStmtList);
            List<String> serviceDefinitionIds = jdbcTemplate.query(idQuery, preparedStmtList.toArray(), new SingleColumnRowMapper<>(String.class));


            if(CollectionUtils.isEmpty(serviceDefinitionIds))
                return new ArrayList<>();

            // Set ids in criteria
            criteria.setIds(serviceDefinitionIds);
            preparedStmtList.clear();
        }



        String query = serviceDefinitionQueryBuilder.getServiceDefinitionSearchQuery(criteria, preparedStmtList);
        // Replacing schema placeholder with the schema name for the tenant id
        query = multiStateInstanceUtil.replaceSchemaPlaceholder(query, criteria.getTenantId());
        log.info("query for search: {} params: {}", query, preparedStmtList);
        return jdbcTemplate.query(query, preparedStmtList.toArray(), serviceDefinitionRowMapper);

    }

    public List<Service> getService(ServiceCriteria criteria) {
        return new ArrayList<>();
    }
}
