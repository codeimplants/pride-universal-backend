package com.prideuniversal.domain.notice;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.notice.dto.NoticeDtos.NoticeRequest;
import com.prideuniversal.domain.notice.dto.NoticeDtos.NoticeResponse;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The society notice board.
 *
 * <h2>Who sees what</h2>
 * The communications roles ({@link Role#COMMS}) see everything: drafts, and every
 * wing's notices, because they edit them. Everyone else sees published notices
 * addressed to both wings or to their own flat's wing; a caller with no flat sees
 * society-wide notices only. That rule lives in one place, {@link Audience}, used
 * by the list (as a query) and by single reads (as a check), so they cannot drift.
 *
 * <h2>Hidden is 404</h2>
 * A notice the caller may not see answers 404, not 403. For a flat, a 403 is fine
 * because the directory already lists every flat. For an unannounced notice, its
 * existence is the secret.
 *
 * <h2>Delete is a real delete</h2>
 * Nothing references a notice, so there is no ledger to protect (unlike a flat).
 * Unpublishing is how a notice is withdrawn while keeping it.
 */
@Service
public class NoticeService {

    /** {@link Role#COMMS}: the treasurer manages money, not the society's voice. */
    private static final String CAN_WRITE = "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','COMMITTEE_MEMBER')";

    private final NoticeRepository noticeRepository;
    private final WingScopes wingScopes;

    public NoticeService(NoticeRepository noticeRepository, WingScopes wingScopes) {
        this.noticeRepository = noticeRepository;
        this.wingScopes = wingScopes;
    }

    // =====================================================================
    // Reads
    // =====================================================================

    @Transactional(readOnly = true)
    public List<NoticeResponse> list() {
        Audience audience = audienceOf(AuthPrincipal.current());
        List<Notice> notices = audience.everything()
                ? noticeRepository.findTop200ByOrderByPinnedDescCreatedDateDesc()
                : noticeRepository.findTop200ByPublishedTrueAndScopeInOrderByPinnedDescCreatedDateDesc(audience.scopes());
        return notices.stream().map(NoticeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public NoticeResponse get(UUID id) {
        Notice notice = noticeRepository.findById(id)
                .filter(audienceOf(AuthPrincipal.current())::canSee)
                .orElseThrow(() -> ApiException.notFound("Notice", id));
        return NoticeResponse.from(notice);
    }

    // =====================================================================
    // Writes
    // =====================================================================

    @PreAuthorize(CAN_WRITE)
    @Transactional
    public NoticeResponse create(NoticeRequest request) {
        AuthPrincipal author = AuthPrincipal.current();
        Notice notice = new Notice();
        notice.setPublishedBy(author.displayName());
        notice.setPublishedById(author.userId());
        apply(request, notice);
        return NoticeResponse.from(noticeRepository.save(notice));
    }

    /**
     * Edits a notice, including publishing a draft or pinning it. The original
     * author stays the author: a notice records who issued it, and a typo fix by
     * another committee member doesn't change that.
     */
    @PreAuthorize(CAN_WRITE)
    @Transactional
    public NoticeResponse update(UUID id, NoticeRequest request) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notice", id));

        if (request.version() == null) {
            throw ApiException.badRequest(
                    "version is required when updating a notice. Re-fetch it and send the version it returned.");
        }
        if (!Objects.equals(notice.getVersion(), request.version())) {
            throw new OptimisticLockingFailureException("Notice " + id + " was modified by someone else");
        }

        apply(request, notice);
        // saveAndFlush so the response carries the incremented version (LEARNING-LOG entry 006).
        return NoticeResponse.from(noticeRepository.saveAndFlush(notice));
    }

    @PreAuthorize(CAN_WRITE)
    @Transactional
    public void delete(UUID id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Notice", id));
        noticeRepository.delete(notice);
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private void apply(NoticeRequest request, Notice notice) {
        NoticeType type = request.noticeType() == null ? NoticeType.GENERAL : request.noticeType();
        if (type.isMeeting() && request.meetingDate() == null) {
            // No database constraint backs this; this check is the only one.
            throw ApiException.badRequest("An AGM or SGM notice must give the meeting date.");
        }

        notice.setTitle(request.title().trim());
        notice.setContent(request.content().trim());
        notice.setNoticeType(type);
        notice.setScope(request.scope() == null ? NoticeScope.BOTH : request.scope());
        notice.setPublished(Boolean.TRUE.equals(request.isPublished()));
        notice.setPinned(request.pinned());
        notice.setMeetingDate(request.meetingDate());
        notice.setMeetingVenue(blankToNull(request.meetingVenue()));
    }

    private Audience audienceOf(AuthPrincipal caller) {
        return Role.COMMS.contains(caller.role())
                ? Audience.EVERYTHING
                : new Audience(false, wingScopes.addressing(caller));
    }

    /** The one definition of which notices a caller may see. */
    record Audience(boolean everything, Set<NoticeScope> scopes) {

        static final Audience EVERYTHING = new Audience(true, EnumSet.allOf(NoticeScope.class));

        boolean canSee(Notice notice) {
            return everything || (notice.isPublished() && scopes.contains(notice.getScope()));
        }
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
