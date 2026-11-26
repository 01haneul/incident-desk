package io.github.haneul.incidentdesk.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import jakarta.servlet.DispatcherType;

// HTTP 서버 환경에서 API 키 인증 필터를 등록한다.
// 서버 키가 비어 있으면 검증 객체 생성이 실패해 서버 시작을 차단한다.
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET
)
public class IngestionSecurityConfiguration {

    @Bean
    public FilterRegistrationBean<IngestionApiKeyFilter> ingestionApiKeyFilter(
            @Value("${incident.ingestion.api-key:}") String configuredKey
    ) {
        IngestionApiKeyVerifier verifier =
                new IngestionApiKeyVerifier(configuredKey);

        IngestionApiKeyFilter filter =
                new IngestionApiKeyFilter(verifier);

        FilterRegistrationBean<IngestionApiKeyFilter> registration =
                new FilterRegistrationBean<>(filter);

        registration.setName("ingestionApiKeyFilter");
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);

        return registration;
    }
}