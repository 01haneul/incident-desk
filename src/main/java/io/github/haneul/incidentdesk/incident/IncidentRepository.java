package io.github.haneul.incidentdesk.incident;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 장애 사건의 저장과 조회를 담당한다.
// DB가 계산한 active_incident_key로 연결 가능한 OPEN 사건을 찾는다.
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    // incidentKey가 있는 요청에서 사용할 조회 메서드
    @Query(
            value = """
                    SELECT i.*
                    FROM incidents i
                    WHERE i.service_key = :serviceKey
                      AND i.active_incident_key = :incidentKey
                    """,
            nativeQuery = true
    )
    Optional<Incident> findOpenByServiceKeyAndIncidentKey(
            @Param("serviceKey") String serviceKey,
            @Param("incidentKey") String incidentKey
    );
}