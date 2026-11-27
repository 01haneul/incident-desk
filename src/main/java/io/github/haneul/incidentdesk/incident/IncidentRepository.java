package io.github.haneul.incidentdesk.incident;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

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

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Incident i
            SET i.status = :target
            WHERE i.id = :incidentId AND i.status = :expected
            """)
    int updateStatusIfCurrent(
            @Param("incidentId") Long incidentId,
            @Param("expected") String expected,
            @Param("target") String target
    );

        @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Incident i SET i.assigneeKey = :assigneeKey
            WHERE i.id = :incidentId AND i.status = 'OPEN'
              AND i.assigneeKey IS NULL
            """)

    int assignIfUnassigned(
            @Param("incidentId") Long incidentId,
            @Param("assigneeKey") String assigneeKey
    );


    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Incident i
            SET i.assigneeKey = :nextAssignee
            WHERE i.id = :incidentId
              AND i.status = 'OPEN'
              AND i.assigneeKey = :currentAssignee
              AND i.assigneeKey <> :nextAssignee
            """)
    int transferIfOpenAndAssignedTo(
            @Param("incidentId") Long incidentId,
            @Param("currentAssignee") String currentAssignee,
            @Param("nextAssignee") String nextAssignee
    );
}