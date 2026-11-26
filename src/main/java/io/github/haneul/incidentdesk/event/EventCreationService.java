package io.github.haneul.incidentdesk.event;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.haneul.incidentdesk.incident.Incident;
import io.github.haneul.incidentdesk.incident.IncidentRepository;

// 새 사건과 이벤트를 하나의 트랜잭션으로 저장한다.
// 실패하면 두 저장 모두 롤백하고 예외를 호출자에게 전달한다.
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

    @Transactional
    public EventIngestionService.Receipt create(
            String serviceKey,
            String title,
            String externalEventId
    ) {
        LocalDateTime receivedAt = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);

        Incident incident = incidentRepository.save(
                new Incident(serviceKey, title, receivedAt)
        );

        Event event = eventRepository.save(
                new Event(
                        incident,
                        serviceKey,
                        title,
                        receivedAt,
                        externalEventId
                )
        );

        return new EventIngestionService.Receipt(
                event.getId(),
                incident.getId(),
                incident.getStatus(),
                receivedAt.toInstant(ZoneOffset.UTC)
        );
    }
}