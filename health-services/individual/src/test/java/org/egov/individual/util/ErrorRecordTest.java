package org.egov.individual.util;

import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.Name;
import org.egov.common.utils.CommonUtils;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test data for individual's error-record tests. The rule itself lives in health-services-common CommonUtils. */
public class ErrorRecordTest {

    public static final String TOKEN = "test-auth-token-0001";
    public static final String CALLER_MOBILE = "9000000099";
    public static final String GIVEN_NAME = "TestGivenName";
    public static final String MOBILE = "9000000001";
    public static final String AADHAAR = "999911112222";
    public static final List<String> SECRETS = List.of(TOKEN, CALLER_MOBILE, GIVEN_NAME, MOBILE, AADHAAR);

    public static RequestInfo requestInfo() {
        return RequestInfo.builder().apiId("/individual/v1/bulk/_create").msgId("msg-1").authToken(TOKEN)
                .userInfo(User.builder().uuid("caller-uuid").userName("test-caller").mobileNumber(CALLER_MOBILE).build())
                .build();
    }

    public static Individual individual() {
        return Individual.builder().id("ind-1").clientReferenceId("cref-1").tenantId("test-tenant")
                .individualId("IND-0001")
                .name(Name.builder().givenName(GIVEN_NAME).build())
                .mobileNumber(MOBILE)
                .identifiers(new ArrayList<>(List.of(Identifier.builder().identifierType("AADHAAR").identifierId(AADHAAR).build())))
                .build();
    }

    /** Builds the error record exactly as IndividualService does (shared populateErrorDetails). */
    static Map<Individual, ErrorDetails> errorRecord(Individual individual, RequestInfo requestInfo) {
        IndividualBulkRequest request = IndividualBulkRequest.builder().requestInfo(requestInfo)
                .individuals(List.of(individual)).build();
        Map<Individual, ErrorDetails> map = new HashMap<>();
        CommonUtils.populateErrorDetails(request, map, List.of(individual),
                new CustomException("ENCRYPTION_SERVICE_ERROR", "Error occurred while calling Encryption Service"),
                "setIndividuals");
        return map;
    }

    @Test
    void errorRecordKeepsIdsOnly() {
        IndividualBulkRequest request = IndividualBulkRequest.builder().requestInfo(requestInfo())
                .individuals(List.of(individual())).build();
        String plain = request.toString();
        // the check below can only pass if it is able to see these values
        SECRETS.forEach(s -> assertTrue(plain.contains(s), "expected in the request: " + s));

        Map<Individual, ErrorDetails> map = errorRecord(individual(), requestInfo());

        String body = map.values().iterator().next().getApiDetails().getRequestBody();
        String asMessage = map.values().toString(); // what a non-bulk error puts in the exception message
        for (String s : SECRETS) {
            assertFalse(body.contains(s), "leaked in requestBody: " + s);
            assertFalse(asMessage.contains(s), "leaked in message text: " + s);
        }
        assertFalse(body.contains("userInfo"));
        assertTrue(body.contains("\"clientReferenceId\":\"cref-1\""));
        assertTrue(body.contains("\"id\":\"ind-1\""));
        assertTrue(body.contains("\"individualId\":\"IND-0001\""));
        assertTrue(body.contains("\"msgId\":\"msg-1\""));
        assertTrue(body.contains("\"Individuals\""));
        assertTrue(asMessage.contains("ENCRYPTION_SERVICE_ERROR"), "error code must stay");
    }
}
