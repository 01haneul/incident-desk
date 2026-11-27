package io.github.haneul.incidentdesk.incident;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IncidentQueryService {

    private final IncidentRepository incidentRepository;
    private final JdbcTemplate jdbc;

    public IncidentQueryService(
            IncidentRepository incidentRepository,
            JdbcTemplate jdbc
    ) {
        this.incidentRepository = incidentRepository;
        this.jdbc = jdbc;
    }

    public IncidentDetailResponse getDetail(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(IncidentNotFoundException::new);

        return new IncidentDetailResponse(
                incident.getId(),
                incident.getServiceKey(),
                incident.getTitle(),
                incident.getStatus(),
                incident.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    public List<HistoryEntry> getHistory(Long incidentId, long afterId) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new IncidentNotFoundException();
        }

        return jdbc.query("""
                SELECT id, action_type, previous_assignee_key,
                       new_assignee_key, occurred_at
                FROM incident_history
                WHERE incident_id = ? AND id > ?
                ORDER BY id
                LIMIT 50
                """,
                (rs, rowNum) -> new HistoryEntry(
                        rs.getLong("id"),
                        rs.getString("action_type"),
                        rs.getString("previous_assignee_key"),
                        rs.getString("new_assignee_key"),
                        rs.getObject("occurred_at", LocalDateTime.class)
                                .toInstant(ZoneOffset.UTC)
                ),
                incidentId,
                afterId
        );
    }

    public record HistoryEntry(
            long historyId,
            String actionType,
            String previousAssigneeKey,
            String newAssigneeKey,
            Instant occurredAt
    ) {
    }
}