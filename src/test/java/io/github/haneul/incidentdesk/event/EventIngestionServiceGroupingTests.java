package io.github.haneul.incidentdesk.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.github.haneul.incidentdesk.incident.IncidentStatusConflictException;
import io.github.haneul.incidentdesk.incident.IncidentStatusService;

// 실제 MySQL에서 사건 연결과 재전송 처리의 조합을 검사한다.
// 테스트 DB에서 고유한 서비스 식별자를 사용하고 해당 데이터만 정리한다.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/incident_desk_test",
        "spring.datasource.username=incident_app",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate"
    }
)
class EventIngestionServiceGroupingTests {

    private static final String INCIDENT_KEY =
            "payment-approval-failure";

    private static final String FIRST_TITLE =
            "Payment approval failed";

    private static final String SECOND_TITLE =
            "Payment approval is still failing";

    @Autowired
    private EventIngestionService eventIngestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IncidentStatusService incidentStatusService;

    private String serviceKey;

    @BeforeEach
    void prepareTest() {
        assertEquals(
                "incident_desk_test",
                jdbcTemplate.queryForObject(
                        "SELECT DATABASE()",
                        String.class
                )
        );

        // 서비스의 실제 커밋 결과를 확인하도록 외부 트랜잭션 없이 시작
        assertFalse(
                TransactionSynchronizationManager.isActualTransactionActive()
        );

        serviceKey = "group-test-" + UUID.randomUUID();
        assertCounts(0, 0);
    }

    @AfterEach
    void cleanUp() {
        // DB 확인 전에 실패했다면 정리 쿼리를 실행하지 않음
        if (serviceKey == null) {
            return;
        }

        jdbcTemplate.update(
                "DELETE FROM events WHERE service_key = ?",
                serviceKey
        );

        jdbcTemplate.update(
                "DELETE FROM incidents WHERE service_key = ?",
                serviceKey
        );
    }

    @Test
    void connectsDifferentEventsToSameOpenIncident() {
        EventIngestionService.Result first =
                receive(FIRST_TITLE, "evt-001");

        EventIngestionService.Result second =
                receive(SECOND_TITLE, "evt-002");

        assertTrue(first.created());
        assertTrue(first.incidentCreated());

        assertTrue(second.created());
        assertFalse(second.incidentCreated());

        assertEquals(
                first.receipt().incidentId(),
                second.receipt().incidentId()
        );

        assertNotEquals(
                first.receipt().eventId(),
                second.receipt().eventId()
        );

        // 사건 대표 제목은 최초 값을 유지
        assertEquals(
                FIRST_TITLE,
                jdbcTemplate.queryForObject(
                        "SELECT title FROM incidents WHERE id = ?",
                        String.class,
                        first.receipt().incidentId()
                )
        );

        // 두 번째 이벤트에는 두 번째 요청 내용을 보관
        assertEquals(
                SECOND_TITLE,
                jdbcTemplate.queryForObject(
                        "SELECT title FROM events WHERE id = ?",
                        String.class,
                        second.receipt().eventId()
                )
        );

        assertEquals(
                INCIDENT_KEY,
                jdbcTemplate.queryForObject(
                        "SELECT incident_key FROM events WHERE id = ?",
                        String.class,
                        second.receipt().eventId()
                )
        );

        assertCounts(1, 2);
    }

    @Test
    void reusesSameEventWithinGroup() {
        EventIngestionService.Result first =
                receive(FIRST_TITLE, "evt-001");

        EventIngestionService.Result replay =
                receive(FIRST_TITLE, "evt-001");

        assertFalse(replay.created());
        assertFalse(replay.incidentCreated());
        assertEquals(first.receipt(), replay.receipt());

        assertCounts(1, 1);
    }

