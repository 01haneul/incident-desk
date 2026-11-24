// 애플리케이션의 HTTP 응답 여부를 확인하는 API
// GET/api/health로 서비스 이름과 고정 상태값을 반환하며, DB 연결은 검사하지 않는데



// 이 클래스가 속한 패키지 지정
package io.github.haneul.incidentdesk;

//  사용할 클래스와 에너테이션 가져오기
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// HTTP요청을 처리하고 응답 데이터를 반환하는 클래스로 등록
@RestController
// 클래스 선언.파일 이름과 일치해야함
public class HealthController {
    // 해당 주소의 GET 요청을 아래 메서드에 연결
    @GetMapping("/api/health")
    // 문자열 키와 값을 담은 Map을 반환하는 메서드
    public Map<String, String>health() {
        // 응답에 사용할 키·값 데이터 생성
        // return 생성한 데이터를 호출 결과로 반환  
        return Map.of(  
            "status", "UP",
            "service", "incident-desk"
        );
    }
}