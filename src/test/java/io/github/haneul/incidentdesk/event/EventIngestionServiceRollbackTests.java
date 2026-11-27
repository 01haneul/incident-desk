package io.github.haneul.incidentdesk.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:${INCIDENT_TEST_DB_PORT:3306}/incident_desk_test",
        "spring.datasource.username=incident_app",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate"
    }
)
class EventIngestionServiceRollbackTests {

    @Autowired
    private EventIngestionService eventIngestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private EventRepository eventRepository;

    @Test
    @DisplayName("이벤트 저장 실패 시 먼저 저장한 사건도 롤백된다")
    void rollsBackIncidentWhenEventSaveFails() {
        assertEquals(
            "incident_desk_test",
            jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)
        );
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());

        String serviceKey = "rollback-" + UUID.randomUUID();

        assertEquals(0L, countIncidents(serviceKey));
        assertEquals(0L, countEvents(serviceKey));

        doAnswer(invocation -> {
            // 같은 트랜잭션에서 사건 INSERT가 실행됐음을 확인한 뒤 실패시킨다.
            assertEquals(1L, countIncidents(serviceKey));
            assertEquals(0L, countEvents(serviceKey));
            throw new IllegalStateException("TEST_EVENT_SAVE_FAILURE");
        }).when(eventRepository).save(any(Event.class));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> eventIngestionService.receive(serviceKey, "Rollback verification")
        );

        assertEquals("TEST_EVENT_SAVE_FAILURE", exception.getMessage());
        verify(eventRepository).save(any(Event.class));

        // 서비스의 트랜잭션이 끝난 뒤 실제 DB에 남은 행을 확인한다.
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(0L, countIncidents(serviceKey));
        assertEquals(0L, countEvents(serviceKey));
    }

    @Test
    @DisplayName("사건 연결 키가 있어도 이벤트 저장 실패 시 새 사건이 롤백된다")
    void rollsBackGroupedIncidentWhenEventSaveFails() {
        assertEquals(
            "incident_desk_test",
            jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)
        );
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());

        String serviceKey = "grouped-rollback-" + UUID.randomUUID();
        String incidentKey = "payment-approval-failure";

        assertEquals(0L, countIncidents(serviceKey));
        assertEquals(0L, countEvents(serviceKey));

        doAnswer(invocation -> {
            // 실패 직전에는 요청한 연결 키를 가진 새 사건이 보여야 한다.
            assertEquals(1L, countIncidents(serviceKey));
            assertEquals(0L, countEvents(serviceKey));
            assertEquals(
                incidentKey,
                jdbcTemplate.queryForObject(
                    "SELECT incident_key FROM incidents WHERE service_key = ?",
                    String.class,
                    serviceKey
                )
            );
            throw new IllegalStateException("TEST_GROUPED_EVENT_SAVE_FAILURE");
        }).when(eventRepository).save(any(Event.class));

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> eventIngestionService.receive(
                serviceKey,
                "Grouped rollback verification",
                "evt-rollback",
                incidentKey
            )
        );

        assertEquals("TEST_GROUPED_EVENT_SAVE_FAILURE", exception.getMessage());
        verify(eventRepository).save(any(Event.class));

        // 실패 직전에 보였던 사건까지 사라져야 롤백 성공이다.
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(0L, countIncidents(serviceKey));
        assertEquals(0L, countEvents(serviceKey));
    }

    private long countIncidents(String serviceKey) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM incidents WHERE service_key = ?",
            Long.class,
            serviceKey
        );
    }

    private long countEvents(String serviceKey) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE service_key = ?",
            Long.class,
            serviceKey
        );
    }
}