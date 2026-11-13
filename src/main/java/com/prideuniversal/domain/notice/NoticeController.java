package com.prideuniversal.domain.notice;

import com.prideuniversal.domain.notice.dto.NoticeDtos.NoticeRequest;
import com.prideuniversal.domain.notice.dto.NoticeDtos.NoticeResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** HTTP surface for the notice board. The rules live in {@link NoticeService}. */
@RestController
@RequestMapping("/api/notices")
@Tag(name = "Notices", description = "The society notice board")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    @Operation(summary = "The board as the caller may see it: pinned first, then newest")
    public List<NoticeResponse> list() {
        return noticeService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One notice (404 if the caller may not see it)")
    public NoticeResponse get(@PathVariable UUID id) {
        return noticeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a notice (communications roles)")
    public NoticeResponse create(@Valid @RequestBody NoticeRequest request) {
        return noticeService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit, publish or pin a notice; requires the version last read")
    public NoticeResponse update(@PathVariable UUID id, @Valid @RequestBody NoticeRequest request) {
        return noticeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a notice")
    public void delete(@PathVariable UUID id) {
        noticeService.delete(id);
    }
}
