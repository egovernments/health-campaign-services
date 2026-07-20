package org.egov.referralmanagement.spice;

import org.egov.referralmanagement.spice.model.SpiceHousehold;
import org.egov.referralmanagement.spice.model.SpiceMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpiceApiClient — auth (HMAC), token caching, 401 re-login")
class SpiceApiClientTest {

    // ground truth: printf 'Spice123' | openssl dgst -sha512 -hmac 'spice_uat'
    private static final String EXPECTED_HMAC =
            "3901e08e724bb73a72137e03e2f03d54d70eaeea39f8a7e0459f15d9e80585ffd2159283b8324d72f7ba8c2aa2f0e82e5c1b685d7ef569e2ebad02d71ba4076e";

    private static final String LOGIN_BODY = "{\"id\":64,\"organizationIds\":[28],\"tenantId\":28}";

    @Mock
    private RestTemplate rest;

    private SpiceApiClient client;

    @BeforeEach
    void setUp() {
        client = new SpiceApiClient();
        ReflectionTestUtils.setField(client, "rest", rest); // inject mock into the internal RestTemplate
    }

    private ResponseEntity<String> loginResponse() {
        HttpHeaders h = new HttpHeaders();
        h.add("Authorization", "Bearer test-token");
        h.add("tenantid", "28");
        return new ResponseEntity<>(LOGIN_BODY, h, HttpStatus.OK);
    }

    private HttpClientErrorException unauthorized() {
        return HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("password is HMAC-SHA512 hashed with salt 'spice_uat' (hex)")
    void hmac() {
        String hashed = ReflectionTestUtils.invokeMethod(client, "hmacSha512", "Spice123");
        assertEquals(EXPECTED_HMAC, hashed);
    }

    @Test
    @DisplayName("getHouseholds logs in once then parses the array; second call reuses the token")
    void loginCachedAcrossCalls() {
        AtomicInteger logins = new AtomicInteger();
        when(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenAnswer(inv -> {
                    String url = inv.getArgument(0);
                    if (url.contains("auth-service/session")) {
                        logins.incrementAndGet();
                        return loginResponse();
                    }
                    if (url.contains("household/list"))
                        return new ResponseEntity<>("[{\"id\":\"H1\",\"noOfPeople\":2}]", HttpStatus.OK);
                    return new ResponseEntity<>("[{\"id\":\"M1\",\"householdId\":\"H1\"}]", HttpStatus.OK);
                });

        List<SpiceHousehold> hh = client.getHouseholds(64L, null);
        List<SpiceMember> mm = client.getMembers(64L, null);

        assertEquals(1, hh.size());
        assertEquals("H1", hh.get(0).getId());
        assertEquals(1, mm.size());
        assertEquals("M1", mm.get(0).getId());
        assertEquals(1, logins.get(), "should authenticate exactly once and cache the token");
    }

    @Test
    @DisplayName("a 401 on a data call triggers one re-login and a retry")
    void reLoginOn401() {
        AtomicInteger logins = new AtomicInteger();
        AtomicInteger dataCalls = new AtomicInteger();
        when(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenAnswer(inv -> {
                    String url = inv.getArgument(0);
                    if (url.contains("auth-service/session")) {
                        logins.incrementAndGet();
                        return loginResponse();
                    }
                    // first data call fails with 401, retry succeeds
                    if (dataCalls.incrementAndGet() == 1) throw unauthorized();
                    return new ResponseEntity<>("[{\"id\":\"H9\"}]", HttpStatus.OK);
                });

        List<SpiceHousehold> hh = client.getHouseholds(64L, null);

        assertEquals(1, hh.size());
        assertEquals("H9", hh.get(0).getId());
        assertEquals(2, logins.get(), "should re-login after the 401");
        assertEquals(2, dataCalls.get(), "should retry the data call once");
    }

    @Test
    @DisplayName("empty/blank Spice response yields an empty list, not an error")
    void emptyResponse() {
        when(rest.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(String.class)))
                .thenAnswer(inv -> {
                    String url = inv.getArgument(0);
                    if (url.contains("auth-service/session")) return loginResponse();
                    return new ResponseEntity<>("[]", HttpStatus.OK);
                });

        assertTrue(client.getHouseholds(64L, null).isEmpty());
    }
}
