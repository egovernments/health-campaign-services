package org.egov.servicerequest.repository;

import lombok.extern.slf4j.Slf4j;
import org.egov.servicerequest.repository.querybuilder.ServiceDefinitionQueryBuilder;
import org.egov.servicerequest.repository.rowmapper.ServiceDefinitionRowMapper;
import org.egov.servicerequest.web.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class ServiceDefinitionRequestRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ServiceDefinitionRowMapper serviceDefinitionRowMapper;

    @Autowired
    private ServiceDefinitionQueryBuilder serviceDefinitionQueryBuilder;


    public List<ServiceDefinition> getServiceDefinitions(ServiceDefinitionSearchRequest serviceDefinitionSearchRequest) {
        ServiceDefinitionCriteria criteria = serviceDefinitionSearchRequest.getServiceDefinitionCriteria();

        List<Object> preparedStmtList = new ArrayList<>();

        if(CollectionUtils.isEmpty(criteria.getIds()) && ObjectUtils.isEmpty(criteria.getTenantId()) && CollectionUtils.isEmpty(criteria.getCode()))
            return new ArrayList<>();

        // Fetch ids based on criteria if ids are not present
        if(CollectionUtils.isEmpty(criteria.getIds())){
            // Fetch ids according to given criteria
            String idQuery = serviceDefinitionQueryBuilder.getServiceDefinitionsIdsQuery(serviceDefinitionSearchRequest, preparedStmtList);
            log.info("Service definition ids query: " + idQuery);
            log.info("Parameters: " + preparedStmtList.toString());
            List<String> serviceDefinitionIds = jdbcTemplate.query(idQuery, preparedStmtList.toArray(), new SingleColumnRowMapper<>(String.class));

            if(CollectionUtils.isEmpty(serviceDefinitionIds))
                return new ArrayList<>();

            // Set ids in criteria
            criteria.setIds(serviceDefinitionIds);
            preparedStmtList.clear();
        }

        String query = serviceDefinitionQueryBuilder.getServiceDefinitionSearchQuery(criteria, preparedStmtList);
        log.info("query for search: " + query + " params: " + preparedStmtList);
        List<ServiceDefinition> result = jdbcTemplate.query(query, preparedStmtList.toArray(), serviceDefinitionRowMapper);

        List<ServiceDefinition> activeServiceDefinitions = result;

        if(!serviceDefinitionSearchRequest.getServiceDefinitionCriteria().isIncludeDeleted()) {
            activeServiceDefinitions = result.stream()
                    .filter(ServiceDefinition::getIsActive) // Filter ServiceDefinitions where isActive is true
                    .map(service -> {
                        // Filter active attributes within each ServiceDefinition
                        List<AttributeDefinition> activeAttributes = service.getAttributes().stream()
                                .filter(attribute -> Boolean.TRUE.equals(attribute.getIsActive())) // Filter attributes by isActive
                                .collect(Collectors.toList());

                        // Create a copy of the ServiceDefinition with filtered attributes
                        service.setAttributes(activeAttributes);

                        return service;
                    })
                    .filter(service -> !service.getAttributes().isEmpty()) // Retain ServiceDefinitions with active attributes only
                    .collect(Collectors.toList());
        }

        return activeServiceDefinitions;
    }

    public List<Service> getService(ServiceCriteria criteria) {
        return new ArrayList<>();
    }
}