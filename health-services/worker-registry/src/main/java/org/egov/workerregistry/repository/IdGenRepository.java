package org.egov.workerregistry.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.idgen.IdGenerationRequest;
import org.egov.common.models.idgen.IdRequest;
import org.egov.common.models.idgen.IdGenerationResponse;
import org.egov.common.models.idgen.IdResponse;
import org.egov.tracer.model.CustomException;
import org.egov.workerregistry.config.WorkerRegistryConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class IdGenRepository {

    private final ServiceRequestRepository serviceRequestRepository;
    private final WorkerRegistryConfiguration config;
    private final ObjectMapper objectMapper;


    @Autowired
    public IdGenRepository(ServiceRequestRepository serviceRequestRepository, WorkerRegistryConfiguration config, ObjectMapper objectMapper) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public List<String> getId(Object request, String tenantId, String idKey, String idformat, int count) {
        IdRequest idRequest = new IdRequest(idKey, tenantId, idformat, count);
        List<IdRequest> idRequests = new ArrayList<>();
        idRequests.add(idRequest);
        IdGenerationRequest idGenerationRequest = new IdGenerationRequest();
        idGenerationRequest.setRequestInfo(objectMapper.convertValue(request, RequestInfo.class));
        idGenerationRequest.setIdRequests(idRequests);
        StringBuilder uri = new StringBuilder(config.getIdgenHost()).append(config.getIdgenPath());

        IdGenerationResponse response = objectMapper.convertValue(serviceRequestRepository.fetchResult(uri, idGenerationRequest), IdGenerationResponse.class);

        List<String> idResponses = Optional.ofNullable(response.getIdResponses()).orElse(Collections.emptyList())
                .stream().map(IdResponse::getId).collect(Collectors.toList());
        if (idResponses.isEmpty()) {
            throw new CustomException("IDGEN_ERROR", "No ids returned from idgen Service");
        }
        return idResponses;
    }
}
