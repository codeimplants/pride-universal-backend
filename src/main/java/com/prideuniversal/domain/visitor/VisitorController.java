package com.prideuniversal.domain.visitor;

import com.prideuniversal.domain.visitor.dto.VisitorDtos.LogVisitorRequest;
import com.prideuniversal.domain.visitor.dto.VisitorDtos.RejectVisitorRequest;
import com.prideuniversal.domain.visitor.dto.VisitorDtos.VisitorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/visitors")
@Tag(name = "Visitors", description = "The gate register")
public class VisitorController {

    private final VisitorService visitorService;

    public VisitorController(VisitorService visitorService) {
        this.visitorService = visitorService;
    }

    @GetMapping
    @Operation(summary = "Visitors: the whole register for gate staff, the caller's flat for residents")
    public List<VisitorResponse> list(@RequestParam(required = false) VisitorStatus status) {
        return visitorService.list(status);
    }

    @GetMapping("/{id}")
    public VisitorResponse get(@PathVariable UUID id) {
        return visitorService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Log a visitor at the gate")
    public VisitorResponse log(@Valid @RequestBody LogVisitorRequest request) {
        return visitorService.log(request);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Let a waiting visitor in")
    public VisitorResponse approve(@PathVariable UUID id) {
        return visitorService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Turn a waiting visitor away")
    public VisitorResponse reject(@PathVariable UUID id,
                                  @Valid @RequestBody(required = false) RejectVisitorRequest request) {
        return visitorService.reject(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/check-out")
    @Operation(summary = "Record a visitor leaving")
    public VisitorResponse checkOut(@PathVariable UUID id) {
        return visitorService.checkOut(id);
    }
}
