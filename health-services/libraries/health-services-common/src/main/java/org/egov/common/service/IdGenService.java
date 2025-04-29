package org.egov.common.service;

import org.egov.common.contract.idgen.IdGenerationRequest;
import org.egov.common.contract.idgen.IdGenerationResponse;
import org.egov.common.contract.idgen.IdRequest;
import org.egov.common.contract.idgen.IdResponse;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.response.ResponseInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.idgen.*;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@ConditionalOnExpression("!'${egov.idgen.integration.enabled}'.isEmpty() && ${egov.idgen.integration.enabled:false} && !'${egov.idgen.host}'.isEmpty() && !'${egov.idgen.path}'.isEmpty()")
public class IdGenService {

    private final String idGenHost;

    private final String idGenPath;

    private final String idGenSearchPath;

    private final String idGenUpdatePath;

    private final ServiceRequestClient restRepo;

    @Autowired
    public IdGenService(ServiceRequestClient restRepo,
                        @Value("${egov.idgen.host}") String idGenHost,
                        @Value("${egov.idgen.path}") String idGenPath,
                        @Value("${egov.idgen.idpool.search.path:health-idgen/id/id_pool/_search}") String idGenSearchPath,
                        @Value("${egov.idgen.idpool.update.path:health-idgen/id/id_pool/_update}") String idGenUpdatePath) {
        this.restRepo = restRepo;
        this.idGenHost = idGenHost;
        this.idGenPath = idGenPath;
        this.idGenSearchPath = idGenSearchPath;
        this.idGenUpdatePath = idGenUpdatePath;
    }

    public List<String> getIdList(RequestInfo requestInfo, String tenantId, String idName,
                                  String idFormat, Integer count)  {
        List<IdRequest> reqList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            reqList.add(IdRequest.builder().idName(idName).format(idFormat).tenantId(tenantId).build());
        }

        IdGenerationRequest request = IdGenerationRequest.builder().idRequests(reqList).requestInfo(requestInfo).build();
        StringBuilder uri = new StringBuilder(idGenHost).append(idGenPath);
        IdGenerationResponse response = restRepo.fetchResult(uri, request, IdGenerationResponse.class);

        List<IdResponse> idResponses = response.getIdResponses();

        if (CollectionUtils.isEmpty(idResponses))
            throw new CustomException("IDGEN_ERROR", "No ids returned from idgen Service");

        return idResponses.stream().map(IdResponse::getId).collect(Collectors.toList());
    }

    public IdDispatchResponse searchIdRecord(List<String> ids, String status, String tenantId, RequestInfo requestInfo) {

        IdStatus idStatus = null;
        if (!StringUtils.isEmpty(status)) idStatus = IdStatus.valueOf(status);
        IdPoolSearchRequest searchRequest = IdPoolSearchRequest.builder()
                .requestInfo(requestInfo)
                .idPoolSearch(IdPoolSearch.builder()
                        .idList(ids)
                        .status(idStatus)
                        .tenantId(tenantId)
                        .build())
                .build();
        StringBuilder uri = new StringBuilder(idGenHost).append(idGenSearchPath);
        IdDispatchResponse response  = restRepo.fetchResult(uri, searchRequest, IdDispatchResponse.class);
        return response;
    }


    public ResponseInfo updateIdRecord(List<IdRecord> idRecords, RequestInfo requestInfo) {
        IdRecordBulkRequest updateRequest = IdRecordBulkRequest.builder()
                .requestInfo(requestInfo)
                .idRecords(idRecords)
                .build();

        StringBuilder uri = new StringBuilder(idGenHost).append(idGenUpdatePath);
        return restRepo.fetchResult(uri, updateRequest, ResponseInfo.class);
    }
}