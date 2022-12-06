package org.egov.common.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.IdGenerationRequest;
import org.egov.common.models.IdGenerationResponse;
import org.egov.common.models.IdRequest;
import org.egov.common.models.IdResponse;
import org.egov.common.http.client.ServiceRequestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty({ "egov.idgen.host", "egov.idgen.path" })
public class IdGenService {

    private final String idGenHost;

    private final String idGenPath;

    private final ServiceRequestClient restRepo;

    @Autowired
    public IdGenService(ServiceRequestClient restRepo,
                        @Value("${egov.idgen.host}") String idGenHost,
                        @Value("${egov.idgen.path}") String idGenPath) {
        this.restRepo = restRepo;
        this.idGenHost = idGenHost;
        this.idGenPath = idGenPath;
    }

    public List<String> getIdList(RequestInfo requestInfo, String tenantId, String idName, String idFormat, Integer count) {
        List<IdRequest> reqList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            reqList.add(IdRequest.builder().idName(idName).format(idFormat).tenantId(tenantId).build());
        }

        IdGenerationRequest request = IdGenerationRequest.builder().idRequests(reqList).requestInfo(requestInfo).build();
        StringBuilder uri = new StringBuilder(idGenHost).append(idGenPath);
        IdGenerationResponse response = restRepo.fetchResult(uri, request, IdGenerationResponse.class);

        List<IdResponse> idResponses = response.getIdResponses();

        if (CollectionUtils.isEmpty(idResponses))
            throw new RuntimeException("IDGEN ERROR - No ids returned from idgen Service");

        return idResponses.stream().map(IdResponse::getId).collect(Collectors.toList());
    }
}