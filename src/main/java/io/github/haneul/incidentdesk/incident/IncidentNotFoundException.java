package io.github.haneul.incidentdesk.incident;

// 요청한 번호의 장애 사건이 존재하지 않음을 표현한다.
// HTTP 404 응답으로 변환하는 처리는 웹 계층에서 담당한다.
public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException() {
        super("장애 사건을 찾을 수 없습니다.");
    }
}