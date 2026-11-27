package io.github.haneul.incidentdesk.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import io.github.haneul.incidentdesk.incident.IncidentRepository;
import io.github.haneul.incidentdesk.incident.IncidentStatusService;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/incident_desk_test",
        "spring.datasource.username=incident_app",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate"
    }
)
class ExistingIncidentRollbackTests {

    @Autowired
    private EventIngestionService ingestionService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private EventRepository eventRepository;

    @Autowired
    private IncidentStatusService statusService;

    @MockitoSpyBean
    private IncidentRepository incidentRepository;

    @Test
    void preservesFirstIncidentAndEventWhenSecondEventSaveFails() {
        assertEquals(
            "incident_desk_test",
            jdbc.queryForObject("SELECT DATABASE()", String.class)
        );

        String serviceKey = "existing-rollback-" + UUID.randomUUID();
        String incidentKey = "payment-approval-failure";

        try {
            // 첫 접수는 실제 MySQL에 커밋한다.
            var first = ingestionService.receive(
                serviceKey, "First alert", "evt-001", incidentKey
            ).receipt();

            var incidentSql = "SELECT * FROM incidents WHERE service_key = ?";
            var eventSql = "SELECT * FROM events WHERE service_key = ?";

            // 정확히 한 행씩 있어야 하며, 첫 이벤트가 첫 사건을 가리켜야 한다.
            var firstIncident = jdbc.queryForMap(incidentSql, serviceKey);
            var firstEvent = jdbc.queryForMap(eventSql, serviceKey);
            assertEquals(first.incidentId(), firstIncident.get("id"));
            assertEquals("OPEN", firstIncident.get("status"));
            assertEquals(first.eventId(), firstEvent.get("id"));
            assertEquals(first.incidentId(), firstEvent.get("incident_id"));
            assertEquals("evt-001", firstEvent.get("external_event_id"));

            // 두 번째 접수에서만 이벤트 저장을 실패시킨다.
            doThrow(new IllegalStateException("TEST_SECOND_EVENT_FAILURE"))
                .when(eventRepository).save(any(Event.class));

            var failure = assertThrows(
                IllegalStateException.class,
                () -> ingestionService.receive(
                    serviceKey, "Second alert", "evt-002", incidentKey
                )
            );
            assertEquals("TEST_SECOND_EVENT_FAILURE", failure.getMessage());

            // 서비스 종료 후 다시 조회한다. 행 개수와 모든 컬럼 값이 같아야 한다.
            assertEquals(firstIncident, jdbc.queryForMap(incidentSql, serviceKey));
            assertEquals(firstEvent, jdbc.queryForMap(eventSql, serviceKey));
        } finally {
            // 이번 테스트가 만든 데이터만 외래키 순서에 맞춰 정리한다.
            jdbc.update("DELETE FROM events WHERE service_key = ?", serviceKey);
            jdbc.update("DELETE FROM incidents WHERE service_key = ?", serviceKey);
        }
    }

    @Test
    void preservesAssigneeAndFirstNotificationWhenTransferNotificationFails() {
        assertEquals(
            "incident_desk_test",
            jdbc.queryForObject("SELECT DATABASE()", String.class)
        );

        String serviceKey = "notification-rollback-" + UUID.randomUUID();

        try {
            Long incidentId = ingestionService.receive(
                serviceKey, "Notification rollback check", "evt-001", "payment-failure"
            ).receipt().incidentId();

            // 최초 배정과 첫 알림은 실제 DB에 커밋한다.
            statusService.assign(incidentId, "oncall-01");

            // 다음 인계에서 알림을 등록하는 호출만 실패시킨다.
            doThrow(new IllegalStateException("TEST_NOTIFICATION_FAILURE"))
                .when(incidentRepository)
                .insertPendingNotification(incidentId, "oncall-02", "TRANSFERRED");

            var failure = assertThrows(
                IllegalStateException.class,
                () -> statusService.transfer(incidentId, "oncall-01", "oncall-02")
            );
            assertEquals("TEST_NOTIFICATION_FAILURE", failure.getMessage());

            // 서비스 호출이 끝난 뒤, DB에서 원래 담당자가 유지됐는지 확인한다.
            assertEquals("oncall-01", jdbc.queryForObject(
                "SELECT assignee_key FROM incidents WHERE id = ?",
                String.class,
                incidentId
            ));

            // 정확히 한 행이어야 하며, 그 행은 최초 배정 알림이어야 한다.
            var notification = jdbc.queryForMap("""
                SELECT notification_type, recipient_key, status, attempt_count
                FROM incident_notifications
                WHERE incident_id = ?
                """, incidentId);

            assertEquals("ASSIGNED", notification.get("notification_type"));
            assertEquals("oncall-01", notification.get("recipient_key"));
            assertEquals("PENDING", notification.get("status"));
            assertEquals(0, notification.get("attempt_count"));
        } finally {
            jdbc.update("DELETE FROM events WHERE service_key = ?", serviceKey);
            jdbc.update("DELETE FROM incidents WHERE service_key = ?", serviceKey);
        }
    }
}