package io.github.haneul.incidentdesk.common;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.haneul.incidentdesk.event.EventIdConflictException;
import io.github.haneul.incidentdesk.incident.IncidentNotFoundException;

// API 예외를 공통 오류 응답으로 변환한다.
// 상태 코드와 필요한 헤더를 유지하며 내부 오류 내용은 응답에서 숨긴다.
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleIncidentNotFound(
            IncidentNotFoundException exception
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                "INCIDENT_NOT_FOUND",
                "장애 사건을 찾을 수 없습니다.",
                List.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // 같은 외부 이벤트 번호에 다른 내용이 전달되면 409로 응답
    @ExceptionHandler(EventIdConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleEventIdConflict(
            EventIdConflictException exception
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                "EVENT_ID_CONFLICT",
                "동일한 외부 이벤트 번호에 다른 내용이 전달됐습니다.",
                List.of()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<ApiErrorResponse.FieldErrorDetail> fieldErrors = List.of();

        if (exception instanceof MethodArgumentNotValidException validation) {
            fieldErrors = validation.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(error -> new ApiErrorResponse.FieldErrorDetail(
                            error.getField(),
                            error.getDefaultMessage() != null
                                    ? error.getDefaultMessage()
                                    : "입력값을 확인해주세요."
                    ))
                    .toList();
        }

        boolean serverError = status.is5xxServerError();

        if (serverError) {
            logger.error("Spring MVC 요청 처리 중 서버 오류", exception);
        }

        ApiErrorResponse errorBody = new ApiErrorResponse(
                serverError ? "INTERNAL_ERROR" : "INVALID_REQUEST",
                serverError
                        ? "요청 처리 중 오류가 발생했습니다."
                        : "입력값을 확인해주세요.",
                fieldErrors
        );

        return super.handleExceptionInternal(
                exception,
                errorBody,
                headers,
                status,
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception
    ) {
        logger.error("예상하지 못한 API 오류", exception);

        ApiErrorResponse body = new ApiErrorResponse(
                "INTERNAL_ERROR",
                "요청 처리 중 오류가 발생했습니다.",
                List.of()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }
}