// 요청 DTO : 전달받을 데이터와 입력 규칙 정의

package io.github.haneul.incidentdesk.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


// 장애 이벤트 접수 요청 데이터와 입력 검증 규칙.
// 서비스 식별자와 제목을 받으며 입력값을 자동 변경하지 않는다.

public record EventCreateRequest(
        // @NotBlank :  null, 빈 문자열, 공백만 있는 문자열 거절
        @NotBlank(message = "서비스 식별자는 필수입니다.")
        // @Size : 문자열의 최대 길이 제한
        @Size(max = 100, message = "서비스 식별자는 100자 이하여야 합니다.")
        // @Pattern : 허용할 문자 형식 제한
        @Pattern(
                regexp = "[a-z0-9-]+",
                message = "서비스 식별자는 영문 소문자, 숫자, 하이픈만 허용합니다."
        )
        String serviceKey,

        @NotBlank(message = "제목은 공백일 수 없습니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title

) {
}
