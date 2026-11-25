package io.github.haneul.incidentdesk.incident;

import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 사건 번호로 DB를 조회하고, 조회 결과를 API 응답 객체로 변환한다.
// 존재하지 않는 사건은 전용 예외로 구분한다.
@Service
public class IncidentQueryService {

    private final IncidentRepository incidentRepository;

    public IncidentQueryService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @Transactional(readOnly = true)
    public IncidentDetailResponse getDetail(Long incidentId) {
        // 기본키로 조회하고, 결과가 없으면 사건 없음 예외를 발생시킨다.
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(IncidentNotFoundException::new);

        // DB의 생성 시각은 UTC로 저장한다는 기존 규칙에 따라 변환한다.
        return new IncidentDetailResponse(
                incident.getId(),
                incident.getServiceKey(),
                incident.getTitle(),
                incident.getStatus(),
                incident.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }
}