package io.github.haneul.incidentdesk.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 실제 MySQL에서 재전송, 충돌, 식별 범위, 동시 접수를 검증한다.
// 테스트마다 고유한 서비스 식별자를 사용하고 종료 후 해당 데이터만 삭제한다.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:${INCIDENT_TEST_DB_PORT:3306}/incident_desk_test",
        "spring.datasource.username=incident_app",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate"
    }
)
class EventIngestionServiceIdempotencyTests {

    private static final String TITLE = "Payment approval failed";

    @Autowired
    private EventIngestionService eventIngestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 실제 저장은 유지하면서 동시 요청의 저장 시작 시점만 조절한다.
    @MockitoSpyBean
    private EventCreationService eventCreationService;

    private final List<String> testServiceKeys = new ArrayList<>();
    private String serviceKey;
    private ExecutorService executor;

    @BeforeEach
    void prepareTest() {
        // 테스트 DB가 아니면 데이터를 생성하기 전에 중단
        assertEquals(
                "incident_desk_test",
                jdbcTemplate.queryForObject("SELECT DATABASE()", String.class)
        );

        // 테스트 자체의 트랜잭션으로 결과를 가리지 않도록 확인
        assertFalse(
                TransactionSynchronizationManager.isActualTransactionActive()
        );

        serviceKey = newServiceKey();
        assertCounts(serviceKey, 0);
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        if (executor != null) {
            executor.shutdownNow();

            // 실행 중인 요청이 종료된 뒤에만 데이터를 정리
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "동시 요청이 종료되지 않아 데이터 정리를 중단했습니다."
                );
            }
        }

        for (String key : testServiceKeys) {
            // 외래키 관계에 따라 이벤트부터 삭제
            jdbcTemplate.update(
                    "DELETE FROM events WHERE service_key = ?",
                    key
            );
            jdbcTemplate.update(
                    "DELETE FROM incidents WHERE service_key = ?",
                    key
            );
        }
    }

    @Test
    void reusesReceiptForSameExternalEvent() {
        EventIngestionService.Result first =
                eventIngestionService.receive(
                        serviceKey, TITLE, "evt-001"
                );

        EventIngestionService.Result replay =
                eventIngestionService.receive(
                        serviceKey, TITLE, "evt-001"
                );

        assertTrue(first.created());
        assertFalse(replay.created());

        // record 전체 비교: 두 번호, 상태, 수신 시각이 모두 같아야 함
        assertEquals(first.receipt(), replay.receipt());

        assertCounts(serviceKey, 1);
    }

    @Test
    void rejectsDifferentTitleWithoutCreatingExtraRows() {
        EventIngestionService.Result first =
                eventIngestionService.receive(
                        serviceKey, TITLE, "evt-001"
                );

        assertThrows(
                EventIdConflictException.class,
                () -> eventIngestionService.receive(
                        serviceKey, "Different title", "evt-001"
                )
        );

        // 최초 접수 내용도 변경되지 않아야 함
        assertEquals(
                TITLE,
                jdbcTemplate.queryForObject(
                        "SELECT title FROM events WHERE id = ?",
                        String.class,
                        first.receipt().eventId()
                )
        );

        assertCounts(serviceKey, 1);
    }

    // 다른 번호, 다른 서비스, 대소문자 차이를 각각 별도 이벤트로 인정
    @ParameterizedTest(
        name = "differentService={0}, externalIds={1}/{2}"
    )
    @CsvSource({
            "false, evt-001, evt-002",
            "true, evt-001, evt-001",
            "false, evt-A, evt-a"
    })
    void acceptsDistinctEventKeys(
            boolean differentService,
            String firstExternalId,
            String secondExternalId
    ) {
        String secondServiceKey = differentService
                ? newServiceKey()
                : serviceKey;

        EventIngestionService.Result first =
                eventIngestionService.receive(
                        serviceKey, TITLE, firstExternalId
                );

        EventIngestionService.Result second =
                eventIngestionService.receive(
                        secondServiceKey, TITLE, secondExternalId
                );

        assertTrue(first.created());
        assertTrue(second.created());

        assertNotEquals(
                first.receipt().eventId(),
                second.receipt().eventId()
        );
        assertNotEquals(
                first.receipt().incidentId(),
                second.receipt().incidentId()
        );

        if (differentService) {
            assertCounts(serviceKey, 1);
            assertCounts(secondServiceKey, 1);
        } else {
            assertCounts(serviceKey, 2);
        }
    }

    @Test
    void createsNewEventsWhenExternalIdIsMissing() {
        EventIngestionService.Result first =
                eventIngestionService.receive(
                        serviceKey, TITLE, null
                );

        EventIngestionService.Result second =
                eventIngestionService.receive(
                        serviceKey, TITLE, null
                );

        assertTrue(first.created());
        assertTrue(second.created());

        assertNotEquals(
                first.receipt().eventId(),
                second.receipt().eventId()
        );
        assertNotEquals(
                first.receipt().incidentId(),
                second.receipt().incidentId()
        );

        assertCounts(serviceKey, 2);
    }

    @Test
    void handlesConcurrentFirstRequestsWithoutDuplicates() throws Exception {
        // CountDownLatch : 두 요청이 모두 저장 직전까지 도착하도록 기다리는 문
        CountDownLatch writersReady = new CountDownLatch(2);

        // 테스트용 대기 동작은 프록시 안의 spy에 설정한다.
        EventCreationService creationSpy =
                AopTestUtils.getUltimateTargetObject(eventCreationService);

        doAnswer(invocation -> {
            // 두 요청 모두 최초 조회를 통과하고 저장 직전에 도착해야 진행
            writersReady.countDown();

            try {
                if (!writersReady.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "두 요청이 저장 지점에 도착하지 못했습니다."
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "동시 요청 대기가 중단됐습니다.",
                        exception
                );
            }

            // 가짜 저장 결과 대신 실제 저장 메서드 실행
            return invocation.callRealMethod();
        }).when(creationSpy).create(
                serviceKey, TITLE, "evt-race"
        );

        executor = Executors.newFixedThreadPool(2);

        Future<EventIngestionService.Result> firstRequest =
                executor.submit(() -> eventIngestionService.receive(
                        serviceKey, TITLE, "evt-race"
                ));

        Future<EventIngestionService.Result> secondRequest =
                executor.submit(() -> eventIngestionService.receive(
                        serviceKey, TITLE, "evt-race"
                ));

        EventIngestionService.Result first =
                firstRequest.get(20, TimeUnit.SECONDS);

        EventIngestionService.Result second =
                secondRequest.get(20, TimeUnit.SECONDS);

        // 한 요청만 신규 생성이고, 나머지는 기존 결과를 반환해야 함
        int createdCount = (first.created() ? 1 : 0)
                + (second.created() ? 1 : 0);

        assertEquals(1, createdCount);
        assertEquals(first.receipt(), second.receipt());

        // 두 요청이 실제로 신규 저장을 시도한 경쟁 상황인지 확인
        verify(creationSpy, times(2)).create(
                serviceKey, TITLE, "evt-race"
        );

        // 실패한 요청의 사건까지 롤백돼야 두 테이블 모두 1건
        assertCounts(serviceKey, 1);
    }

    private String newServiceKey() {
        String key = "idem-test-" + UUID.randomUUID();
        testServiceKeys.add(key);
        return key;
    }

    private void assertCounts(String key, long expected) {
        long incidentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incidents WHERE service_key = ?",
                Long.class,
                key
        );

        long eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM events WHERE service_key = ?",
                Long.class,
                key
        );

        assertEquals(expected, incidentCount, "사건 개수");
        assertEquals(expected, eventCount, "이벤트 개수");
    }
}