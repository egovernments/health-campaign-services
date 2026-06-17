package org.egov.project.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.http.client.ServiceRequestClient;
import org.egov.common.models.core.Boundary;
import org.egov.project.web.models.boundary.BoundaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BoundaryV2UtilTest {

    @InjectMocks
    private BoundaryV2Util boundaryV2Util;

    @Mock
    private ServiceRequestClient serviceRequestClient;

    private BoundaryResponse boundaryResponse(String... codes) {
        BoundaryResponse response = new BoundaryResponse();
        for (String code : codes) {
            response.addBoundaryItem(Boundary.builder().code(code).build());
        }
        return response;
    }

    @Test
    @DisplayName("Should deduplicate boundary codes in the search URL when multiple projects share a boundary")
    void shouldDeduplicateBoundaryCodesInSearchUrl() {
        ReflectionTestUtils.setField(boundaryV2Util, "boundaryHost", "http://boundary");
        ReflectionTestUtils.setField(boundaryV2Util, "boundarySearchUrl", "/boundary-service/boundary/_search");

        // Two projects share the same boundary "mz"
        Map<String, List<String>> boundaries = Collections.singletonMap("Country", Arrays.asList("mz", "mz"));

        ArgumentCaptor<StringBuilder> uriCaptor = ArgumentCaptor.forClass(StringBuilder.class);
        when(serviceRequestClient.fetchResult(uriCaptor.capture(), any(), eq(BoundaryResponse.class)))
                .thenReturn(boundaryResponse("mz"));

        boundaryV2Util.validateBoundaryDetails(boundaries, "mz", RequestInfo.builder().build(), "ADMIN");

        String uri = uriCaptor.getValue().toString();
        // Duplicate "mz" must collapse to a single code, and limit must reflect the unique count
        assertTrue(uri.contains("codes=mz"), "URL should contain the boundary code");
        assertFalse(uri.contains("codes=mz,mz"), "URL must not contain duplicate boundary codes");
        assertTrue(uri.contains("limit=1"), "limit should equal the unique code count");
    }

    @Test
    @DisplayName("Should not throw when all (deduplicated) boundary codes are valid")
    void shouldPassWhenAllBoundaryCodesAreValid() {
        ReflectionTestUtils.setField(boundaryV2Util, "boundaryHost", "http://boundary");
        ReflectionTestUtils.setField(boundaryV2Util, "boundarySearchUrl", "/boundary-service/boundary/_search");

        Map<String, List<String>> boundaries = Collections.singletonMap("Country", Arrays.asList("mz", "mz"));

        when(serviceRequestClient.fetchResult(any(), any(), eq(BoundaryResponse.class)))
                .thenReturn(boundaryResponse("mz"));

        // Should complete without throwing
        boundaryV2Util.validateBoundaryDetails(boundaries, "mz", RequestInfo.builder().build(), "ADMIN");
    }
}
