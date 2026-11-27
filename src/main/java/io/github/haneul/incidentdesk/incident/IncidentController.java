package io.github.haneul.incidentdesk.incident;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/incidents/{incidentId}")
public class IncidentController {

    private final IncidentQueryService incidentQueryService;
    private final IncidentStatusService statusService;

    public IncidentController(
            IncidentQueryService incidentQueryService,
            IncidentStatusService statusService
    ) {
        this.incidentQueryService = incidentQueryService;
        this.statusService = statusService;
    }

    @GetMapping
    public IncidentDetailResponse getDetail(
            @PathVariable("incidentId")
            @Positive(message = "사건 번호는 양의 정수여야 합니다.")
            Long incidentId
    ) {
        return incidentQueryService.getDetail(incidentId);
    }

    @PostMapping("/acknowledge")
    public StatusResponse acknowledge(
            @PathVariable("incidentId") @Positive Long incidentId
    ) {
        return new StatusResponse(incidentId, statusService.acknowledge(incidentId));
    }

    @PostMapping("/resolve")
    public StatusResponse resolve(
            @PathVariable("incidentId") @Positive Long incidentId
    ) {
        return new StatusResponse(incidentId, statusService.resolve(incidentId));
    }

        @PostMapping("/assign")
    public AssignmentResponse assign(
            @PathVariable("incidentId") @Positive Long incidentId,
            @Valid @RequestBody AssignmentRequest request
    ) {
        statusService.assign(incidentId, request.assigneeKey());
        return new AssignmentResponse(incidentId, request.assigneeKey());
    }

    @PostMapping("/transfer")
    public AssignmentResponse transfer(
            @PathVariable("incidentId") @Positive Long incidentId,
            @Valid @RequestBody TransferRequest request
    ) {
        statusService.transfer(
                incidentId,
                request.currentAssignee(),
                request.nextAssignee()
        );
        return new AssignmentResponse(incidentId, request.nextAssignee());
    }

    public record TransferRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9-]{1,100}") String currentAssignee,
            @NotBlank @Pattern(regexp = "[a-z0-9-]{1,100}") String nextAssignee
    ) {
    }

    public record AssignmentRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9-]{1,100}") String assigneeKey
    ) {
    }

    public record AssignmentResponse(Long incidentId, String assigneeKey) {
    }

    public record StatusResponse(Long incidentId, String status) {
    }
}