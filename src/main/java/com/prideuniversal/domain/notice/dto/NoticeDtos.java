package com.prideuniversal.domain.notice.dto;

import com.prideuniversal.domain.notice.Notice;
import com.prideuniversal.domain.notice.NoticeScope;
import com.prideuniversal.domain.notice.NoticeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request and response shapes for the notice endpoints.
 *
 * <p>One response record, where {@code FlatDtos} has two: a notice carries no
 * personal data, and whether a reader may see it at all is decided by the service
 * before any DTO is built. There is no partial view to protect.
 */
public final class NoticeDtos {

    private NoticeDtos() {}

    /**
     * Create and update; {@code version} is null on create and required on update.
     *
     * <p>Defaults for absent fields: a null {@code isPublished} means <b>draft</b> —
     * the schema defaults to published, but a message to every flat should go out
     * only when someone said so. Null {@code noticeType} is {@code general}, null
     * {@code scope} is {@code both}.
     *
     * <p>There is no {@code publishedBy} / {@code publishedById}: the author comes
     * from the caller's token. A request that could carry an author would let anyone
     * post in the chairman's name.
     */
    public record NoticeRequest(

            @NotBlank(message = "Title is required")
            @Size(max = 255, message = "Title is too long")
            String title,

            @NotBlank(message = "Content is required")
            @Size(max = 20_000, message = "Content is too long")
            String content,

            NoticeType noticeType,

            NoticeScope scope,

            Boolean isPublished,

            boolean pinned,

            LocalDate meetingDate,

            @Size(max = 255, message = "Venue is too long")
            String meetingVenue,

            Long version
    ) {}

    public record NoticeResponse(
            UUID id,
            String title,
            String content,
            NoticeType noticeType,
            NoticeScope scope,
            String publishedBy,
            boolean isPublished,
            boolean pinned,
            String attachmentUrl,
            LocalDate meetingDate,
            String meetingVenue,
            Instant createdDate,
            Instant updatedDate,
            /** Echoed back so the client can send it on the next update. */
            Long version
    ) {
        public static NoticeResponse from(Notice notice) {
            return new NoticeResponse(
                    notice.getId(),
                    notice.getTitle(),
                    notice.getContent(),
                    notice.getNoticeType(),
                    notice.getScope(),
                    notice.getPublishedBy(),
                    notice.isPublished(),
                    notice.isPinned(),
                    notice.getAttachmentUrl(),
                    notice.getMeetingDate(),
                    notice.getMeetingVenue(),
                    notice.getCreatedDate(),
                    notice.getUpdatedDate(),
                    notice.getVersion());
        }
    }
}
