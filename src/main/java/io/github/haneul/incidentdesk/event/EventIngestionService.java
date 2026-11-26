package io.github.haneul.incidentdesk.event;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 재전송을 확인하고 사건 연결을 포함한 접수 처리를 조정한다.
// 저장 실패 처리는 별도 서비스의 트랜잭션이 롤백된 후 실행한다.
@Service
@Transactional(propagation = Propagation.NEVER)
public class EventIngestionService {

    private static final String OPEN_INCIDENT_CONSTRAINT =
            "uk_incidents_service_active";

    private final EventCreationService eventCreationService;
    private final EventRepository eventRepository;

    public EventIngestionService(
            EventCreationService eventCreationService,
            EventRepository eventRepository
    ) {
        this.eventCreationService = eventCreationService;
        this.eventRepository = eventRepository;
    }

    // 기존 두 인자 호출 방식 유지
    public Receipt receive(String serviceKey, String title) {
        return receive(serviceKey, title, null).receipt();
    }

    // 기존 세 인자 호출 방식 유지
    public Result receive(
            String serviceKey,
            String title,
            String externalEventId
    ) {
        return receive(serviceKey, title, externalEventId, null);
    }

    public Result receive(
            String serviceKey,
            String title,
            String externalEventId,
            String incidentKey
    ) {
        Optional<Event> existing = findExistingEvent(
                serviceKey,
                externalEventId
        );

        if (existing.isPresent()) {
            return reuseExisting(
                    existing.get(),
                    title,
                    incidentKey
            );
        }

        return createWithRecovery(
                serviceKey,
                title,
                externalEventId,
                incidentKey,
                true
        );
    }

    private Result createWithRecovery(
            String serviceKey,
            String title,
            String externalEventId,
            String incidentKey,
            boolean groupingRetryAllowed
    ) {
        try {
            // 기존 호출 경로를 유지해 이전 동작과 테스트 보존
            if (incidentKey == null) {
                Receipt receipt = eventCreationService.create(
                        serviceKey,
                        title,
                        externalEventId
                );

                return new Result(receipt, true);
            }

            return eventCreationService.create(
                    serviceKey,
                    title,
                    externalEventId,
                    incidentKey
            );
        } catch (DataIntegrityViolationException exception) {
            // 저장 서비스의 트랜잭션은 이 시점에 이미 롤백됐다.
            Optional<Event> concurrentlyCreated = findExistingEvent(
                    serviceKey,
                    externalEventId
            );

            // 같은 이벤트를 다른 요청이 먼저 저장했는지 우선 확인
            if (concurrentlyCreated.isPresent()) {
                return reuseExisting(
                        concurrentlyCreated.get(),
                        title,
                        incidentKey
                );
            }

            // OPEN 사건 생성 충돌만 새 트랜잭션에서 한 번 재시도
            if (incidentKey != null
                    && groupingRetryAllowed
                    && isOpenIncidentConflict(exception)) {
                return createWithRecovery(
                        serviceKey,
                        title,
                        externalEventId,
                        incidentKey,
                        false
                );
            }

            throw exception;
        }
    }

    private Optional<Event> findExistingEvent(
            String serviceKey,
            String externalEventId
    ) {
        // 외부 번호가 없는 요청에는 재전송 조회를 적용하지 않음
        if (externalEventId == null) {
            return Optional.empty();
        }

        return eventRepository.findByServiceKeyAndExternalEventId(
                serviceKey,
                externalEventId
        );
    }

    private Result reuseExisting(
            Event event,
            String requestedTitle,
            String requestedIncidentKey
    ) {
        // 재전송은 제목과 사건 연결 키까지 원래 요청과 같아야 한다.
        if (!event.getTitle().equals(requestedTitle)
                || !Objects.equals(
                        event.getIncidentKey(),
                        requestedIncidentKey
                )) {
            throw new EventIdConflictException();
        }

        Receipt receipt = new Receipt(
                event.getId(),
                event.getIncident().getId(),
                event.getIncident().getStatus(),
                event.getReceivedAt().toInstant(ZoneOffset.UTC)
        );

        return new Result(receipt, false, false);
    }

    private boolean isOpenIncidentConflict(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();

                // MySQL이 테이블 이름을 붙여 반환하는 경우도 허용
                if (name != null
                        && (name.equals(OPEN_INCIDENT_CONSTRAINT)
                        || name.endsWith(
                                "." + OPEN_INCIDENT_CONSTRAINT
                        ))) {
                    return true;
                }
            }

            cause = cause.getCause();
        }

        return false;
    }

    public record Receipt(
            Long eventId,
            Long incidentId,
            String status,
            Instant receivedAt
    ) {
    }

    // created는 새 이벤트 생성 여부, incidentCreated는 새 사건 생성 여부
    public record Result(
            Receipt receipt,
            boolean created,
            boolean incidentCreated
    ) {
        // 기존 코드와 테스트에서 사용하던 생성 방식 유지
        public Result(Receipt receipt, boolean created) {
            this(receipt, created, created);
        }
    }
}