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


// 접수 당시의 장애 정보를 보존하며 events 테이블과 연결되는 엔티티.
// 각 이벤트는 하나의 장애 사건에 속하고, 접수 시각은 UTC 기준으로 전달받는다.

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 여러 이벤트가 하나의 사건에 연결되며, 사건 자동 저장·삭제는 하지 않는다.
    // 여러 이벤트가 하나의 사건을 참조하는 관계 지정 
    // optional = false 사건 연결이 필수라는 JPA 설정
    // Fetch.LAZY 연결된 사건의 데이터는 필요할 때 조회하도록 지정
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    // DB에서는 사건의 기본키를 incident_id에 저장
    @JoinColumn(name = "incident_id", nullable = false)
    // 이벤트가 속한 장애 사건을 객체로 표현
    private Incident incident;

    @Column(name = "service_key", nullable = false, length = 100)
    private String serviceKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    // JPA가 DB 조회 결과로 객체를 생성할 때 사용하는 기본 생성자
    protected Event() {
    }

    public Event(
            Incident incident,
            String serviceKey,
            String title,
            LocalDateTime receivedAt
    ) {
        this.incident = Objects.requireNonNull(incident);
        this.serviceKey = Objects.requireNonNull(serviceKey);
        this.title = Objects.requireNonNull(title);
        this.receivedAt = Objects.requireNonNull(receivedAt);
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

    public String getTitle() {
        return title;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}