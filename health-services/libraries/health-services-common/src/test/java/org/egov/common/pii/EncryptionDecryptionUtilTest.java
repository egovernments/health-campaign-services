package org.egov.common.pii;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.Role;
import org.egov.common.contract.request.User;
import org.egov.encryption.EncryptionService;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The generic enc-client calls: the model and the audit purpose come from the caller, not from this library. */
class EncryptionDecryptionUtilTest {

    private static final String CIPHER = "228075|" + Base64.getEncoder().encodeToString(new byte[24]);
    private static final String MODEL = "SomePiiEncrypt";

    private final ObjectMapper mapper = new ObjectMapper();
    private EncryptionService encryptionService;
    private EncryptionDecryptionUtil util;

    @BeforeEach
    void setUp() {
        encryptionService = mock(EncryptionService.class);
        PiiEncryptionProperties properties = new PiiEncryptionProperties();
        properties.setStateLevelTenantId("dev");
        properties.setEncCircuitFailureThreshold(5);
        properties.setEncCircuitOpenMs(30_000L);
        util = new EncryptionDecryptionUtil(encryptionService, mapper, properties, new EncCircuitBreaker(properties));
    }

    private ArrayNode cipherResult(int count) {
        ArrayNode result = mapper.createArrayNode();
        for (int i = 0; i < count; i++) {
            result.addObject().put("value", CIPHER);
        }
        return result;
    }

    @Test
    @DisplayName("encryptValues: one enc-client call with the model and the state tenant, ciphers come back in order")
    void encryptsInOneCall() throws IOException {
        when(encryptionService.encryptJson(any(), anyString(), anyString())).thenReturn(cipherResult(2));

        List<String> ciphers = util.encryptValues(List.of("9876543210", "asha"), MODEL);

        assertEquals(List.of(CIPHER, CIPHER), ciphers);
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(encryptionService).encryptJson(sent.capture(), eq(MODEL), eq("dev"));
        JsonNode nodes = (JsonNode) sent.getValue();
        assertEquals("9876543210", nodes.get(0).get("value").asText());
        assertEquals("asha", nodes.get(1).get("value").asText());
    }

    @Test
    @DisplayName("encryptValues: nothing to encrypt -> no enc-client call")
    void emptyListSkipsCall() {
        assertTrue(util.encryptValues(List.of(), MODEL).isEmpty());
        assertTrue(util.encryptValues(null, MODEL).isEmpty());

        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("encryptValues: a value that did not come back as cipher text is never accepted; the error names the model")
    void rejectsNonCipherResult() throws IOException {
        ArrayNode plain = mapper.createArrayNode();
        plain.addObject().put("value", "9876543210");
        when(encryptionService.encryptJson(any(), anyString(), anyString())).thenReturn(plain);

        CustomException e = assertThrows(CustomException.class, () -> util.encryptValues(List.of("9876543210"), MODEL));

        assertEquals("INDIVIDUAL_ENCRYPTION_NULL_RESULT", e.getCode());
        assertTrue(e.getMessage().contains(MODEL));
    }

    @Test
    @DisplayName("encryptValues: wrong number of results, an IO error and an unexpected error each give their own code")
    void errorCodes() throws IOException {
        when(encryptionService.encryptJson(any(), anyString(), anyString())).thenReturn(cipherResult(1));
        assertEquals("INDIVIDUAL_ENCRYPTION_NULL_RESULT",
                assertThrows(CustomException.class, () -> util.encryptValues(List.of("a", "b"), MODEL)).getCode());

        // re-stubbing a mock that already throws: doThrow, because when(mock.call()) would run the old stub
        doThrow(new IOException("boom")).when(encryptionService).encryptJson(any(), anyString(), anyString());
        assertEquals("INDIVIDUAL_ENCRYPTION_TRANSPORT_ERROR",
                assertThrows(CustomException.class, () -> util.encryptValues(List.of("a"), MODEL)).getCode());

        doThrow(new IllegalStateException("boom")).when(encryptionService).encryptJson(any(), anyString(), anyString());
        assertEquals("INDIVIDUAL_ENCRYPTION_ERROR",
                assertThrows(CustomException.class, () -> util.encryptValues(List.of("a"), MODEL)).getCode());
    }

    @Test
    @DisplayName("decryptValueNodes: model and purpose are passed on; the user's type is added as a role on a copy of the user")
    void decryptAddsUserTypeRole() throws IOException {
        ArrayNode nodes = mapper.createArrayNode();
        nodes.addObject().put("ownerId", "o1").put("value", CIPHER);
        when(encryptionService.decryptJson(any(), any(), anyString(), anyString())).thenReturn(nodes);
        User user = User.builder().uuid("u1").type("CITIZEN").roles(List.of(Role.builder().code("SOME_ROLE").build())).build();
        RequestInfo requestInfo = RequestInfo.builder().userInfo(user).build();

        JsonNode result = util.decryptValueNodes(nodes, "SomePiiDecrypt", requestInfo, "somePurpose");

        assertSame(nodes, result);
        ArgumentCaptor<RequestInfo> sent = ArgumentCaptor.forClass(RequestInfo.class);
        verify(encryptionService).decryptJson(sent.capture(), eq(nodes), eq("SomePiiDecrypt"), eq("somePurpose"));
        List<String> roles = sent.getValue().getUserInfo().getRoles().stream().map(Role::getCode).toList();
        assertEquals(List.of("SOME_ROLE", "CITIZEN"), roles);
        assertEquals(1, user.getRoles().size(), "the caller's own user object keeps its roles");
    }

    @Test
    @DisplayName("decryptValueNodes: no request info -> an EMPLOYEE user is used; nothing to decrypt -> no call")
    void decryptWithoutRequestInfo() throws IOException {
        ArrayNode nodes = mapper.createArrayNode();
        nodes.addObject().put("ownerId", "o1").put("value", CIPHER);
        when(encryptionService.decryptJson(any(), any(), anyString(), anyString())).thenReturn(nodes);

        util.decryptValueNodes(nodes, "SomePiiDecrypt", null, "somePurpose");

        ArgumentCaptor<RequestInfo> sent = ArgumentCaptor.forClass(RequestInfo.class);
        verify(encryptionService).decryptJson(sent.capture(), any(), anyString(), anyString());
        assertEquals("EMPLOYEE", sent.getValue().getUserInfo().getType());
        assertEquals("EMPLOYEE", sent.getValue().getUserInfo().getRoles().get(0).getCode());

        org.mockito.Mockito.clearInvocations(encryptionService);
        assertEquals(0, util.decryptValueNodes(mapper.createArrayNode(), "SomePiiDecrypt", null, "somePurpose").size());
        verifyNoInteractions(encryptionService);
    }

    @Test
    @DisplayName("decryptValueNodes: wrong result size -> INDIVIDUAL_DECRYPTION_NULL_RESULT naming the model")
    void decryptResultSizeChecked() throws IOException {
        ArrayNode nodes = mapper.createArrayNode();
        nodes.addObject().put("ownerId", "o1").put("value", CIPHER);
        when(encryptionService.decryptJson(any(), any(), anyString(), anyString())).thenReturn(mapper.createArrayNode());

        CustomException e = assertThrows(CustomException.class,
                () -> util.decryptValueNodes(nodes, "SomePiiDecrypt", null, "somePurpose"));

        assertEquals("INDIVIDUAL_DECRYPTION_NULL_RESULT", e.getCode());
        assertTrue(e.getMessage().contains("SomePiiDecrypt"));
    }
}
