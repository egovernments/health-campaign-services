package org.egov.id.service;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.models.idgen.*;
import org.egov.id.repository.IdRepository;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class IdGenerationServiceTest {

    @InjectMocks
    private IdGenerationService idGenerationService;

    @Mock
    private IdRepository idRepository;

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void testGenerateIdResponseWithInvalidInput(IdGenerationRequest request) {
        assertThrows(CustomException.class, () -> idGenerationService.generateIdResponse(request));
    }

    static Stream<IdGenerationRequest> invalidRequests() {
        return Stream.of(
                // Case 1: Null RequestInfo, empty IdRequest
                request(Collections.singletonList(new IdRequest()), null),

                // Case 2: Valid fields, but throws due to format or internal logic
                request(Collections.singletonList(buildRequest("Id Name", "42", "\\[(.*?)\\]", 3)), new RequestInfo()),

                // Case 3: Null tenant ID
                request(Collections.singletonList(buildRequest("Id Name", null, "\\[(.*?)\\]", 3)), new RequestInfo()),

                // Case 4: Null count
                request(Collections.singletonList(buildRequest("Id Name", "42", "\\[(.*?)\\]", null)), new RequestInfo()),

                // Case 5: Null ID name
                request(Collections.singletonList(buildRequest(null, "42", "\\[(.*?)\\]", 3)), new RequestInfo())
        );
    }

    private static IdRequest buildRequest(String idName, String tenantId, String format, Integer count) {
        IdRequest idRequest = new IdRequest(idName, tenantId, format, count);
        idRequest.setFormat(format);
        return idRequest;
    }

    private static IdGenerationRequest request(List<IdRequest> idRequests, RequestInfo requestInfo) {
        IdGenerationRequest req = new IdGenerationRequest();
        req.setIdRequests(idRequests);
        req.setRequestInfo(requestInfo);
        return req;
    }

}

