package io.github.haneul.incidentdesk.event;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 외부 이벤트 번호로 재전송을 판단하고 접수 결과를 반환한다.
// 저장은 별도 서비스에 맡겨, 롤백 완료 후 기존 결과를 조회한다.
@Service
@Transactional(propagation = Propagation.NEVER)
public class EventIngestionService {

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

    public Result receive(
            String serviceKey,
            String title,
            String externalEventId
    ) {
        // 외부 번호가 없으면 기존처럼 항상 새로 접수
        if (externalEventId == null) {
            Receipt receipt = eventCreationService.create(
                    serviceKey,
                    title,
                    null
            );

            return new Result(receipt, true);
        }

        Optional<Event> existing =
                eventRepository.findByServiceKeyAndExternalEventId(
                        serviceKey,
                        externalEventId
                );

        if (existing.isPresent()) {
            return reuseExisting(existing.get(), title);
        }

        try {
            Receipt receipt = eventCreationService.create(
                    serviceKey,
                    title,
                    externalEventId
            );

            return new Result(receipt, true);
        } catch (DataIntegrityViolationException exception) {
            // 최초 조회 이후 다른 요청이 먼저 저장했을 가능성 확인.
            // create 호출의 트랜잭션은 이미 롤백된 상태다.
            Optional<Event> concurrentlyCreated =
                    eventRepository.findByServiceKeyAndExternalEventId(
                            serviceKey,
                            externalEventId
                    );

            if (concurrentlyCreated.isPresent()) {
                return reuseExisting(concurrentlyCreated.get(), title);
            }

            // 기존 접수가 없다면 다른 저장 오류이므로 그대로 전달
            throw exception;
        }
    }

    private Result reuseExisting(Event event, String requestedTitle) {
        // 같은 번호라도 제목이 다르면 동일 이벤트로 인정하지 않음
        if (!event.getTitle().equals(requestedTitle)) {
            throw new EventIdConflictException();
        }

        Receipt receipt = new Receipt(
                event.getId(),
                event.getIncident().getId(),
                event.getIncident().getStatus(),
                event.getReceivedAt().toInstant(ZoneOffset.UTC)
        );

        return new Result(receipt, false);
    }

    // 외부로 반환할 접수 결과
    public record Receipt(
            Long eventId,
            Long incidentId,
            String status,
            Instant receivedAt
    ) {
    }

    // 이번 요청에서 새로 생성했는지를 Controller에 전달
    public record Result(
            Receipt receipt,
            boolean created
    ) {
    }
}