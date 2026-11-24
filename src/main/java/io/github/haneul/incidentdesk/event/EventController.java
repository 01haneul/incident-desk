package io.github.haneul.incidentdesk.event;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


// POST /api/events 요청을 검증하고 장애 접수 서비스를 호출한다.
// 저장이 완료되면 201 상태와 접수 결과, 사건 조회 주소를 반환한다.

@RestController
public class EventController {

    private final EventIngestionService eventIngestionService;

    public EventController(EventIngestionService eventIngestionService) {
        this.eventIngestionService = eventIngestionService;
    }

    @PostMapping("/api/events")
    public ResponseEntity<EventIngestionService.Receipt> create(
            // @RequestBody : JSON을 EventCreateRequest 객체로 변환
            // @Valid : DTO에 적은 검증 규칙 적용
            @Valid @RequestBody EventCreateRequest request
    ) {
        EventIngestionService.Receipt receipt =
                eventIngestionService.receive(
                        request.serviceKey(),
                        request.title()
                );

        URI location = URI.create(
                "/api/incidents/" + receipt.incidentId()
        );
        // ResponseEntity : HTTP 상태 코드·헤더·본문을 지정
        // created(location) : 201 Created 와 Location 헤더 설정
        return ResponseEntity.created(location).body(receipt);
    }
}