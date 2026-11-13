package com.prideuniversal.domain.meeting;

import com.prideuniversal.domain.meeting.dto.MeetingDtos.MeetingRequest;
import com.prideuniversal.domain.meeting.dto.MeetingDtos.MeetingResponse;
import com.prideuniversal.domain.meeting.dto.MeetingDtos.ResolutionRequest;
import com.prideuniversal.domain.meeting.dto.MeetingDtos.ResolutionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/meetings")
@Tag(name = "Meetings", description = "General body and committee meetings, and resolutions")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @GetMapping
    @Operation(summary = "Meetings addressed to the caller, latest first")
    public List<MeetingResponse> list(@RequestParam(required = false) MeetingStatus status) {
        return meetingService.list(status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One meeting with its resolutions")
    public MeetingResponse get(@PathVariable UUID id) {
        return meetingService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Schedule a meeting")
    public MeetingResponse create(@Valid @RequestBody MeetingRequest request) {
        return meetingService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update or record a meeting; requires the version last read")
    public MeetingResponse update(@PathVariable UUID id, @Valid @RequestBody MeetingRequest request) {
        return meetingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a meeting and its resolutions")
    public void delete(@PathVariable UUID id) {
        meetingService.delete(id);
    }

    @PostMapping("/{id}/resolutions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a resolution")
    public ResolutionResponse addResolution(@PathVariable UUID id, @Valid @RequestBody ResolutionRequest request) {
        return meetingService.addResolution(id, request);
    }

    @PutMapping("/{id}/resolutions/{resolutionId}")
    @Operation(summary = "Edit a resolution; requires the version last read")
    public ResolutionResponse updateResolution(@PathVariable UUID id, @PathVariable UUID resolutionId,
                                               @Valid @RequestBody ResolutionRequest request) {
        return meetingService.updateResolution(id, resolutionId, request);
    }

    @DeleteMapping("/{id}/resolutions/{resolutionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a resolution")
    public void deleteResolution(@PathVariable UUID id, @PathVariable UUID resolutionId) {
        meetingService.deleteResolution(id, resolutionId);
    }
}
