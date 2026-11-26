package io.github.haneul.incidentdesk.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import io.github.haneul.incidentdesk.common.ApiExceptionHandler;

// 접수 API의 신규·재사용·충돌 응답과 입력 오류를 검사한다.
// 서비스 대역을 사용하므로 실제 DB 중복 처리는 별도로 검증한다.
@WebMvcTest(EventController.class)
@Import(ApiExceptionHandler.class)
class EventControllerWebTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventIngestionService eventIngestionService;

    @Test
    void returns201WhenExternalEventIdIsOmitted() throws Exception {
        EventIngestionService.Receipt receipt = sampleReceipt();

        given(eventIngestionService.receive(
                "payment-api",
                "Payment approval failed",
                null
        )).willReturn(new EventIngestionService.Result(receipt, true));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceKey": "payment-api",
                                  "title": "Payment approval failed"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/incidents/20"))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.eventId").value(10))
                .andExpect(jsonPath("$.incidentId").value(20))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.receivedAt")
                        .value(receipt.receivedAt().toString()));

        verify(eventIngestionService).receive(
                "payment-api",
                "Payment approval failed",
                null
        );
    }

    // // 신규 생성과 기존 결과 재사용을 각각 한 건씩 검사한다.
    @ParameterizedTest(name = "created={0} -> HTTP {1}")
    @CsvSource({
            "true, 201",
            "false, 200"
    })
    void returnsExpectedStatusForExternalEvent(
            boolean created,
            int expectedStatus
    ) throws Exception {
        EventIngestionService.Receipt receipt = sampleReceipt();

        given(eventIngestionService.receive(
                "payment-api",
                "Payment approval failed",
                "evt-001"
        )).willReturn(new EventIngestionService.Result(receipt, created));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceKey": "payment-api",
                                  "externalEventId": "evt-001",
                                  "title": "Payment approval failed"
                                }
                                """))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().string("Location", "/api/incidents/20"))
                .andExpect(jsonPath("$.eventId").value(10))
                .andExpect(jsonPath("$.incidentId").value(20))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.receivedAt")
                        .value(receipt.receivedAt().toString()));

        verify(eventIngestionService).receive(
                "payment-api",
                "Payment approval failed",
                "evt-001"
        );
    }

    @Test
    void returns409WhenExternalEventIdConflicts() throws Exception {
        given(eventIngestionService.receive(
                "payment-api",
                "Different title",
                "evt-001"
        )).willThrow(new EventIdConflictException());

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceKey": "payment-api",
                                  "externalEventId": "evt-001",
                                  "title": "Different title"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().json("""
                        {
                          "code": "EVENT_ID_CONFLICT",
                          "message": "동일한 외부 이벤트 번호에 다른 내용이 전달됐습니다.",
                          "fieldErrors": []
                        }
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void rejectsBlankTitleBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceKey": "payment-api",
                                  "title": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "code": "INVALID_REQUEST",
                          "message": "입력값을 확인해주세요.",
                          "fieldErrors": [
                            {
                              "field": "title",
                              "message": "제목은 공백일 수 없습니다."
                            }
                          ]
                        }
                        """, JsonCompareMode.STRICT));

        verifyNoInteractions(eventIngestionService);
    }

    @Test
    void rejectsMalformedJsonBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "code": "INVALID_REQUEST",
                          "message": "입력값을 확인해주세요.",
                          "fieldErrors": []
                        }
                        """, JsonCompareMode.STRICT));

        verifyNoInteractions(eventIngestionService);
    }

    // 실제 DB에 생성하지 않는 테스트용 접수 결과
    private EventIngestionService.Receipt sampleReceipt() {
        return new EventIngestionService.Receipt(
                10L,
                20L,
                "OPEN",
                Instant.parse("2026-11-26T00:00:00Z")
        );
    }
}