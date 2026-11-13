package com.prideuniversal.domain.notice.dto;

import com.prideuniversal.domain.notice.Notice;
import com.prideuniversal.domain.notice.NoticeScope;
import com.prideuniversal.domain.notice.NoticeType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request and response shapes for the notice endpoints.
 *
 * <p>The fields are declared because they are the wire contract the tests are
 * written against. What is left to you is marked {@code TODO(entry 008)}.
 *
 * <p>There is one response record here, where {@code FlatDtos} has two. That is
 * not an oversight — work out why the split does not carry over, and put the
 * answer in your LEARNING-LOG entry.
 */
public final class NoticeDtos {

    private NoticeDtos() {}

    /**
     * Used for both create and update; {@code version} is null on create and
     * required on update, exactly as in {@code FlatRequest}.
     *
     * <p>TODO(entry 008): bean validation. {@code blankTitleIsAFieldError}
     * expects a missing or blank title to come back as a 400 with
     * {@code field_errors.title}. Content is required too. Take the length
     * limits from the column definitions in {@link Notice}, not from guesses —
     * a limit looser than the column turns a 400 into a database error.
     *
     * <p>TODO(entry 008): decide what a <em>null</em> {@code isPublished}
     * means, and say so in this comment. The schema defaults the column to
     * TRUE; ask yourself whether that is the safe default for a message that
     * goes to every resident. Do the same for null {@code noticeType} and
     * {@code scope}. (The tests always send all three, so the choice is yours.)
     *
     * <p>Note what is absent: {@code publishedBy} and {@code publishedById}.
     * Keep it that way — see {@code authorComesFromTheTokenNotTheBody}.
     */
    public record NoticeRequest(
            String title,
            String content,
            NoticeType noticeType,
            NoticeScope scope,
            Boolean isPublished,
            boolean pinned,
            LocalDate meetingDate,
            String meetingVenue,
            Long version
    ) {}

    /** What every reader gets. */
    public record NoticeResponse(
            UUID id,
            String title,
            String content,
            NoticeType noticeType,
            NoticeScope scope,
            String publishedBy,
            boolean isPublished,
            boolean pinned,
            LocalDate meetingDate,
            String meetingVenue,
            Instant createdDate,
            Instant updatedDate,
            /** Echoed back so the client can send it on the next update. */
            Long version
    ) {
        public static NoticeResponse from(Notice notice) {
            // TODO(entry 008): map the entity. FlatSummary.from() is the model.
            throw new UnsupportedOperationException("TODO(entry 008): NoticeResponse.from");
        }
    }
}
