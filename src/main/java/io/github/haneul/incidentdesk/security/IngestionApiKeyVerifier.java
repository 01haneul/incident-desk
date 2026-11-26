package io.github.haneul.incidentdesk.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// 서버에 설정한 API 키와 요청으로 받은 키를 비교한다.
// 키를 출력하지 않으며, 공백 제거나 대소문자 변환을 하지 않는다.
public final class IngestionApiKeyVerifier {

    private final byte[] expectedDigest;

    public IngestionApiKeyVerifier(String configuredKey) {
        // 비어 있는 설정으로 인증 기능이 만들어지는 것을 차단한다.
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalArgumentException(
                    "이벤트 접수 API 키 설정이 필요합니다."
            );
        }

        this.expectedDigest = digest(configuredKey);
    }

    public boolean isValid(String providedKey) {
        // 헤더가 없거나 비어 있으면 인증 실패
        if (providedKey == null || providedKey.isBlank()) {
            return false;
        }

        byte[] providedDigest = digest(providedKey);

        // 같은 길이의 해시 값을 JDK 비교 함수로 확인한다.
        return MessageDigest.isEqual(
                expectedDigest,
                providedDigest
        );
    }

    private static byte[] digest(String value) {
        try {
            // 요청마다 별도 객체를 사용해 동시 요청 간 상태 공유를 피한다.
            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");

            return messageDigest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "API 키 검증 기능을 초기화할 수 없습니다.",
                    exception
            );
        }
    }
}