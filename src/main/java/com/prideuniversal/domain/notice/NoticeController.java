package com.prideuniversal.domain.notice;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP surface for the notice board. <b>Entry 008 — this class is yours.</b>
 *
 * <p>TODO(entry 008): five endpoints, the same shape as {@code FlatController}:
 *
 * <pre>
 *   GET    /api/notices        200  the board, as the caller may see it
 *   GET    /api/notices/{id}   200  one notice (404 if the caller may not see it)
 *   POST   /api/notices        201  post a notice
 *   PUT    /api/notices/{id}   200  edit; requires the version last read
 *   DELETE /api/notices/{id}   204  remove
 * </pre>
 *
 * <p>If you find yourself writing an {@code if} in here, it belongs in
 * {@link NoticeService}.
 */
@RestController
@RequestMapping("/api/notices")
@Tag(name = "Notices", description = "The society notice board")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }
}
