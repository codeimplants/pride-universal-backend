package com.prideuniversal.domain.business;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Text;
import com.prideuniversal.common.Versions;
import com.prideuniversal.domain.business.BusinessDtos.BusinessRequest;
import com.prideuniversal.domain.business.BusinessDtos.BusinessResponse;
import com.prideuniversal.domain.business.BusinessDtos.ReviewRequest;
import com.prideuniversal.domain.flat.FlatRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Members' businesses.
 *
 * <ul>
 *   <li><b>Members</b> (residents and the committee; not the guards) see approved listings, and
 *       their own in any state. A member lists up to {@value #MAX_PER_MEMBER} businesses.</li>
 *   <li>A member's new listing waits for the <b>committee</b>, which approves, rejects (with a note
 *       the owner sees) or later hides it. The committee's own listings go up approved. An owner who
 *       fixes a rejected listing sends it back for review.</li>
 *   <li>Approved listings stay approved through the owner's edits: owners are neighbours with a flat
 *       number on every listing, and the committee can hide one at any time.</li>
 * </ul>
 */
@Service
public class BusinessService {

    private static final String MEMBERS = "isAuthenticated() and !hasRole('SECURITY')";
    private static final String COMMITTEE =
            "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','TREASURER','COMMITTEE_MEMBER')";
    private static final ZoneId SOCIETY_ZONE = ZoneId.of("Asia/Kolkata");

    static final int MAX_PER_MEMBER = 3;

    private final MemberBusinessRepository businessRepository;
    private final FlatRepository flatRepository;

    public BusinessService(MemberBusinessRepository businessRepository, FlatRepository flatRepository) {
        this.businessRepository = businessRepository;
        this.flatRepository = flatRepository;
    }

    /** Live offers first, then the most recently updated. */
    @PreAuthorize(MEMBERS)
    @Transactional(readOnly = true)
    public List<BusinessResponse> list(BusinessCategory category, BusinessStatus status) {
        AuthPrincipal caller = AuthPrincipal.current();
        LocalDate today = LocalDate.now(SOCIETY_ZONE);
        List<MemberBusiness> rows = caller.isManagement()
                ? businessRepository.findAllByOrderByUpdatedDateDesc()
                : businessRepository.findAllByStatusOrOwnerUserIdOrderByUpdatedDateDesc(BusinessStatus.APPROVED, caller.userId());
        return rows.stream()
                .filter(b -> category == null || b.getCategory() == category)
                .filter(b -> status == null || b.getStatus() == status)
                .sorted(Comparator.comparing((MemberBusiness b) -> !b.offerActive(today)))
                .map(b -> response(b, caller, today))
                .toList();
    }

    @PreAuthorize(MEMBERS)
    @Transactional(readOnly = true)
    public BusinessResponse get(UUID id) {
        AuthPrincipal caller = AuthPrincipal.current();
        MemberBusiness b = find(id);
        if (b.getStatus() != BusinessStatus.APPROVED && !mayEdit(b, caller)) {
            throw ApiException.notFound("Business", id);
        }
        return response(b, caller, LocalDate.now(SOCIETY_ZONE));
    }

    @PreAuthorize(MEMBERS)
    @Transactional
    public BusinessResponse create(BusinessRequest request) {
        AuthPrincipal caller = AuthPrincipal.current();
        if (caller.flatId() == null && !caller.isManagement()) {
            throw ApiException.forbidden("Only residents can list a business.");
        }
        long live = businessRepository.countByOwnerUserIdAndStatusIn(caller.userId(),
                EnumSet.of(BusinessStatus.PENDING, BusinessStatus.APPROVED, BusinessStatus.HIDDEN));
        if (live >= MAX_PER_MEMBER) {
            throw ApiException.conflict("You can list up to " + MAX_PER_MEMBER + " businesses.");
        }

        MemberBusiness b = new MemberBusiness();
        b.setOwnerUserId(caller.userId());
        b.setOwnerName(caller.displayName());
        if (caller.flatId() != null) {
            flatRepository.findById(caller.flatId()).ifPresent(flat -> {
                b.setFlatId(flat.getId());
                b.setWing(flat.getWing());
                b.setFlatNumber(flat.getFlatNumber());
            });
        }
        apply(request, b);
        if (caller.isManagement()) {
            b.setStatus(BusinessStatus.APPROVED);
            b.setReviewedBy(caller.displayName());
        } else {
            b.setStatus(BusinessStatus.PENDING);
        }
        return response(businessRepository.saveAndFlush(b), caller, LocalDate.now(SOCIETY_ZONE));
    }

