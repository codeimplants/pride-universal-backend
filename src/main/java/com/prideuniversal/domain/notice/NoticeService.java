package com.prideuniversal.domain.notice;

import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.notice.dto.NoticeDtos.NoticeRequest;
import com.prideuniversal.domain.notice.dto.NoticeDtos.NoticeResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * The notice board. <b>Entry 008 — this class is yours.</b>
 *
 * <p>Copy the <em>structure</em> of {@code FlatService}: authorisation here
 * rather than on the controller, {@code readOnly} reads, the two-layer
 * optimistic lock on update, an {@code apply()} that copies the request field
 * by field. Do <em>not</em> copy its rules — almost every one of them is
 * different here, and each difference is pinned by a test in
 * {@code NoticeApiIntegrationTest}.
 *
 * <p>Every method below throws until you implement it, so the tests fail with
 * a 500 rather than refusing to compile. Delete {@link #todo} when you are
 * done; if it still has callers, you are not done.
 */
@Service
public class NoticeService {

    // TODO(entry 008): who may write notices?
    // It is not FlatService.CAN_EDIT. Read the permission groups at the bottom
    // of Role.java - one of them was made for this. treasurerCannotPostNotices
    // is the test that tells you whether you picked the right one.

    private final NoticeRepository noticeRepository;

    /** You will need it: the caller's wing is not in their token. */
    private final FlatRepository flatRepository;

    public NoticeService(NoticeRepository noticeRepository, FlatRepository flatRepository) {
        this.noticeRepository = noticeRepository;
        this.flatRepository = flatRepository;
    }

    // =====================================================================
    // Reads
    // =====================================================================

    /**
     * The board as the caller is allowed to see it: pinned first, then newest.
     *
     * <p>Visibility rules:
     * <ul>
     *   <li>People who may <em>write</em> notices see everything — drafts, and
     *       every wing's notices. They need to, to edit them.</li>
     *   <li>Everyone else sees only <b>published</b> notices addressed to
     *       {@code BOTH} wings or to <b>the wing of their own flat</b>. A caller
     *       with no flat (a security guard, a treasurer who lives elsewhere)
     *       sees {@code BOTH} only.</li>
     * </ul>
     *
     * <p>{@code AuthPrincipal} carries a {@code flatId}, not a wing.
     * {@link NoticeScope#forWing} turns a wing code into a scope.
     */
    public List<NoticeResponse> list() {
        throw todo("list");
    }

    /**
     * One notice, if the caller is allowed to see it.
     *
     * <p>A notice the caller may not see is a <b>404, not a 403</b>
     * ({@code draftIsNotFoundToAResident}). {@code FlatService.detail()} made
     * the opposite choice for flats, and both are right — work out why before
     * you write this. (Hint: what does a 403 confirm, and is that a secret for
     * a flat? For a draft?)
     *
     * <p>Whatever you write here and in {@link #list()} must agree on what
     * "visible" means. If they each carry their own copy of the rule, they
     * will drift. One definition, used twice.
     */
    public NoticeResponse get(UUID id) {
        throw todo("get");
    }

    // =====================================================================
    // Writes
    // =====================================================================

    /**
     * Posts a notice.
     *
     * <ul>
     *   <li>The author — {@code publishedBy} and {@code publishedById} — comes
     *       from {@code AuthPrincipal}, never from the request. A request
     *       record that can carry an author is a forgery API.</li>
     *   <li>An AGM or SGM notice without a meeting date is a 400
     *       ({@link NoticeType#isMeeting}). Same idea as
     *       {@code FlatService.requireTenantNamed()} — except here there is no
     *       database constraint behind it, so your check is the only one.</li>
     * </ul>
     */
    public NoticeResponse create(NoticeRequest request) {
        throw todo("create");
    }

    /**
     * Edits a notice — including publishing a draft, by sending
     * {@code is_published: true}.
     *
     * <p>Optimistic locking exactly as {@code FlatService.update()}: version
     * required (400 without), stale version is a 409, and remember entry 006's
     * bug about which version the response must carry.
     *
     * <p>Decide whether editing someone else's notice changes its author. The
     * tests do not force an answer; write down the one you chose.
     */
    public NoticeResponse update(UUID id, NoticeRequest request) {
        throw todo("update");
    }

    /**
     * Removes a notice. Afterwards it must be gone for everyone, including
     * the committee ({@code committeeDeletesANotice}).
     *
     * <p>{@code FlatService.deactivate()} explains why a flat can never be
     * deleted. Check whether that reason holds for a notice — see the javadoc
     * on {@link Notice}.
     */
    public void delete(UUID id) {
        throw todo("delete");
    }

    // =====================================================================
    // Internals
    // =====================================================================

    private static UnsupportedOperationException todo(String method) {
        return new UnsupportedOperationException("TODO(entry 008): NoticeService." + method);
    }
}
