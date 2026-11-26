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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import io.github.haneul.incidentdesk.common.ApiExceptionHandler;

// 이벤트 접수의 정상 응답, 입력 검증, 잘못된 JSON 처리를 검사한다.
// 접수 서비스는 대역을 사용하므로 실제 DB 저장은 수행하지 않는다.
@WebMvcTest(EventController.class)
@Import(ApiExceptionHandler.class)
class EventControllerWebTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventIngestionService eventIngestionService;

    @Test
    void returns201AndIncidentLocationWhenRequestIsValid() throws Exception {
        Instant receivedAt = Instant.parse("2026-11-26T00:00:00Z");

        // 두 번호를 다르게 설정해 응답에서 서로 뒤바뀌지 않는지도 검사
        EventIngestionService.Receipt receipt =
                new EventIngestionService.Receipt(
                        10L,
                        20L,
                        "OPEN",
                        receivedAt
                );

        given(eventIngestionService.receive(
                "payment-api",
                "Payment approval failed"
        )).willReturn(receipt);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceKey": "payment-api",
                                  "title": "Payment approval failed"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/incidents/20"
                ))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.eventId").value(10))
                .andExpect(jsonPath("$.incidentId").value(20))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.receivedAt")
                        .value(receivedAt.toString()));

        // 요청의 두 값이 접수 서비스에 정확히 전달됐는지 검사
        verify(eventIngestionService).receive(
                "payment-api",
                "Payment approval failed"
        );
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

        // 입력 검증에서 거절되면 접수 서비스는 실행되지 않아야 함
        verifyNoInteractions(eventIngestionService);
    }

    @Test
    void rejectsMalformedJsonBeforeCallingService() throws Exception {
        // 열린 중괄호만 보내 JSON을 해석할 수 없는 상황 재현
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
}