    @Test
    void rejectsChangedIncidentKeyForSameExternalEvent() {
        receive(FIRST_TITLE, "evt-001");

        assertThrows(
                EventIdConflictException.class,
                () -> eventIngestionService.receive(
                        serviceKey,
                        FIRST_TITLE,
                        "evt-001",
                        "database-connection-failure"
                )
        );

        assertCounts(1, 1);
    }

    @Test
    void groupsNewEventsWithoutExternalEventId() {
        EventIngestionService.Result first =
                receive(FIRST_TITLE, null);

        EventIngestionService.Result second =
                receive(SECOND_TITLE, null);

        assertTrue(first.created());
        assertTrue(first.incidentCreated());

        assertTrue(second.created());
        assertFalse(second.incidentCreated());

        assertEquals(
                first.receipt().incidentId(),
                second.receipt().incidentId()
        );

        assertNotEquals(
                first.receipt().eventId(),
                second.receipt().eventId()
        );

        assertCounts(1, 2);
    }

    @Test
    void preservesFirstAssigneeAfterRetryConflictAndAnotherAlert() {
        var first = receive(FIRST_TITLE, "evt-001");
        Long incidentId = first.receipt().incidentId();

        // 최초 배정과 같은 담당자의 재요청은 모두 성공한다.
        incidentStatusService.assign(incidentId, "oncall-01");
        incidentStatusService.assign(incidentId, "oncall-01");

        // 다른 담당자가 최초 배정을 덮어쓸 수 없다.
        assertThrows(
                IncidentStatusConflictException.class,
                () -> incidentStatusService.assign(incidentId, "oncall-02")
        );

        // 배정 후에도 같은 장애의 새 알림은 기존 OPEN 사건으로 들어온다.
        var second = receive(SECOND_TITLE, "evt-002");
        assertEquals(incidentId, second.receipt().incidentId());
        assertFalse(second.incidentCreated());

        var incident = jdbcTemplate.queryForMap(
                "SELECT status, assignee_key FROM incidents WHERE id = ?",
                incidentId
        );
        assertEquals("OPEN", incident.get("status"));
        assertEquals("oncall-01", incident.get("assignee_key"));
        assertCounts(1, 2);
    }

        @Test
    void transfersOpenIncidentAndPreservesAssigneeAfterConflicts() {
        Long incidentId = receive(FIRST_TITLE, "evt-001").receipt().incidentId();
        incidentStatusService.assign(incidentId, "oncall-01");

        // OPEN 사건은 현재 담당자가 일치하면 인계된다.
        incidentStatusService.transfer(incidentId, "oncall-01", "oncall-02");

        // 이전 담당자 정보로 보낸 요청은 새 담당자를 덮어쓸 수 없다.
        assertThrows(
                IncidentStatusConflictException.class,
                () -> incidentStatusService.transfer(incidentId, "oncall-01", "oncall-03")
        );

        assertEquals("ACKNOWLEDGED", incidentStatusService.acknowledge(incidentId));

        // 현재 담당자가 정확해도 이미 확인한 사건은 인계할 수 없다.
        assertThrows(
                IncidentStatusConflictException.class,
                () -> incidentStatusService.transfer(incidentId, "oncall-02", "oncall-03")
        );

        var incident = jdbcTemplate.queryForMap(
                "SELECT status, assignee_key FROM incidents WHERE id = ?",
                incidentId
        );
        assertEquals("ACKNOWLEDGED", incident.get("status"));
        assertEquals("oncall-02", incident.get("assignee_key"));
    }

    private EventIngestionService.Result receive(
            String title,
            String externalEventId
    ) {
        return eventIngestionService.receive(
                serviceKey,
                title,
                externalEventId,
                INCIDENT_KEY
        );
    }

    private void assertCounts(
            long expectedIncidents,
            long expectedEvents
    ) {
        assertEquals(
                expectedIncidents,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM incidents WHERE service_key = ?",
                        Long.class,
                        serviceKey
                )
        );

        assertEquals(
                expectedEvents,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM events WHERE service_key = ?",
                        Long.class,
                        serviceKey
                )
        );
    }
}