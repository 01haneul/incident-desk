package io.github.haneul.incidentdesk.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class NotificationSenderTests {

    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<ReceivedRequest> received = new AtomicReference<>();

    private HttpServer server;
    private NotificationSender sender;

    @BeforeEach
    void startReceiver() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        sender = new NotificationSender(
                URI.create("http://127.0.0.1:" + port + "/notifications")
        );
    }

    @AfterEach
    void stopReceiver() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsAssignmentAsJsonAndAcceptsHttp200() throws Exception {
        respondWith(200);

        sender.send(42L, 9L, "oncall-01", "ASSIGNED");

        var request = received.get();
        assertNotNull(request);
        assertEquals("POST", request.method());
        assertEquals("/notifications", request.path());
        assertEquals("42", request.idempotencyKey());

        var body = json.readTree(request.body());
        assertEquals(42L, body.get("notificationId").asLong());
        assertEquals(9L, body.get("incidentId").asLong());
        assertEquals("oncall-01", body.get("recipientKey").asText());
        assertEquals("ASSIGNED", body.get("notificationType").asText());
    }

    @Test
    void reportsFailureWhenReceiverReturnsHttp503() {
        respondWith(503);

        var failure = assertThrows(
                IllegalStateException.class,
                () -> sender.send(43L, 9L, "oncall-02", "TRANSFERRED")
        );

        assertEquals("알림 수신 서버 응답: HTTP 503", failure.getMessage());
        assertNotNull(received.get());
        assertEquals("43", received.get().idempotencyKey());
    }

    private void respondWith(int status) {
        server.createContext("/notifications", exchange -> {
            try {
                String body = new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                );

                received.set(new ReceivedRequest(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                        body
                ));

                exchange.sendResponseHeaders(status, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private record ReceivedRequest(
            String method,
            String path,
            String idempotencyKey,
            String body
    ) {
    }
}