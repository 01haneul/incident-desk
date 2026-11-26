package io.github.haneul.incidentdesk.event;

import java.time.Instant;

// 접수 결과와 이벤트·사건의 생성 여부를 API 응답으로 전달한다.
// 기존 사건 연결과 동일 이벤트 재전송을 구분할 수 있다.
public record EventCreateResponse(
        Long eventId,
        Long incidentId,
        String status,
        Instant receivedAt,
        boolean eventCreated,
        boolean incidentCreated
) {
}