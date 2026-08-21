package org.egov.common.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.idgen.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Service to interact with the Beneficiary ID Generation system.
 * This service is conditionally enabled based on the property:
 * egov.beneficiary.idgen.integration.enabled=true
 */
@Service
@ConditionalOnProperty(name = "egov.beneficiary.idgen.integration.enabled", havingValue = "true")
public class BeneficiaryIdGenService {

    private final String healthIdGenHost;
    private final String healthIdGenSearchPath;
    private final String healthIdGenUpdatePath;
    private final ServiceRequestClient restRepo;

    /**
     * Constructor to inject dependencies and configuration values.
     *
     * @param restRepo               Client used for making REST service calls
     * @param healthIdGenHost        Base URL for the ID generation service
     * @param healthIdGenSearchPath  Endpoint path for ID pool search
     * @param healthIdGenUpdatePath  Endpoint path for ID pool update
     */
    @Autowired
    public BeneficiaryIdGenService(ServiceRequestClient restRepo,
                                   @Value("${egov.beneficiary.idgen.host}") String healthIdGenHost,
                                   @Value("${egov.beneficiary.idgen.idpool.search.path:beneficiary-idgen/id/id_pool/_search}") String healthIdGenSearchPath,
                                   @Value("${egov.beneficiary.idgen.idpool.update.path:beneficiary-idgen/id/id_pool/_update}") String healthIdGenUpdatePath) {
        this.restRepo = restRepo;
        this.healthIdGenHost = healthIdGenHost;
        this.healthIdGenSearchPath = healthIdGenSearchPath;
        this.healthIdGenUpdatePath = healthIdGenUpdatePath;
    }

    /**
     * Searches for existing ID records from the ID generation service.
     *
     * @param ids         List of ID values to search for
     * @param status      (Optional) Status filter for the ID records
     * @param tenantId    Tenant identifier
     * @param requestInfo Metadata about the incoming request
     * @return            Response containing matching ID records
     */
    public IdDispatchResponse searchIdRecord(List<String> ids, String status, String tenantId, RequestInfo requestInfo) {
        IdStatus idStatus = null;
        if (!StringUtils.isEmpty(status)) {
            idStatus = IdStatus.valueOf(status);
        }

        // Construct search request payload
        IdPoolSearchRequest searchRequest = IdPoolSearchRequest.builder()
                .requestInfo(requestInfo)
                .idPoolSearch(IdPoolSearch.builder()
                        .idList(ids)
                        .status(idStatus)
                        .tenantId(tenantId)
                        .build())
                .build();

        // Build the complete URI and make the REST call
        StringBuilder uri = new StringBuilder(healthIdGenHost).append(healthIdGenSearchPath);
        IdDispatchResponse response = restRepo.fetchResult(uri, searchRequest, IdDispatchResponse.class);
        return response;
    }

    /**
     * Updates ID records in the ID generation system.
     *
     * @param idRecords   List of ID records to update
     * @param requestInfo Metadata about the incoming request
     * @return            Response info indicating the result of the update
     */
    public ResponseInfo updateIdRecord(List<IdRecord> idRecords, RequestInfo requestInfo) {
        // Construct update request payload
        IdRecordBulkRequest updateRequest = IdRecordBulkRequest.builder()
                .requestInfo(requestInfo)
                .idRecords(idRecords)
                .build();

        // Build the complete URI and make the REST call
        StringBuilder uri = new StringBuilder(healthIdGenHost).append(healthIdGenUpdatePath);
        return restRepo.fetchResult(uri, updateRequest, ResponseInfo.class);
    }
}
