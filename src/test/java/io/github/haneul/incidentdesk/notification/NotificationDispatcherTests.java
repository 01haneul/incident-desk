package io.github.haneul.incidentdesk.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.github.haneul.incidentdesk.event.EventIngestionService;
import io.github.haneul.incidentdesk.incident.IncidentStatusService;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:${INCIDENT_TEST_DB_PORT:3306}/incident_desk_test",
        "spring.datasource.username=incident_app",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate"
    }
)
class NotificationDispatcherTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EventIngestionService ingestionService;

    @Autowired
    private IncidentStatusService statusService;

    @Autowired
    private NotificationDispatcher dispatcher;

    @MockitoBean
    private NotificationSender sender;

    @Test
    void retriesSameNotificationWhenDueAndMarksItSent() {
        assertEquals(
            "incident_desk_test",
            jdbc.queryForObject("SELECT DATABASE()", String.class)
        );

        // dispatchOne()은 전체 큐에서 한 건을 고르므로 기존 대기 건을 먼저 확인한다.
        assertEquals(0L, jdbc.queryForObject("""
            SELECT COUNT(*) FROM incident_notifications WHERE status = 'PENDING'
            """, Long.class), "테스트 DB에 이전 PENDING 알림이 남아 있습니다.");

        String serviceKey = "dispatch-test-" + UUID.randomUUID();

        try {
            long incidentId = ingestionService.receive(
                serviceKey, "Dispatch retry test", "evt-001", "dispatch-check"
            ).receipt().incidentId();

            statusService.assign(incidentId, "oncall-01");

            long notificationId = jdbc.queryForObject("""
                SELECT id FROM incident_notifications WHERE incident_id = ?
                """, Long.class, incidentId);

            doThrow(new IllegalStateException("TEST_RECEIVER_UNAVAILABLE"))
                .doNothing()
                .when(sender)
                .send(notificationId, incidentId, "oncall-01", "ASSIGNED");

            // 첫 실패가 DB에 기록되고 다음 전송은 미래로 예약돼야 한다.
            assertTrue(dispatcher.dispatchOne());
            assertState(notificationId, "PENDING", 1);
            assertEquals(1L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM incident_notifications
                WHERE id = ? AND next_attempt_at > UTC_TIMESTAMP(6)
                """, Long.class, notificationId));

            // 아직 재시도 시간이 되지 않았으므로 전송기를 다시 호출하지 않는다.
            assertFalse(dispatcher.dispatchOne());
            verify(sender).send(notificationId, incidentId, "oncall-01", "ASSIGNED");

            // 실제로 30초를 기다리지 않고 이 테스트 알림만 재시도 가능하게 한다.
            jdbc.update("""
                UPDATE incident_notifications
                SET next_attempt_at = UTC_TIMESTAMP(6)
                WHERE id = ?
                """, notificationId);

            assertTrue(dispatcher.dispatchOne());
            assertState(notificationId, "SENT", 2);

            // 성공한 알림은 다시 선택되지 않는다.
            assertFalse(dispatcher.dispatchOne());
            verify(sender, times(2))
                .send(notificationId, incidentId, "oncall-01", "ASSIGNED");
        } finally {
            jdbc.update("DELETE FROM events WHERE service_key = ?", serviceKey);
            jdbc.update("DELETE FROM incidents WHERE service_key = ?", serviceKey);
        }
    }

    @Test
    void stopsAfterThreeFailedAttempts() {
        assertEquals(
            "incident_desk_test",
            jdbc.queryForObject("SELECT DATABASE()", String.class)
        );
        assertEquals(0L, jdbc.queryForObject("""
            SELECT COUNT(*) FROM incident_notifications WHERE status = 'PENDING'
            """, Long.class), "테스트 DB에 이전 PENDING 알림이 남아 있습니다.");

        String serviceKey = "dispatch-limit-" + UUID.randomUUID();

        try {
            long incidentId = ingestionService.receive(
                serviceKey, "Retry limit check", "evt-001", "retry-limit"
            ).receipt().incidentId();

            statusService.assign(incidentId, "oncall-01");

            long notificationId = jdbc.queryForObject("""
                SELECT id FROM incident_notifications WHERE incident_id = ?
                """, Long.class, incidentId);

            doThrow(new IllegalStateException("TEST_RECEIVER_UNAVAILABLE"))
                .when(sender)
                .send(notificationId, incidentId, "oncall-01", "ASSIGNED");

            for (int attempt = 1; attempt <= 3; attempt++) {
                assertTrue(dispatcher.dispatchOne());
                assertState(notificationId, attempt < 3 ? "PENDING" : "FAILED", attempt);

                // 30초를 기다리는 대신 이 테스트 알림의 예약 시각만 앞당긴다.
                jdbc.update("""
                    UPDATE incident_notifications
                    SET next_attempt_at = UTC_TIMESTAMP(6)
                    WHERE id = ?
                    """, notificationId);
            }

            // 예약 시각이 됐어도 FAILED 알림은 네 번째로 전송되지 않아야 한다.
            assertFalse(dispatcher.dispatchOne());
            verify(sender, times(3))
                .send(notificationId, incidentId, "oncall-01", "ASSIGNED");
        } finally {
            jdbc.update("DELETE FROM events WHERE service_key = ?", serviceKey);
            jdbc.update("DELETE FROM incidents WHERE service_key = ?", serviceKey);
        }
    }


    private void assertState(long notificationId, String status, int attempts) {
        var row = jdbc.queryForMap("""
            SELECT status, attempt_count
            FROM incident_notifications
            WHERE id = ?
            """, notificationId);

        assertEquals(status, row.get("status"));
        assertEquals(attempts, row.get("attempt_count"));
    }
}