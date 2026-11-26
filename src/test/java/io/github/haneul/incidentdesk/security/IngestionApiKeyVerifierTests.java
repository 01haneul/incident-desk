package io.github.haneul.incidentdesk.security;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// 실제 비밀값 대신 공개 가능한 테스트 전용 문자열을 사용한다.
// 서버와 DB를 실행하지 않고 키 검증 규칙만 검사한다.
class IngestionApiKeyVerifierTests {

    private static final String TEST_KEY =
            "Test-Only-Ingestion-Key-A9f3";

    private final IngestionApiKeyVerifier verifier =
            new IngestionApiKeyVerifier(TEST_KEY);

    @Test
    void acceptsOnlyExactlyMatchingKey() {
        assertAll(
                // 정확히 같은 키만 허용
                () -> assertTrue(verifier.isValid(TEST_KEY)),

                // 다른 키는 거절
                () -> assertFalse(
                        verifier.isValid("wrong-key")
                ),

                // 대소문자가 달라도 거절
                () -> assertFalse(
                        verifier.isValid(
                                "test-only-ingestion-key-a9f3"
                        )
                ),

                // 뒤에 문자가 추가된 키도 거절
                () -> assertFalse(
                        verifier.isValid(TEST_KEY + "-extra")
                )
        );
    }

    @Test
    void rejectsMissingAndBlankRequestKeys() {
        assertAll(
                () -> assertFalse(verifier.isValid(null)),
                () -> assertFalse(verifier.isValid("")),
                () -> assertFalse(verifier.isValid("   "))
        );
    }

    @Test
    void rejectsMissingAndBlankConfiguredKeys() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new IngestionApiKeyVerifier(null)
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new IngestionApiKeyVerifier("")
                ),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new IngestionApiKeyVerifier("   ")
                )
        );
    }
}