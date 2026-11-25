package io.github.haneul.incidentdesk.incident;

import java.time.Instant;

// 장애 사건 상세 조회에 필요한 값만 전달하는 응답 객체
// DB 엔티티와 API 응답 구조를 분리한다.

public record IncidentDetailResponse(
        Long incidentId,
        String serviceKey,
        String title,
        String status,
        Instant createdAt
) {
}