    @PreAuthorize(MEMBERS)
    @Transactional
    public BusinessResponse update(UUID id, BusinessRequest request) {
        AuthPrincipal caller = AuthPrincipal.current();
        MemberBusiness b = findEditable(id, caller);
        Versions.requireCurrent(b.getVersion(), request.version(), "business");
        apply(request, b);
        if (b.getStatus() == BusinessStatus.REJECTED && !caller.isManagement()) {
            b.setStatus(BusinessStatus.PENDING);   // fixed and sent back for review
            b.setReviewNote(null);
        }
        return response(businessRepository.saveAndFlush(b), caller, LocalDate.now(SOCIETY_ZONE));
    }

    @PreAuthorize(COMMITTEE)
    @Transactional
    public BusinessResponse review(UUID id, ReviewRequest request) {
        AuthPrincipal caller = AuthPrincipal.current();
        if (request.status() == BusinessStatus.PENDING) {
            throw ApiException.badRequest("A review approves, rejects or hides a listing.");
        }
        MemberBusiness b = find(id);
        b.setStatus(request.status());
        b.setReviewedBy(caller.displayName());
        b.setReviewNote(Text.blankToNull(request.note()));
        return response(businessRepository.saveAndFlush(b), caller, LocalDate.now(SOCIETY_ZONE));
    }

    @PreAuthorize(MEMBERS)
    @Transactional
    public void delete(UUID id) {
        businessRepository.delete(findEditable(id, AuthPrincipal.current()));
    }

    // ---------------------------------------------------------------------

    private MemberBusiness find(UUID id) {
        return businessRepository.findById(id).orElseThrow(() -> ApiException.notFound("Business", id));
    }

    private static boolean mayEdit(MemberBusiness b, AuthPrincipal caller) {
        return caller.isManagement() || b.getOwnerUserId().equals(caller.userId());
    }

    /** Another member's listing is not found unless it is approved, and then it is not theirs to change. */
    private MemberBusiness findEditable(UUID id, AuthPrincipal caller) {
        MemberBusiness b = find(id);
        if (mayEdit(b, caller)) {
            return b;
        }
        if (b.getStatus() == BusinessStatus.APPROVED) {
            throw ApiException.forbidden("Only the owner or the committee can change this listing.");
        }
        throw ApiException.notFound("Business", id);
    }

    private static void apply(BusinessRequest r, MemberBusiness b) {
        b.setName(r.name().trim());
        b.setCategory(r.category());
        b.setDescription(Text.blankToNull(r.description()));
        b.setOffer(Text.blankToNull(r.offer()));
        b.setOfferUntil(b.getOffer() == null ? null : r.offerUntil());
        b.setContact(r.contact());
        b.setWhatsapp(Text.blankToNull(r.whatsapp()));
        b.setLink(link(r.link()));
        b.setHours(Text.blankToNull(r.hours()));
        b.setDelivers(r.delivers());
    }

    /** "instagram.com/x" → "https://instagram.com/x", so the app can open it as it is. */
    private static String link(String raw) {
        String l = Text.blankToNull(raw);
        if (l == null) {
            return null;
        }
        return l.matches("(?i)^https?://.*") ? l : "https://" + l;
    }

    private static BusinessResponse response(MemberBusiness b, AuthPrincipal caller, LocalDate today) {
        boolean mine = b.getOwnerUserId().equals(caller.userId());
        return new BusinessResponse(b.getId(), b.getName(), b.getCategory(), b.getDescription(),
                b.getOffer(), b.getOfferUntil(), b.offerActive(today), b.getContact(), b.getWhatsapp(), b.getLink(),
                b.getHours(), b.isDelivers(), b.getOwnerName(), b.getWing(), b.getFlatNumber(), b.getStatus(),
                mine || caller.isManagement() ? b.getReviewNote() : null, mine,
                b.getCreatedDate(), b.getUpdatedDate(), b.getVersion());
    }
}
