package io.github.haneul.incidentdesk.common;

import java.util.List;

// API 계약에 맞춰 오류 코드, 안내 메시지, 필드별 오류를 전달한다.
// 예외 객체나 DB 내부 정보는 응답에 포함하지 않는다.
public record ApiErrorResponse(
        String code,
        String message,
        List<FieldErrorDetail> fieldErrors
) {

    // 입력 오류가 발생한 필드와 안내 메시지
    public record FieldErrorDetail(
            String field,
            String message
    ) {
    }
}