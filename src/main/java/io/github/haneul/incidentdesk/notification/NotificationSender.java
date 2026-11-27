package io.github.haneul.incidentdesk.notification;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationSender {

    private final RestClient client;
    private final URI receiverUrl;

    public NotificationSender(
            @Value("${incident.notification.receiver-url:http://127.0.0.1:8090/notifications}")
            URI receiverUrl
    ) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));

        this.client = RestClient.builder()
                .requestFactory(factory)
                .build();
        this.receiverUrl = receiverUrl;
    }

    public void send(long notificationId, long incidentId,
                     String recipientKey, String notificationType) {
        client.post()
                .uri(receiverUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", Long.toString(notificationId))
                .body(Map.of(
                        "notificationId", notificationId,
                        "incidentId", incidentId,
                        "recipientKey", recipientKey,
                        "notificationType", notificationType
                ))
                .exchange((request, response) -> {
                    var status = response.getStatusCode();
                    if (!status.is2xxSuccessful()) {
                        throw new IllegalStateException(
                                "알림 수신 서버 응답: HTTP " + status.value()
                        );
                    }
                    return null;
                });
    }
}