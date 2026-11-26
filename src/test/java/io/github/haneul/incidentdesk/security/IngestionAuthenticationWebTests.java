package io.github.haneul.incidentdesk.security;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.github.haneul.incidentdesk.HealthController;
import io.github.haneul.incidentdesk.common.ApiExceptionHandler;
import io.github.haneul.incidentdesk.event.EventController;
import io.github.haneul.incidentdesk.event.EventIngestionService;

// 실제 인증 필터를 포함해 요청 차단과 정상 전달을 검사한다.
// 접수 서비스는 대역을 사용하므로 실제 DB에는 저장하지 않는다.
@WebMvcTest({
        EventController.class,
        HealthController.class
})
@Import({
        IngestionSecurityConfiguration.class,
        ApiExceptionHandler.class
})
@TestPropertySource(properties = {
        "incident.ingestion.api-key=Test-Only-Web-Ingestion-Key-A9f3"
})
class IngestionAuthenticationWebTests {

    // 공개 가능한 테스트 전용 키. 실제 서버 키와 별개다.
    private static final String TEST_KEY =
            "Test-Only-Web-Ingestion-Key-A9f3";

    private static final String VALID_BODY = """
            {
              "serviceKey": "payment-api",
              "title": "Payment approval failed"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventIngestionService eventIngestionService;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"wrong-key", "   "})
    void rejectsMissingOrInvalidKeys(String key) throws Exception {
        MockHttpServletRequestBuilder request = eventRequest();

        if (key != null) {
            request.header("X-API-Key", key);
        }

        expectUnauthorized(request);
    }

    @Test
    void acceptsValidKeyAndCallsService() throws Exception {
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
                null
        )).willReturn(new EventIngestionService.Result(receipt, true));

        mockMvc.perform(eventRequest()
                        .header("X-API-Key", TEST_KEY))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "/api/incidents/20"
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

    @Test
    void validatesInputAfterSuccessfulAuthentication() throws Exception {
        mockMvc.perform(post("/api/events")
                        .header("X-API-Key", TEST_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceKey": "payment-api",
                                  "title": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors[0].field")
                        .value("title"));

        verifyNoInteractions(eventIngestionService);
    }

    @Test
    void rejectsInvalidKeyBeforeParsingJson() throws Exception {
        // JSON도 잘못됐지만, 먼저 인증 실패인 401로 거절해야 한다.
        expectUnauthorized(post("/api/events")
                .header("X-API-Key", "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"));
    }

    @Test
    void rejectsRepeatedApiKeyHeaders() throws Exception {
        expectUnauthorized(eventRequest()
                .header("X-API-Key", TEST_KEY, "wrong-key"));
    }

    @Test
    void allowsHealthRequestWithoutKey() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("incident-desk"));

        verifyNoInteractions(eventIngestionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/events;source=monitor",
            "/api/%65vents"
    })
    void protectsAlternateFormsOfEventPath(String path) throws Exception {
        // 경로 표기만 바꿔 인증 검사를 우회할 수 없어야 한다.
        expectUnauthorized(post(URI.create(path))
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY));
    }

    private MockHttpServletRequestBuilder eventRequest() {
        return post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY);
    }

    private void expectUnauthorized(
            MockHttpServletRequestBuilder request
    ) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        "WWW-Authenticate",
                        "ApiKey realm=\"incident-desk\""
                ))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(content().json("""
                        {
                          "code": "UNAUTHORIZED",
                          "message": "인증 정보가 없거나 올바르지 않습니다.",
                          "fieldErrors": []
                        }
                        """, JsonCompareMode.STRICT));

        // 인증 실패 요청이 접수 서비스까지 도달하지 않았는지 확인
        verifyNoInteractions(eventIngestionService);
    }
}