package io.github.haneul.incidentdesk.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.haneul.incidentdesk.incident.Incident;
import io.github.haneul.incidentdesk.incident.IncidentRepository;


// 장애 이벤트를 접수하고 새 장애 사건과 이벤트를 함께 저장한다.
// 두 저장을 하나의 트랜잭션으로 묶어 부분 저장을 방지한다.

// @Service : Spring이 이 클래스를 서비스 객체로 생성·관리
// 서비스가 사용할 저장·조회 도구를 Spring에서 전달받음
@Service
public class EventIngestionService {
    // final : 생성자로 받은 리포지토리 참조를 이후 재할당 하지 않음
    private final IncidentRepository incidentRepository;
    private final EventRepository eventRepository;

    // Spring이 생성한 리포지토리를 생성자로 전달받는다.
    public EventIngestionService(
            IncidentRepository incidentRepository,
            EventRepository eventRepository
    ) {
        this.incidentRepository = incidentRepository;
        this.eventRepository = eventRepository;
    }
    // 외부에서 이 서비스를 호출할 때 트랜잭션 경계 적용
    @Transactional
    public Receipt receive(String serviceKey, String title) {
        // DB의 DATETIME(6) 정밀도에 맞춰 UTC 시각을 생성한다.
        LocalDateTime receivedAt = LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);

        Incident incident = new Incident(serviceKey, title, receivedAt);
        // incidentRepository.save() : 사건 저장
        Incident savedIncident = incidentRepository.save(incident);

        // 먼저 저장한 사건을 참조하도록 이벤트를 연결한다.
        Event event = new Event(
                savedIncident,
                serviceKey,
                title,
                receivedAt
        );
        // eventRepository.save() : 사건과 연결된 이벤트 저장
        Event savedEvent = eventRepository.save(event);

        // 엔티티 대신 응답에 필요한 값만 반환한다.
        // Receipt : 호출자에게 전달할 접수 결과
        return new Receipt(
                savedEvent.getId(),
                savedIncident.getId(),
                savedIncident.getStatus(),
                receivedAt.toInstant(ZoneOffset.UTC)
        );
    }

    
    // 접수 결과를 전달하는 데이터 객체.
    // API 계약에 맞춰 식별자, 상태, UTC 접수 시각을 담는다.
    // record : 결과값을 묶어 전달하기 위한 java 문법
    public record Receipt(
            Long eventId,
            Long incidentId,
            String status,
            Instant receivedAt
    ) {
    }
}