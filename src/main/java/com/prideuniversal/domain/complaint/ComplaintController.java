package com.prideuniversal.domain.complaint;

import com.prideuniversal.domain.complaint.dto.ComplaintDtos.ComplaintResponse;
import com.prideuniversal.domain.complaint.dto.ComplaintDtos.CreateComplaintRequest;
import com.prideuniversal.domain.complaint.dto.ComplaintDtos.UpdateComplaintRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/complaints")
@Tag(name = "Complaints", description = "The helpdesk")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    @GetMapping
    @Operation(summary = "Complaints the caller may see, newest first")
    public List<ComplaintResponse> list(@RequestParam(required = false) ComplaintStatus status,
                                        @RequestParam(required = false) String wing) {
        return complaintService.list(status, wing);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One complaint (404 if the caller may not see it)")
    public ComplaintResponse get(@PathVariable UUID id) {
        return complaintService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise a complaint")
    public ComplaintResponse create(@Valid @RequestBody CreateComplaintRequest request) {
        return complaintService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update status, priority, assignee and notes (helpdesk roles)")
    public ComplaintResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateComplaintRequest request) {
        return complaintService.update(id, request);
    }
}
