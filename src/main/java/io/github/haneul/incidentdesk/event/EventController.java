package io.github.haneul.incidentdesk.event;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

// 사건 연결 키를 포함한 접수 요청을 서비스에 전달한다.
// 새 사건 생성은 201, 기존 사건 연결과 재전송은 200으로 응답한다.
@RestController
public class EventController {

    private final EventIngestionService eventIngestionService;

    public EventController(EventIngestionService eventIngestionService) {
        this.eventIngestionService = eventIngestionService;
    }

    @PostMapping("/api/events")
    public ResponseEntity<EventCreateResponse> create(
            @Valid @RequestBody EventCreateRequest request
    ) {
        EventIngestionService.Result result;

        if (request.incidentKey() == null) {
            // 연결 키가 없는 기존 호출 방식 유지
            result = eventIngestionService.receive(
                    request.serviceKey(),
                    request.title(),
                    request.externalEventId()
            );
        } else {
            result = eventIngestionService.receive(
                    request.serviceKey(),
                    request.title(),
                    request.externalEventId(),
                    request.incidentKey()
            );
        }

        EventIngestionService.Receipt receipt = result.receipt();

        EventCreateResponse response = new EventCreateResponse(
                receipt.eventId(),
                receipt.incidentId(),
                receipt.status(),
                receipt.receivedAt(),
                result.created(),
                result.incidentCreated()
        );

        URI location = URI.create(
                "/api/incidents/" + receipt.incidentId()
        );

        // 상태 코드는 새 사건 생성 여부를 기준으로 결정
        HttpStatus status = result.incidentCreated()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status)
                .location(location)
                .body(response);
    }
}