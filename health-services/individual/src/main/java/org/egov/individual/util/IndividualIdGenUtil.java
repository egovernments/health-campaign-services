package org.egov.individual.util;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.idgen.IdGenerationResponse;
import org.egov.common.contract.idgen.IdResponse;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Individual-local helper for id generation.
 *
 * <p>Unlike the shared {@code IdGenService#getIdList}, which fans out to {@code count} identical
 * {@code IdRequest}s (causing idgen to resolve the id format from MDMS once per id), this util
 * sends a <b>single</b> {@code IdRequest} carrying the {@code count}. idgen then resolves the
 * format from MDMS <b>once</b> and returns {@code count} ids — eliminating {@code count - 1}
 * redundant MDMS lookups for bulk individual creates.</p>
 *
 * <p>The request body is built as a {@link Map} so the {@code count} field can be sent without a
 * change to the shared {@code org.egov.common.contract.idgen.IdRequest} contract (which has no
 * {@code count} field).</p>
 */
@Slf4j
@Component
public class IndividualIdGenUtil {

    private final ServiceRequestClient restRepo;

    private final String idGenHost;

    private final String idGenPath;

    public IndividualIdGenUtil(ServiceRequestClient restRepo,
                               @Value("${egov.idgen.host}") String idGenHost,
                               @Value("${egov.idgen.path}") String idGenPath) {
        this.restRepo = restRepo;
        this.idGenHost = idGenHost;
        this.idGenPath = idGenPath;
    }

    /**
     * Generates {@code count} ids using a single idgen request that carries the count.
     *
     * @param requestInfo the request info to forward to idgen
     * @param tenantId    the tenant for which ids are generated
     * @param idName      the idgen name (e.g. {@code individual.id})
     * @param idFormat    optional explicit format; may be {@code null} to let idgen resolve it from MDMS
     * @param count       the number of ids to generate (typically the size of the incoming individual list)
     * @return the list of generated ids (size == {@code count})
     */
    public List<String> getIdList(RequestInfo requestInfo, String tenantId, String idName,
                                  String idFormat, Integer count) {
        Map<String, Object> idRequest = new HashMap<>();
        idRequest.put("idName", idName);
        idRequest.put("tenantId", tenantId);
        if (idFormat != null) {
            idRequest.put("format", idFormat);
        }
        idRequest.put("count", count);

        Map<String, Object> request = new HashMap<>();
        request.put("RequestInfo", requestInfo);
        request.put("idRequests", Collections.singletonList(idRequest));

        StringBuilder uri = new StringBuilder(idGenHost).append(idGenPath);
        log.info("requesting {} ids from idgen in a single call for idName {}", count, idName);
        IdGenerationResponse response = restRepo.fetchResult(uri, request, IdGenerationResponse.class);

        List<IdResponse> idResponses = response != null ? response.getIdResponses() : null;
        if (CollectionUtils.isEmpty(idResponses)) {
            throw new CustomException("IDGEN_ERROR", "No ids returned from idgen Service");
        }
        return idResponses.stream().map(IdResponse::getId).collect(Collectors.toList());
    }
}
