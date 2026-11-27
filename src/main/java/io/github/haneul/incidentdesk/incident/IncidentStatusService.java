package io.github.haneul.incidentdesk.incident;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IncidentStatusService {

    private final IncidentRepository incidentRepository;

    public IncidentStatusService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public String acknowledge(Long incidentId) {
        return transition(incidentId, "OPEN", "ACKNOWLEDGED");
    }

    public String resolve(Long incidentId) {
        return transition(incidentId, "ACKNOWLEDGED", "RESOLVED");
    }

    public void assign(Long incidentId, String assigneeKey) {
                if (incidentRepository.assignIfUnassigned(incidentId, assigneeKey) == 1) {
            incidentRepository.insertPendingNotification(
                    incidentId, assigneeKey, "ASSIGNED"
            );
            incidentRepository.insertHistory(
                    incidentId, "ASSIGNED", null, assigneeKey
            );
            return;
        }

    Incident incident = incidentRepository.findById(incidentId)
        .orElseThrow(IncidentNotFoundException::new);

        if ("OPEN".equals(incident.getStatus())
                && assigneeKey.equals(incident.getAssigneeKey())) {
            return;
        }

        throw new IncidentStatusConflictException();
    }

    public void transfer(Long incidentId, String currentAssignee, String nextAssignee) {
        int updated = incidentRepository.transferIfOpenAndAssignedTo(
                incidentId,
                currentAssignee,
                nextAssignee
        );

               if (updated == 1) {
            incidentRepository.insertPendingNotification(
                    incidentId, nextAssignee, "TRANSFERRED"
            );
            incidentRepository.insertHistory(
                    incidentId, "TRANSFERRED", currentAssignee, nextAssignee
            );
            return;
        }

        if (!incidentRepository.existsById(incidentId)) {
            throw new IncidentNotFoundException();
        }

        throw new IncidentStatusConflictException();
    }    

    private String transition(Long incidentId, String expected, String target) {
        if (incidentRepository.updateStatusIfCurrent(incidentId, expected, target) == 1) {
            incidentRepository.insertHistory(incidentId, target, null, null);
            return target;
        }

        String current = incidentRepository.findById(incidentId)
                .orElseThrow(IncidentNotFoundException::new)
                .getStatus();

        // 이미 목표 상태라면 같은 요청을 다시 보내도 성공으로 처리한다.
        if (target.equals(current)) {
            return current;
        }

        throw new IncidentStatusConflictException();
    }
}