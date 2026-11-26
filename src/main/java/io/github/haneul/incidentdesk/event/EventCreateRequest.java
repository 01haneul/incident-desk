package io.github.haneul.incidentdesk.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 장애 이벤트 접수 요청과 입력 검증 규칙.
// 외부 이벤트 번호는 선택 입력이며, 기존 요청도 계속 허용한다.
public record EventCreateRequest(
        @NotBlank(message = "서비스 식별자는 필수입니다.")
        @Size(max = 100, message = "서비스 식별자는 100자 이하여야 합니다.")
        @Pattern(
                regexp = "[a-z0-9-]+",
                message = "서비스 식별자는 영문 소문자, 숫자, 하이픈만 허용합니다."
        )
        String serviceKey,

        @NotBlank(message = "제목은 공백일 수 없습니다.")
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,

        // 두 검증은 null을 허용하지만, 제공된 값은 길이와 형식을 검사한다.
        @Size(
                min = 1,
                max = 100,
                message = "외부 이벤트 번호는 1~100자여야 합니다."
        )
        @Pattern(
                regexp = "[A-Za-z0-9._:-]+",
                message = "외부 이벤트 번호는 영문, 숫자, 점, 밑줄, 콜론, 하이픈만 허용합니다."
        )
        String externalEventId
) {
}