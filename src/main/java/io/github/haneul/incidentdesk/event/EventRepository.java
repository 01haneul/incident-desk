package io.github.haneul.incidentdesk.event;

import org.springframework.data.jpa.repository.JpaRepository;

// 수신 이벤트의 저장과 조회를 담당하는 리포지토리
// Spring Data JPA가 실행 시 구현체를 생성한다
// interface : 사용할 기능의 계약을 정의하는 java 문법
// extends JpaRepository : Spring Data JPA의 기본 기능을 상속
// <Incident, Long> : 관리할 엔티티는 Incident, 식별자 타입은 Long
// <Event, Long> : 관리할 엔티티는 Event, 식별자 타입은 Long
public interface EventRepository extends JpaRepository<Event, Long> {
    
}


// 기본 저장·조회 기능을 JpaRepository 에서 제공함.