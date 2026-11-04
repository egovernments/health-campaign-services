package org.egov.web.notification.push.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.firebase.FirebaseApp;

class FcmClientRegistryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static String privateKeyPem;

    @BeforeAll
    static void generateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String body = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        privateKeyPem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n";
    }

    @AfterEach
    void deleteApps() {
        FirebaseApp.getApps().forEach(FirebaseApp::delete);
    }

    private static ObjectNode serviceAccount(String projectId) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "service_account");
        node.put("project_id", projectId);
        node.put("private_key_id", "kid-" + projectId);
        node.put("private_key", privateKeyPem);
        node.put("client_email", "sa@" + projectId + ".iam.gserviceaccount.com");
        node.put("client_id", "123");
        node.put("token_uri", "https://oauth2.googleapis.com/token");
        return node;
    }

    private static String array(String... projectIds) {
        var array = MAPPER.createArrayNode();
        for (String id : projectIds) {
            array.add(serviceAccount(id));
        }
        return array.toString();
    }

    private static String projectFor(FcmClientRegistry registry, String tenantId) {
        return registry.forTenant(tenantId).map(FcmClientRegistry.FcmClient::projectId).orElse(null);
    }

    private static String failure(String keys, String map) {
        return assertThrows(IllegalStateException.class, () -> new FcmClientRegistry(keys, map, MAPPER)).getMessage();
    }

    @Test
    void singleObjectWithoutMap_servesAllTenants() {
        FcmClientRegistry registry = new FcmClientRegistry(serviceAccount("p1").toString(), "", MAPPER);

        assertEquals("p1", projectFor(registry, "ng.kano"));
        assertEquals("p1", projectFor(registry, null));
    }

    @Test
    void listWithMap_routesByTenantAndParentTenant() {
        FcmClientRegistry registry = new FcmClientRegistry(array("p1", "p2"),
                "{\"ng\":\"p1\",\"ng.kano\":\"p2\"}", MAPPER);

        assertEquals("p2", projectFor(registry, "ng.kano"));
        assertEquals("p2", projectFor(registry, "ng.kano.ward1"));
        assertEquals("p1", projectFor(registry, "ng.lagos"));
        assertEquals(null, projectFor(registry, "mz"));
        assertEquals(null, projectFor(registry, null));
    }

    @Test
    void missingKey_fails() {
        assertTrue(failure("", "").contains("FCM_SERVICE_ACCOUNT_KEY_JSON is not set"));
    }

    @Test
    void invalidJson_fails() {
        assertTrue(failure("{not json", "").contains("is not valid JSON"));
    }

    @Test
    void emptyArray_fails() {
        assertTrue(failure("[]", "").contains("empty array"));
    }

    @Test
    void notServiceAccount_failsWithIndex() {
        assertTrue(failure("[" + serviceAccount("p1") + ",{\"type\":\"user\"}]", "").contains("[1]"));
    }

    @Test
    void missingProjectId_fails() {
        ObjectNode node = serviceAccount("p1");
        node.remove("project_id");
        assertTrue(failure(node.toString(), "").contains("project_id"));
    }

    @Test
    void duplicateProjectId_fails() {
        assertTrue(failure(array("p1", "p1"), "{\"ng\":\"p1\"}").contains("more than one service account for project 'p1'"));
    }

    @Test
    void multipleAccountsWithoutMap_fails() {
        assertTrue(failure(array("p1", "p2"), "").contains("FCM_TENANT_PROJECT_MAP_JSON is required"));
    }

    @Test
    void mapNotObject_fails() {
        assertTrue(failure(array("p1"), "[\"ng\"]").contains("must be a JSON object"));
    }

    @Test
    void mapToUnknownProject_fails() {
        String message = failure(array("p1"), "{\"ng\":\"p9\"}");
        assertTrue(message.contains("tenant 'ng'") && message.contains("'p9'"));
    }

    @Test
    void badPrivateKey_fails() {
        ObjectNode node = serviceAccount("p1");
        node.put("private_key", "garbage");
        String message = failure(node.toString(), "");
        assertTrue(message.contains("project 'p1' could not be loaded"));
        assertTrue(!message.contains("garbage"));
    }
}
