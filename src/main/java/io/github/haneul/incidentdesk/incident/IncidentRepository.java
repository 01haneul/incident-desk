package io.github.haneul.incidentdesk.incident;

import org.springframework.data.jpa.repository.JpaRepository;

// 장애 사건의 저장과 조회를 담당하는 리포지토리
// Spring Data JPA가 실행 시 구현체를 생성한다
public interface IncidentRepository extends JpaRepository<Incident, Long> {
}
