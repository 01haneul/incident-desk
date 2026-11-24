package io.github.haneul.incidentdesk.incident;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


// 장애 사건 한 건을 표현하며 incidents 테이블과 연결되는 엔티티.
// 새 사건의 초기 상태는 OPEN이고, 생성 시각은 UTC 기준으로 전달받는다.

// JPA가 저장·조회할 대상으로 인식
@Entity
// 연결할 DB 테이블 지정
@Table(name = "incidents")
public class Incident {
    // 객체의 식별자이자 DB 기본키
    @Id 
    // MySQL의 AUTO_INCREMENT 로 생성한 번호 사용
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // java의 serviceKey를 DB의 service_key에 연결
    @Column(name = "service_key", nullable = false, length = 100)
    private String serviceKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // JPA가 사용할 매개변수 없는 생성자
    protected Incident() {
    }

    // 새 사건은 호출자가 상태를 지정하지 않고 항상 OPEN으로 생성
    public Incident(
            String serviceKey,
            String title,
            LocalDateTime createdAt
    ) {
        this.serviceKey = Objects.requireNonNull(serviceKey);
        this.title = Objects.requireNonNull(title);
        // java에서 생성할 때도 초기 상태 보장
        this.status = "OPEN";
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}