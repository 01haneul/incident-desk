package io.github.haneul.incidentdesk.event;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

// 이벤트 저장과 조회를 담당한다.
// 서비스 식별자와 외부 번호로 기존 접수를 찾을 수 있다.
public interface EventRepository extends JpaRepository<Event, Long> {

    // 외부 번호가 있는 요청에 사용하며, 연결된 사건도 함께 조회한다.
    @EntityGraph(attributePaths = "incident")
    Optional<Event> findByServiceKeyAndExternalEventId(
            String serviceKey,
            String externalEventId
    );
}