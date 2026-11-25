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

// 이벤트 저장 실패 시, 앞서 저장한 사건도 롤백되는지 실제 MySQL로 검증한다.
// 테스트 전용 DB를 사용하고, HTTP 서버를 띄우지 않고 서비스를 직접 호출한다.
// 실제 Spring 설정으로 서비스와 DB 연결을 준비
@SpringBootTest(
    // HTTP 서버 없이 서비스만 호출해서 검사
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/incident_desk_test",
        "spring.datasource.username=incident_app",
        "spring.datasource.password=${DB_PASSWORD}",
        // 테이블 구조를 검사하며, 자동 생성·수정은 하지 않음
        "spring.jpa.hibernate.ddl-auto=validate"
    }
)
class EventIngestionServiceRollbackTests {

    @Autowired
    private EventIngestionService eventIngestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 테스트에서만 이벤트 저장 동작을 바꿀 수 있도록 감싼다.
    @MockitoSpyBean
    private EventRepository eventRepository;

    @Test
    @DisplayName("이벤트 저장 실패 시 먼저 저장한 사건도 롤백된다")
    void rollsBackIncidentWhenEventSaveFails() {
        // 다른 DB에 연결됐다면 저장을 시도하기 전에 테스트를 중단한다.
        assertEquals(
            "incident_desk_test",
            jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)
        );

        // 테스트 전체를 감싸는 별도 트랜잭션이 없는 상태에서 시작한다.
        assertFalse(
            TransactionSynchronizationManager.isActualTransactionActive()
        );

        // 실행마다 식별자를 바꿔 이전 테스트 데이터와 구분한다.
        String serviceKey = "rollback-" + UUID.randomUUID();

        assertEquals(0L, countIncidents(serviceKey));
        assertEquals(0L, countEvents(serviceKey));

        doAnswer(invocation -> {
            // 실패 직전, 사건 INSERT가 실제 DB까지 실행됐는지 확인한다.
            // 같은 트랜잭션 안에서는 아직 커밋되지 않은 사건을 조회할 수 있다.
            assertEquals(1L, countIncidents(serviceKey));
            assertEquals(0L, countEvents(serviceKey));

            // 실제 이벤트 저장 대신 테스트용 실패를 발생시킨다.
            throw new IllegalStateException("TEST_EVENT_SAVE_FAILURE");
        }).when(eventRepository).save(any(Event.class));

        // 실제 Spring 서비스의 트랜잭션 경계를 통해 호출한다.
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> eventIngestionService.receive(
                serviceKey,
                "Rollback verification"
            )
        );

        assertEquals("TEST_EVENT_SAVE_FAILURE", exception.getMessage());
        verify(eventRepository).save(any(Event.class));

        // 서비스 호출이 끝난 뒤, 트랜잭션 밖에서 최종 DB 상태를 확인한다.
        assertFalse(
            TransactionSynchronizationManager.isActualTransactionActive()
        );

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