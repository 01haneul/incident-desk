package io.github.haneul.incidentdesk.event;

// 이미 접수한 외부 이벤트 번호에 다른 내용이 전달됐음을 표현한다.
// 웹 계층에서 HTTP 409 응답으로 변환할 예외다.
public class EventIdConflictException extends RuntimeException {

    public EventIdConflictException() {
        super("동일한 외부 이벤트 번호에 다른 내용이 전달됐습니다.");
    }
}