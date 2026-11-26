package io.github.haneul.incidentdesk.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.haneul.incidentdesk.common.ApiExceptionHandler;

// 외부 이벤트 번호의 허용 문자와 길이 경계를 검사한다.
// 실제 DB 저장 없이 입력 검증과 서비스 전달 여부를 확인한다.
@WebMvcTest(EventController.class)
@Import(ApiExceptionHandler.class)
class EventExternalIdValidationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventIngestionService eventIngestionService;

    @ParameterizedTest(name = "invalid externalEventId case {index}")
    @MethodSource("invalidExternalEventIds")
    void rejectsInvalidExternalEventId(
            String externalEventId
    ) throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(externalEventId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("externalEventId"));

        // 잘못된 외부 번호는 접수 서비스에 전달하지 않음
        verifyNoInteractions(eventIngestionService);
    }

    // 다섯 값을 각각 별도의 테스트 실행에 사용
    static Stream<String> invalidExternalEventIds() {
        return Stream.of(
                "",
                "   ",
                "evt/001",
                "이벤트-001",
                "a".repeat(101)
        );
    }

    @Test
    void acceptsExternalEventIdAtMaximumLength() throws Exception {
        String externalEventId = "a".repeat(100);

        EventIngestionService.Receipt receipt =
                new EventIngestionService.Receipt(
                        10L,
                        20L,
                        "OPEN",
                        Instant.parse("2026-11-26T00:00:00Z")
                );

        given(eventIngestionService.receive(
                "payment-api",
                "Payment approval failed",
                externalEventId
        )).willReturn(new EventIngestionService.Result(receipt, true));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(externalEventId)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/incidents/20"
                ))
                .andExpect(jsonPath("$.eventId").value(10))
                .andExpect(jsonPath("$.incidentId").value(20));

        // 100자 전체가 변경 없이 서비스에 전달됐는지 확인
        verify(eventIngestionService).receive(
                "payment-api",
                "Payment approval failed",
                externalEventId
        );
    }

    // 이 테스트에서 사용하는 고정 형식의 JSON 생성
    private String requestBody(String externalEventId) {
        return """
                {
                  "serviceKey": "payment-api",
                  "title": "Payment approval failed",
                  "externalEventId": "%s"
                }
                """.formatted(externalEventId);
    }
}