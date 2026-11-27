package io.github.haneul.incidentdesk.incident;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 장애 사건을 저장하며 incidentKey로 같은 장애 유형을 구분한다.
// 새 사건의 상태는 OPEN이고, 생성 시각은 UTC 기준으로 전달받는다.
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_key", nullable = false, length = 100)
    private String serviceKey;

    @Column(name = "incident_key", length = 100)
    private String incidentKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "assignee_key", length = 100)
    private String assigneeKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // JPA가 조회한 데이터로 객체를 만들 때 사용
    protected Incident() {
    }

    // 기존 호출 방식 유지: 사건 연결 키가 없는 요청
    public Incident(
            String serviceKey,
            String title,
            LocalDateTime createdAt
    ) {
        this(serviceKey, title, createdAt, null);
    }

    // 사건 연결 키를 포함하는 생성 방식
    public Incident(
            String serviceKey,
            String title,
            LocalDateTime createdAt,
            String incidentKey
    ) {
        this.serviceKey = Objects.requireNonNull(serviceKey);
        this.incidentKey = incidentKey;
        this.title = Objects.requireNonNull(title);
        this.status = "OPEN";
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public String getIncidentKey() {
        return incidentKey;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public String getAssigneeKey() {
        return assigneeKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}