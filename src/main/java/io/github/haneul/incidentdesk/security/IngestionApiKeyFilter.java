package io.github.haneul.incidentdesk.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 이벤트 접수 요청의 API 키를 컨트롤러 호출 전에 확인한다.
// 인증 실패 시 고정된 오류 응답을 보내고 다음 처리를 중단한다.
public final class IngestionApiKeyFilter extends OncePerRequestFilter {

    private static final PathPattern EVENT_PATH =
            PathPatternParser.defaultInstance.parse("/api/events");

    private static final String UNAUTHORIZED_BODY = """
            {
              "code": "UNAUTHORIZED",
              "message": "인증 정보가 없거나 올바르지 않습니다.",
              "fieldErrors": []
            }
            """;

    private final IngestionApiKeyVerifier verifier;

    public IngestionApiKeyFilter(IngestionApiKeyVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 인증 적용 대상은 POST /api/events
        if (!"POST".equals(request.getMethod())) {
            return true;
        }

        // Spring의 경로 해석 방식을 사용해 인코딩된 경로도 확인한다.
        var path = ServletRequestPathUtils.parse(request)
                .pathWithinApplication();

        return !EVENT_PATH.matches(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Enumeration<String> keys = request.getHeaders("X-API-Key");

        if (keys == null || !keys.hasMoreElements()) {
            writeUnauthorized(response);
            return;
        }

        String providedKey = keys.nextElement();

        // 헤더가 여러 번 전달된 모호한 요청도 거절한다.
        if (keys.hasMoreElements() || !verifier.isValid(providedKey)) {
            writeUnauthorized(response);
            return;
        }

        // 인증에 성공한 요청만 컨트롤러 방향으로 전달
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(
            HttpServletResponse response
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(
                HttpHeaders.WWW_AUTHENTICATE,
                "ApiKey realm=\"incident-desk\""
        );
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        // 실제 키나 요청 내용을 포함하지 않는 고정 응답
        response.getWriter().write(UNAUTHORIZED_BODY);
    }
}