package io.github.haneul.incidentdesk.incident;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import io.github.haneul.incidentdesk.common.ApiExceptionHandler;

// 사건 조회의 입력 검증, 응답 JSON, 예외 응답을 자동으로 검사한다.
// 조회 서비스는 테스트용 객체로 대체하므로 실제 MySQL에는 접근하지 않는다.
@WebMvcTest(IncidentController.class)
@Import(ApiExceptionHandler.class)
class IncidentControllerWebTests {

    // 실제 서버를 켜지 않고 Spring MVC에 요청을 전달하는 테스트 도구
    @Autowired
    private MockMvc mockMvc;

    // 테스트마다 조회 결과나 예외를 지정할 수 있는 서비스 대역
    @MockitoBean
    private IncidentQueryService incidentQueryService;

    @MockitoBean
    private IncidentStatusService statusService;

    @Test
    void returnsIncidentWhenFound() throws Exception {
        Instant createdAt = Instant.parse("2026-11-24T07:39:07.814095Z");

        IncidentDetailResponse detail = new IncidentDetailResponse(
                1L,
                "payment-api",
                "Payment approval failed",
                "OPEN",
                createdAt
        );

        // 사건 1번을 조회하면 준비한 결과를 반환하도록 설정
        given(incidentQueryService.getDetail(1L)).willReturn(detail);

        // HTTP 상태와 응답의 모든 필드가 계약과 일치하는지 검사
        mockMvc.perform(get("/api/incidents/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.incidentId").value(1))
                .andExpect(jsonPath("$.serviceKey").value("payment-api"))
                .andExpect(jsonPath("$.title").value("Payment approval failed"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.createdAt").value(createdAt.toString()));
    }

    @Test
    void returns404WhenIncidentIsMissing() throws Exception {
        // 서비스가 사건 없음 예외를 발생시키는 상황 재현
        given(incidentQueryService.getDetail(999999L))
                .willThrow(new IncidentNotFoundException());

        // 예외 처리기가 404와 공통 오류 응답으로 변환하는지 검사
        mockMvc.perform(get("/api/incidents/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INCIDENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("장애 사건을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @ParameterizedTest(name = "incidentId={0} -> 400")
    @ValueSource(strings = {
            "0",
            "-1",
            "abc",
            "9223372036854775808"
    })
    void rejectsInvalidIncidentId(String incidentId) throws Exception {
        // 네 가지 입력에 대해 같은 검사를 각각 실행
        mockMvc.perform(get("/api/incidents/{incidentId}", incidentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());

        // 잘못된 번호는 조회 서비스까지 전달되지 않아야 함
        verifyNoInteractions(incidentQueryService);
    }

    @Test
    void hidesInternalDetailsWhenUnexpectedErrorOccurs() throws Exception {
        // 실제 비밀값이 아닌 테스트용 내부 오류 문구로 예외 발생
        given(incidentQueryService.getDetail(1L))
                .willThrow(new IllegalStateException(
                        "TEST_ONLY_INTERNAL_DETAIL"
                ));

        // 500 응답이 정해진 세 필드와 값으로만 구성되는지 검사
        // 내부 예외 메시지나 스택 트레이스가 추가되면 실패한다.
        mockMvc.perform(get("/api/incidents/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(content().json("""
                        {
                          "code": "INTERNAL_ERROR",
                          "message": "요청 처리 중 오류가 발생했습니다.",
                          "fieldErrors": []
                        }
                        """, JsonCompareMode.STRICT));
    }
}