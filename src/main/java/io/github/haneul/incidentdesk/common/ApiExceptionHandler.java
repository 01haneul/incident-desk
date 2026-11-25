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

import io.github.haneul.incidentdesk.incident.IncidentNotFoundException;

// API 예외를 공통 오류 응답으로 변환한다.
// Spring MVC의 기본 상태 코드와 헤더를 유지하고, 내부 오류 내용은 숨긴다.
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    // 정상적인 번호지만 해당 사건이 없으면 404로 응답한다.
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

    // 입력 검증, 숫자 변환, 잘못된 JSON 등 Spring MVC 예외의 응답 형식을 통일한다.
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<ApiErrorResponse.FieldErrorDetail> fieldErrors = List.of();

        // 요청 본문의 필드 검증 오류는 필드명과 안내 메시지를 제공한다.
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

    // 별도로 처리하지 않은 예외는 내부 정보를 숨기고 500으로 응답한다.
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