package org.egov.common.pii;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.egov.tracer.model.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a failed envelope open is classed: enc-service answered (refused) vs could not be reached. */
class EnvelopeEncClientTest {

    private static final String CIPHER = "228075|" + Base64.getEncoder().encodeToString(new byte[24]);
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private int serve(int status) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server.getAddress().getPort();
    }

    private static EnvelopeEncClient client(int port, EncCircuitBreaker breaker) {
        PiiEncryptionProperties properties = new PiiEncryptionProperties();
        properties.setEncConnectTimeoutMs(500);
        properties.setEncReadTimeoutMs(2_000);
        return new EnvelopeEncClient(properties, new ObjectMapper(), breaker, "http://127.0.0.1:" + port,
                "/encrypt", "/decrypt", "dev");
    }

    private static EncCircuitBreaker breaker() {
        PiiEncryptionProperties properties = new PiiEncryptionProperties();
        properties.setEncCircuitFailureThreshold(2);
        properties.setEncCircuitOpenMs(60_000L);
        return new EncCircuitBreaker(properties);
    }

    @Test
    @DisplayName("HTTP 500 from enc-service (unknown key id, damaged cipher) -> REJECTED, the circuit stays closed")
    void http500IsRejected() throws Exception {
        EncCircuitBreaker breaker = breaker();
        EnvelopeEncClient client = client(serve(500), breaker);
        for (int i = 0; i < 3; i++) {
            CustomException e = assertThrows(CustomException.class, () -> client.decrypt(CIPHER));
            assertEquals(EncCircuitBreaker.ENC_REJECTED, e.getCode());
        }
        assertFalse(breaker.isOpen());
    }

    @Test
    @DisplayName("HTTP 503 -> UNAVAILABLE and counted")
    void http503IsUnavailable() throws Exception {
        EncCircuitBreaker breaker = breaker();
        EnvelopeEncClient client = client(serve(503), breaker);
        for (int i = 0; i < 2; i++) {
            CustomException e = assertThrows(CustomException.class, () -> client.decrypt(CIPHER));
            assertEquals(EncCircuitBreaker.ENC_UNAVAILABLE, e.getCode());
        }
        assertTrue(breaker.isOpen());
    }

    @Test
    @DisplayName("nothing listening (enc-service down) -> UNAVAILABLE and counted")
    void connectionRefusedIsUnavailable() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        EncCircuitBreaker breaker = breaker();
        EnvelopeEncClient client = client(port, breaker);
        for (int i = 0; i < 2; i++) {
            CustomException e = assertThrows(CustomException.class, () -> client.decrypt(CIPHER));
            assertEquals(EncCircuitBreaker.ENC_UNAVAILABLE, e.getCode());
        }
        assertTrue(breaker.isOpen());
    }
}
