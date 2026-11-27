package io.github.haneul.incidentdesk.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 실제 MySQL에서 사건 연결과 재전송의 동시 접수를 검증한다.
// 실제 조회·저장을 유지하고, 두 요청의 진행 시점만 테스트에서 조절한다.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:${INCIDENT_TEST_DB_PORT:3306}/incident_desk_test",
        "spring.datasource.username=incident_app",
        "spring.datasource.password=${DB_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate"
    }
)
class EventIngestionServiceGroupingConcurrencyTests {

    private static final String INCIDENT_KEY =
            "payment-approval-failure";

    private static final String TITLE =
            "Payment approval failed";

    @Autowired
    private EventIngestionService eventIngestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private EventCreationService eventCreationService;

    private String serviceKey;
    private ExecutorService executor;

    @BeforeEach
    void prepareTest() {
        assertEquals(
                "incident_desk_test",
                jdbcTemplate.queryForObject(
                        "SELECT DATABASE()",
                        String.class
                )
        );

        assertFalse(
                TransactionSynchronizationManager.isActualTransactionActive()
        );

        serviceKey = "group-race-" + UUID.randomUUID();
        assertCounts(0, 0);
    }

    @AfterEach
    void cleanUp() throws InterruptedException {
        if (executor != null) {
            executor.shutdownNow();

            // 작업이 종료된 뒤에만 테스트 데이터를 정리
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "동시 요청이 종료되지 않아 데이터 정리를 중단했습니다."
                );
            }
        }

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

    @ParameterizedTest(name = "sameExternalEventId={0}")
    @ValueSource(booleans = {false, true})
    void handlesConcurrentRequests(
            boolean sameExternalEventId
    ) throws Exception {
        CountDownLatch emptyLookupsCompleted = new CountDownLatch(2);

        // 트랜잭션 프록시는 유지하고 내부 spy에 대기 동작을 설정
        EventCreationService creationSpy =
                AopTestUtils.getUltimateTargetObject(
                        eventCreationService
                );

        doAnswer(invocation -> {
            // 실제 DB 조회 결과를 그대로 사용
            Optional<?> existing =
                    (Optional<?>) invocation.callRealMethod();

            if (existing.isEmpty()) {
                emptyLookupsCompleted.countDown();

                try {
                    // 두 요청이 모두 사건 없음 조회를 마쳐야 저장 진행
                    if (!emptyLookupsCompleted.await(
                            10,
                            TimeUnit.SECONDS
                    )) {
                        throw new IllegalStateException(
                                "두 요청이 사건 조회 지점에 도착하지 못했습니다."
                        );
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();

                    throw new IllegalStateException(
                            "동시 요청 대기가 중단됐습니다.",
                            exception
                    );
                }
            }

            return existing;
        }).when(creationSpy).findOpenIncident(
                serviceKey,
                INCIDENT_KEY
        );

        String secondExternalId = sameExternalEventId
                ? "evt-001"
                : "evt-002";

        executor = Executors.newFixedThreadPool(2);

        Future<EventIngestionService.Result> firstRequest =
                executor.submit(() -> eventIngestionService.receive(
                        serviceKey,
                        TITLE,
                        "evt-001",
                        INCIDENT_KEY
                ));

        Future<EventIngestionService.Result> secondRequest =
                executor.submit(() -> eventIngestionService.receive(
                        serviceKey,
                        TITLE,
                        secondExternalId,
                        INCIDENT_KEY
                ));

        EventIngestionService.Result first =
                firstRequest.get(20, TimeUnit.SECONDS);

        EventIngestionService.Result second =
                secondRequest.get(20, TimeUnit.SECONDS);

        // 두 요청이 같은 사건에 연결돼야 한다.
        assertEquals(
                first.receipt().incidentId(),
                second.receipt().incidentId()
        );

        int incidentCreatedCount =
                (first.incidentCreated() ? 1 : 0)
                + (second.incidentCreated() ? 1 : 0);

        assertEquals(1, incidentCreatedCount);

        int eventCreatedCount =
                (first.created() ? 1 : 0)
                + (second.created() ? 1 : 0);

        if (sameExternalEventId) {
            // 같은 이벤트면 하나만 생성하고 기존 접수 결과 재사용
            assertEquals(1, eventCreatedCount);
            assertEquals(first.receipt(), second.receipt());

            assertCounts(1, 1);

            verify(creationSpy, times(2)).findOpenIncident(
                    serviceKey,
                    INCIDENT_KEY
            );
        } else {
            // 서로 다른 이벤트는 각각 저장
            assertEquals(2, eventCreatedCount);

            assertNotEquals(
                    first.receipt().eventId(),
                    second.receipt().eventId()
            );

            assertCounts(1, 2);

            // 최초 조회 두 번과 사건 생성 충돌 후 재조회 한 번
            verify(creationSpy, times(3)).findOpenIncident(
                    serviceKey,
                    INCIDENT_KEY
            );
        }
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