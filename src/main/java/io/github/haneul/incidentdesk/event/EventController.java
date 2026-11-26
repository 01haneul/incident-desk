package io.github.haneul.incidentdesk.event;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

// 외부 이벤트 번호를 포함한 접수 요청을 서비스에 전달한다.
// 새 접수는 201, 기존 접수 재사용은 200으로 응답한다.
@RestController
public class EventController {

    private final EventIngestionService eventIngestionService;

    public EventController(EventIngestionService eventIngestionService) {
        this.eventIngestionService = eventIngestionService;
    }

    @PostMapping("/api/events")
    public ResponseEntity<EventIngestionService.Receipt> create(
            @Valid @RequestBody EventCreateRequest request
    ) {
        EventIngestionService.Result result = eventIngestionService.receive(
                request.serviceKey(),
                request.title(),
                request.externalEventId()
        );

        EventIngestionService.Receipt receipt = result.receipt();

        URI location = URI.create(
                "/api/incidents/" + receipt.incidentId()
        );

        HttpStatus status = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status)
                .location(location)
                .body(receipt);
    }
}