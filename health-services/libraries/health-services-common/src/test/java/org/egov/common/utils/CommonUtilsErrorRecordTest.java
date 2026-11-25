package org.egov.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.common.models.ErrorDetails;
import org.egov.common.models.household.Household;
import org.egov.common.models.household.HouseholdBulkRequest;
import org.egov.common.models.individual.Identifier;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.IndividualBulkRequest;
import org.egov.common.models.individual.Name;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferralBulkRequest;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Error records go to the error topic and the error index. They must keep only IDs: no personal data,
 * no auth token, no caller details. Each test first checks that the request really holds the values
 * (so the check can find them), then checks that the error record does not.
 */
class CommonUtilsErrorRecordTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN = "test-auth-token-0001";
    private static final List<String> CALLER_SECRETS = Arrays.asList(
            TOKEN, "test-caller-0001", "TestCallerName", "9000000099", "caller@example.org");

    private static RequestInfo requestInfo() {
        return RequestInfo.builder().apiId("/test/v1/bulk/_create").msgId("test-msg-0001").authToken(TOKEN)
                .userInfo(User.builder().uuid("caller-uuid-0001").userName("test-caller-0001")
                        .name("TestCallerName").mobileNumber("9000000099").emailId("caller@example.org").build())
                .build();
    }

    private static <T> ErrorDetails errorRecord(Object request, T payload, String setter) {
        Map<T, ErrorDetails> map = new HashMap<>();
        CommonUtils.populateErrorDetails(request, map, Collections.singletonList(payload),
                new CustomException("SOME_ERROR", "some message"), setter);
        return map.get(payload);
    }

    private static void assertOnlyIds(Object request, ErrorDetails record, List<String> secrets,
                                      String clientReferenceId) throws Exception {
        String before = MAPPER.writeValueAsString(request);
        for (String s : secrets) {
            assertTrue(before.contains(s), "test setup: request should contain " + s);
        }
        String body = record.getApiDetails().getRequestBody();
        for (String s : secrets) {
            assertFalse(body.contains(s), "error record still contains " + s + ": " + body);
        }
        assertTrue(body.contains(clientReferenceId), body);
        assertTrue(body.contains("test-msg-0001"), body);
        assertEquals("SOME_ERROR", record.getErrors().get(0).getErrorCode());
        assertEquals("some message", record.getErrors().get(0).getErrorMessage());
    }

    @Test
    @DisplayName("individual: error record keeps IDs only, and the single-call error message has no personal data")
    void individualErrorRecordKeepsIdsOnly() throws Exception {
        Individual individual = Individual.builder().id("ind-1").clientReferenceId("ind-cref-1")
                .individualId("IND-0001").tenantId("test-tenant")
                .name(Name.builder().givenName("TestGivenName").familyName("TestFamilyName").build())
                .mobileNumber("9000000001").email("person@example.org")
                .identifiers(Collections.singletonList(Identifier.builder().identifierType("AADHAAR")
                        .identifierId("999911112222").build()))
                .address(Collections.singletonList(org.egov.common.models.individual.Address.builder()
                        .doorNo("D-TEST-1").addressLine1("Test line one 0001").build()))
                .build();
        IndividualBulkRequest request = new IndividualBulkRequest();
        request.setRequestInfo(requestInfo());
        request.setIndividuals(Collections.singletonList(individual));
        List<String> secrets = new java.util.ArrayList<>(CALLER_SECRETS);
        secrets.addAll(Arrays.asList("TestGivenName", "TestFamilyName", "9000000001", "person@example.org",
                "999911112222", "D-TEST-1", "Test line one 0001"));

        ErrorDetails record = errorRecord(request, individual, "setIndividuals");
        assertOnlyIds(request, record, secrets, "ind-cref-1");
        assertTrue(record.getApiDetails().getRequestBody().contains("IND-0001"));

        // a single (non-bulk) call puts the error records into the exception message, which the tracer logs
        Map<Individual, ErrorDetails> map = new HashMap<>();
        map.put(individual, record);
        CustomException thrown = assertThrows(CustomException.class,
                () -> CommonUtils.handleErrors(map, false, "VALIDATION_ERROR"));
        for (String s : secrets) {
            assertFalse(thrown.getMessage().contains(s), "exception message still contains " + s);
        }
    }

    @Test
    @DisplayName("household: error record drops the address and GPS")
    void householdErrorRecordKeepsIdsOnly() throws Exception {
        Household household = Household.builder().id("hh-1").clientReferenceId("hh-cref-1").tenantId("test-tenant")
                .memberCount(3)
                .address(org.egov.common.models.household.Address.builder().doorNo("D-TEST-2")
                        .addressLine1("Test house line 0002").landmark("TestLandmark0002")
                        .latitude(12.9711).longitude(77.5946).build())
                .build();
        HouseholdBulkRequest request = new HouseholdBulkRequest();
        request.setRequestInfo(requestInfo());
        request.setHouseholds(Collections.singletonList(household));
        List<String> secrets = new java.util.ArrayList<>(CALLER_SECRETS);
        secrets.addAll(Arrays.asList("D-TEST-2", "Test house line 0002", "TestLandmark0002", "12.9711", "77.5946"));

        assertOnlyIds(request, errorRecord(request, household, "setHouseholds"), secrets, "hh-cref-1");
    }

    @Test
    @DisplayName("hf referral: error record drops symptom and national ID")
    void hfReferralErrorRecordKeepsIdsOnly() throws Exception {
        HFReferral referral = HFReferral.builder().id("hfr-1").clientReferenceId("hfr-cref-1").tenantId("test-tenant")
                .symptom("TestSymptom0003").nationalLevelId("NLID-TEST-0003").beneficiaryId("BEN-TEST-0003").build();
        HFReferralBulkRequest request = new HFReferralBulkRequest();
        request.setRequestInfo(requestInfo());
        request.setHfReferrals(Collections.singletonList(referral));
        List<String> secrets = new java.util.ArrayList<>(CALLER_SECRETS);
        secrets.addAll(Arrays.asList("TestSymptom0003", "NLID-TEST-0003", "BEN-TEST-0003"));

        assertOnlyIds(request, errorRecord(request, referral, "setHfReferrals"), secrets, "hfr-cref-1");
    }
}
