package io.github.haneul.incidentdesk.event;

import java.time.LocalDateTime;
import java.util.Objects;

import io.github.haneul.incidentdesk.incident.Incident;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 접수 당시의 장애 정보를 보존하고 장애 사건에 연결한다.
// 외부 이벤트 번호는 재전송을 식별하기 위한 선택 값이다.
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "service_key", nullable = false, length = 100)
    private String serviceKey;

    @Column(name = "external_event_id", length = 100)
    private String externalEventId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    protected Event() {
    }

    // 기존 생성 방식 유지: 외부 번호가 없는 이벤트
    public Event(
            Incident incident,
            String serviceKey,
            String title,
            LocalDateTime receivedAt
    ) {
        this(incident, serviceKey, title, receivedAt, null);
    }

    // 외부 번호를 포함할 수 있는 생성 방식
    public Event(
            Incident incident,
            String serviceKey,
            String title,
            LocalDateTime receivedAt,
            String externalEventId
    ) {
        this.incident = Objects.requireNonNull(incident);
        this.serviceKey = Objects.requireNonNull(serviceKey);
        this.title = Objects.requireNonNull(title);
        this.receivedAt = Objects.requireNonNull(receivedAt);
        this.externalEventId = externalEventId;
    }

    public Long getId() {
        return id;
    }

    public Incident getIncident() {
        return incident;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}