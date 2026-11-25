package io.github.haneul.incidentdesk.incident;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Positive;

// 장애 사건 상세 조회 요청을 받아 조회 서비스에 전달한다.
// 사건 번호는 양수만 허용하고, 정상 결과는 HTTP 200으로 반환한다.
@RestController
public class IncidentController {

    private final IncidentQueryService incidentQueryService;

    public IncidentController(IncidentQueryService incidentQueryService) {
        this.incidentQueryService = incidentQueryService;
    }

    @GetMapping("/api/incidents/{incidentId}")
    public IncidentDetailResponse getDetail(
            @PathVariable("incidentId")
            @Positive(message = "사건 번호는 양의 정수여야 합니다.")
            Long incidentId
    ) {
        return incidentQueryService.getDetail(incidentId);
    }
}