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

// 이벤트 접수 당시의 정보와 사건 연결 키를 보관한다.
// externalEventId는 재전송 식별, incidentKey는 사건 연결에 사용한다.
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

    @Column(name = "incident_key", length = 100)
    private String incidentKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    protected Event() {
    }

    // 기존 호출 방식 유지: 외부 이벤트 번호와 사건 연결 키 없음
    public Event(
            Incident incident,
            String serviceKey,
            String title,
            LocalDateTime receivedAt
    ) {
        this(incident, serviceKey, title, receivedAt, null, null);
    }

    // 기존 호출 방식 유지: 외부 이벤트 번호만 포함
    public Event(
            Incident incident,
            String serviceKey,
            String title,
            LocalDateTime receivedAt,
            String externalEventId
    ) {
        this(
                incident,
                serviceKey,
                title,
                receivedAt,
                externalEventId,
                null
        );
    }

    // 외부 이벤트 번호와 사건 연결 키를 모두 포함
    public Event(
            Incident incident,
            String serviceKey,
            String title,
            LocalDateTime receivedAt,
            String externalEventId,
            String incidentKey
    ) {
        this.incident = Objects.requireNonNull(incident);
        this.serviceKey = Objects.requireNonNull(serviceKey);
        this.title = Objects.requireNonNull(title);
        this.receivedAt = Objects.requireNonNull(receivedAt);
        this.externalEventId = externalEventId;
        this.incidentKey = incidentKey;
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

    public String getIncidentKey() {
        return incidentKey;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}