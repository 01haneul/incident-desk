package io.github.haneul.incidentdesk.notification;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@Service
public class NotificationDispatcher {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationDispatcher.class);

    private static final int MAX_ATTEMPTS = 3;

    private final JdbcTemplate jdbc;
    private final NotificationSender sender;

    public NotificationDispatcher(JdbcTemplate jdbc, NotificationSender sender) {
        this.jdbc = jdbc;
        this.sender = sender;
    }

    @Transactional
    public boolean dispatchOne() {
        List<PendingNotification> due = jdbc.query("""
                SELECT id, incident_id, recipient_key, notification_type, attempt_count
                FROM incident_notifications
                WHERE status = 'PENDING'
                  AND next_attempt_at <= UTC_TIMESTAMP(6)
                ORDER BY next_attempt_at, id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """, (rs, rowNum) -> new PendingNotification(
                rs.getLong("id"),
                rs.getLong("incident_id"),
                rs.getString("recipient_key"),
                rs.getString("notification_type"),
                rs.getInt("attempt_count")
        ));

        if (due.isEmpty()) {
            return false;
        }

        var notification = due.get(0);
        int attempt = notification.attemptCount() + 1;

        try {
            sender.send(
                    notification.id(),
                    notification.incidentId(),
                    notification.recipientKey(),
                    notification.notificationType()
            );
        } catch (RestClientException | IllegalStateException exception) {
            String status = attempt >= MAX_ATTEMPTS ? "FAILED" : "PENDING";

            jdbc.update("""
                    UPDATE incident_notifications
                    SET status = ?,
                        attempt_count = ?,
                        next_attempt_at = TIMESTAMPADD(SECOND, 30, UTC_TIMESTAMP(6))
                    WHERE id = ?
                    """, status, attempt, notification.id());

            log.warn("알림 전송 실패 id={} attempt={} status={}",
                    notification.id(), attempt, status);
            return true;
        }

        jdbc.update("""
                UPDATE incident_notifications
                SET status = 'SENT', attempt_count = ?
                WHERE id = ?
                """, attempt, notification.id());

        return true;
    }

    private record PendingNotification(
            long id,
            long incidentId,
            String recipientKey,
            String notificationType,
            int attemptCount
    ) {
    }
}