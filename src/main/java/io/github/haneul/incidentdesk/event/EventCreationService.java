package io.github.haneul.incidentdesk.event;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.haneul.incidentdesk.incident.Incident;
import io.github.haneul.incidentdesk.incident.IncidentRepository;

// 기존 OPEN 사건을 찾거나 새 사건을 만든 뒤 이벤트를 저장한다.
// 사건 생성과 이벤트 저장은 하나의 트랜잭션으로 처리한다.
@Service
public class EventCreationService {

    private final IncidentRepository incidentRepository;
    private final EventRepository eventRepository;

    public EventCreationService(
            IncidentRepository incidentRepository,
            EventRepository eventRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.eventRepository = eventRepository;
    }

    // 기존 호출 방식 유지: 연결 키 없이 새 사건과 이벤트 생성
    @Transactional
    public EventIngestionService.Receipt create(
            String serviceKey,
            String title,
            String externalEventId
    ) {
        return saveEvent(
                serviceKey,
                title,
                externalEventId,
                null
        ).receipt();
    }

    // 연결 키가 있는 요청을 처리하는 새로운 호출 방식
    @Transactional
    public EventIngestionService.Result create(
            String serviceKey,
            String title,
            String externalEventId,
            String incidentKey
    ) {
        return saveEvent(
                serviceKey,
                title,
                externalEventId,
                incidentKey
        );
    }

    private EventIngestionService.Result saveEvent(
            String serviceKey,
            String title,
            String externalEventId,
            String incidentKey
    ) {
        Optional<Incident> existingIncident = incidentKey == null
                ? Optional.empty()
                : findOpenIncident(serviceKey, incidentKey);

        boolean incidentCreated = existingIncident.isEmpty();

        LocalDateTime receivedAt = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);

        // 기존 사건이 있으면 제목과 생성 시각을 변경하지 않고 사용
        Incident incident = existingIncident.orElseGet(
                () -> incidentRepository.save(
                        new Incident(
                                serviceKey,
                                title,
                                receivedAt,
                                incidentKey
                        )
                )
        );

        // 사건을 재사용하더라도 새 알림은 별도 이벤트로 보관
        Event event = eventRepository.save(
                new Event(
                        incident,
                        serviceKey,
                        title,
                        receivedAt,
                        externalEventId,
                        incidentKey
                )
        );

        EventIngestionService.Receipt receipt =
                new EventIngestionService.Receipt(
                        event.getId(),
                        incident.getId(),
                        incident.getStatus(),
                        receivedAt.toInstant(ZoneOffset.UTC)
                );

        return new EventIngestionService.Result(
                receipt,
                true,
                incidentCreated
        );
    }

    // create 메서드가 시작한 트랜잭션 안에서 기존 사건 조회
    Optional<Incident> findOpenIncident(
            String serviceKey,
            String incidentKey
    ) {
        return incidentRepository.findOpenByServiceKeyAndIncidentKey(
                serviceKey,
                incidentKey
        );
    }
}