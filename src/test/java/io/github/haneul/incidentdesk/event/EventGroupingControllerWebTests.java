package io.github.haneul.incidentdesk.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import io.github.haneul.incidentdesk.common.ApiExceptionHandler;

// 사건 연결 요청의 입력 검증과 성공·충돌 응답을 검사한다.
// 서비스는 대역을 사용하며, DB 동작은 별도 통합 테스트에서 검증한다.
@WebMvcTest(EventController.class)
@Import(ApiExceptionHandler.class)
class EventGroupingControllerWebTests {

    private static final String INCIDENT_KEY =
            "payment-approval-failure";

    private static final String TITLE =
            "Payment approval failed";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventIngestionService eventIngestionService;

    @ParameterizedTest(
            name = "eventCreated={0}, incidentCreated={1}, HTTP={2}"
    )
    @CsvSource({
            "true, true, 201",
            "true, false, 200",
            "false, false, 200"
    })
    void returnsCreationFlagsAndExpectedStatus(
            boolean eventCreated,
            boolean incidentCreated,
            int expectedStatus
    ) throws Exception {
        given(eventIngestionService.receive(
                "payment-api",
                TITLE,
                "evt-001",
                INCIDENT_KEY
        )).willReturn(new EventIngestionService.Result(
                sampleReceipt(),
                eventCreated,
                incidentCreated
        ));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(INCIDENT_KEY)))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().string(
                        "Location",
                        "/api/incidents/20"
                ))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(content().json("""
                        {
                          "eventId": 10,
                          "incidentId": 20,
                          "status": "OPEN",
                          "receivedAt": "2026-11-26T00:00:00Z",
                          "eventCreated": %s,
                          "incidentCreated": %s
                        }
                        """.formatted(
                                eventCreated,
                                incidentCreated
                        ), JsonCompareMode.STRICT));

        verify(eventIngestionService).receive(
                "payment-api",
                TITLE,
                "evt-001",
                INCIDENT_KEY
        );

        verifyNoMoreInteractions(eventIngestionService);
    }

    @ParameterizedTest(name = "invalid incidentKey case {index}")
    @MethodSource("invalidIncidentKeys")
    void rejectsInvalidIncidentKey(String incidentKey) throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(incidentKey)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("incidentKey"));

        verifyNoInteractions(eventIngestionService);
    }

    static Stream<String> invalidIncidentKeys() {
        return Stream.of(
                "",
                "   ",
                "bad/key",
                "장애-001",
                "a".repeat(101)
        );
    }

    @Test
    void acceptsIncidentKeyAtMaximumLength() throws Exception {
        String incidentKey = "a".repeat(100);

        given(eventIngestionService.receive(
                "payment-api",
                TITLE,
                "evt-001",
                incidentKey
        )).willReturn(new EventIngestionService.Result(
                sampleReceipt(),
                true,
                true
        ));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(incidentKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventCreated").value(true))
                .andExpect(jsonPath("$.incidentCreated").value(true));

        // 입력한 100자 전체가 변경 없이 전달돼야 한다.
        verify(eventIngestionService).receive(
                "payment-api",
                TITLE,
                "evt-001",
                incidentKey
        );
    }

    @Test
    void usesLegacyCallWhenIncidentKeyIsNull() throws Exception {
        given(eventIngestionService.receive(
                "payment-api",
                TITLE,
                "evt-001"
        )).willReturn(new EventIngestionService.Result(
                sampleReceipt(),
                true
        ));

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceKey": "payment-api",
                                  "title": "Payment approval failed",
                                  "externalEventId": "evt-001",
                                  "incidentKey": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventCreated").value(true))
                .andExpect(jsonPath("$.incidentCreated").value(true));

        verify(eventIngestionService).receive(
                "payment-api",
                TITLE,
                "evt-001"
        );

        verifyNoMoreInteractions(eventIngestionService);
    }

    @Test
    void returns409WhenIncidentKeyConflicts() throws Exception {
        given(eventIngestionService.receive(
                "payment-api",
                TITLE,
                "evt-001",
                INCIDENT_KEY
        )).willThrow(new EventIdConflictException());

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(INCIDENT_KEY)))
                .andExpect(status().isConflict())
                .andExpect(content().json("""
                        {
                          "code": "EVENT_ID_CONFLICT",
                          "message": "동일한 외부 이벤트 번호에 다른 내용이 전달됐습니다.",
                          "fieldErrors": []
                        }
                        """, JsonCompareMode.STRICT));
    }

    private String requestBody(String incidentKey) {
        // 이 테스트에서 사용하는 고정 입력값으로 JSON 생성
        return """
                {
                  "serviceKey": "payment-api",
                  "title": "Payment approval failed",
                  "externalEventId": "evt-001",
                  "incidentKey": "%s"
                }
                """.formatted(incidentKey);
    }

    private EventIngestionService.Receipt sampleReceipt() {
        return new EventIngestionService.Receipt(
                10L,
                20L,
                "OPEN",
                Instant.parse("2026-11-26T00:00:00Z")
        );
    }
}