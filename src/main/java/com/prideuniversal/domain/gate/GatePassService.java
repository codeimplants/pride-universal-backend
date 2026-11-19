package com.prideuniversal.domain.gate;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Text;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.gate.GateDtos.CreatePassRequest;
import com.prideuniversal.domain.gate.GateDtos.PassResponse;
import com.prideuniversal.domain.notification.GateAlerts;
import com.prideuniversal.domain.visitor.Visitor;
import com.prideuniversal.domain.visitor.VisitorRepository;
import com.prideuniversal.domain.visitor.VisitorService;
import com.prideuniversal.domain.visitor.VisitorStatus;
import com.prideuniversal.domain.visitor.dto.VisitorDtos.VisitorResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Gate passes: a resident lets a guest in ahead of time, the guard admits them by code.
 *
 * <ul>
 *   <li>Residents make, see and cancel passes for their own flat; the committee for any flat.</li>
 *   <li>Gate staff admit by code; that logs the visit as already approved and tells the flat.</li>
 * </ul>
 */
@Service
public class GatePassService {

    static final String GATE =
            "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','TREASURER','COMMITTEE_MEMBER','SECURITY')";
    private static final int MAX_DAYS = 90;

    private final GatePassRepository passes;
    private final FlatRepository flats;
    private final VisitorRepository visitors;
    private final GateAlerts alerts;

    public GatePassService(GatePassRepository passes, FlatRepository flats, VisitorRepository visitors,
                           GateAlerts alerts) {
        this.passes = passes;
        this.flats = flats;
        this.visitors = visitors;
        this.alerts = alerts;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public PassResponse create(CreatePassRequest r) {
        AuthPrincipal caller = AuthPrincipal.current();
        Flat flat = flatFor(caller, r.wing(), r.flatNumber());
        LocalDate today = Codes.today();
        LocalDate from = r.validFrom() == null ? today : r.validFrom();
        LocalDate until = r.validUntil() == null ? from : r.validUntil();
        if (from.isBefore(today)) {
            throw ApiException.badRequest("The pass can't start in the past.");
        }
        if (until.isBefore(from)) {
            throw ApiException.badRequest("The last day is before the first.");
        }
        if (ChronoUnit.DAYS.between(from, until) >= MAX_DAYS) {
            throw ApiException.badRequest("A pass can last at most " + MAX_DAYS + " days.");
        }

        GatePass p = new GatePass();
        p.setCode(Codes.fresh(passes::existsByCode));
        p.setFlatId(flat.getId());
        p.setWing(flat.getWing());
        p.setFlatNumber(flat.getFlatNumber());
        p.setGuestName(r.guestName().trim());
        p.setGuestContact(Text.blankToNull(r.guestContact()));
        p.setPurpose(r.purpose());
        p.setValidFrom(from);
        p.setValidUntil(until);
        p.setMultiUse(r.multiUse());
        p.setNotes(Text.blankToNull(r.notes()));
        p.setCreatedBy(caller.displayName());
        p.setCreatedById(caller.userId());
        return respond(passes.save(p));
    }

    /** The caller's flat's passes; the committee and the gate see everyone's latest. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<PassResponse> list() {
        AuthPrincipal caller = AuthPrincipal.current();
        List<GatePass> list;
        if (VisitorService.isGate(caller)) {
            list = passes.latest();
        } else if (caller.flatId() != null) {
            list = passes.forFlat(caller.flatId());
        } else {
            list = List.of();
        }
        return list.stream().map(this::respond).toList();
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public PassResponse cancel(UUID id) {
        AuthPrincipal caller = AuthPrincipal.current();
        GatePass p = passes.findById(id)
                .filter(x -> caller.canAccessFlat(x.getFlatId()))
                .orElseThrow(() -> ApiException.notFound("Gate pass", id));
        p.setCancelled(true);
        return respond(passes.saveAndFlush(p));
    }

    /** The guard types the guest's code: if it's good today, the guest is in. */
    @PreAuthorize(GATE)
    @Transactional
    public VisitorResponse use(String code) {
        AuthPrincipal guard = AuthPrincipal.current();
        GatePass p = passes.findByCode(code.trim())
                .orElseThrow(() -> ApiException.badRequest("No pass has that code."));
        String state = state(p);
        switch (state) {
            case "cancelled" -> throw ApiException.conflict("This pass was cancelled by the flat.");
            case "used" -> throw ApiException.conflict("This pass has already been used.");
            case "expired" -> throw ApiException.conflict("This pass expired on " + p.getValidUntil() + ".");
            case "upcoming" -> throw ApiException.conflict("This pass is only good from " + p.getValidFrom() + ".");
            default -> { }
        }
        p.setUses(p.getUses() + 1);
        passes.saveAndFlush(p);   // @Version: the same single-use code typed at two gates gets one 409

        Flat flat = flats.findById(p.getFlatId()).orElse(null);
        Visitor v = new Visitor();
        v.setVisitorName(p.getGuestName());
        v.setVisitorContact(p.getGuestContact());
        v.setPurpose(p.getPurpose());
        v.setFlatId(p.getFlatId());
        v.setWing(p.getWing());
        v.setFlatNumber(p.getFlatNumber());
        v.setResidentName(flat == null ? null : flat.residentName());
        v.setStatus(VisitorStatus.APPROVED);
        v.setEntryTime(Instant.now());
        v.setApprovedBy(p.getCreatedBy() + " (pass)");
        v.setApprovedById(p.getCreatedById());
        v.setPassId(p.getId());
        v.setNotes(p.getNotes());
        v.setCreatedBySecurity(guard.displayName());
        Visitor saved = visitors.save(v);

        alerts.toFlats(List.of(p.getFlatId()), "pass_used", p.getGuestName(),
                p.getWing() + "-" + p.getFlatNumber(), null, saved.getId().toString());
        return VisitorResponse.from(saved);
    }

    // ---------------------------------------------------------------------

    private PassResponse respond(GatePass p) {
        return PassResponse.from(p, state(p));
    }

    /** active | upcoming | used | expired | cancelled */
    static String state(GatePass p) {
        LocalDate today = Codes.today();
        if (p.isCancelled()) {
            return "cancelled";
        }
        if (!p.isMultiUse() && p.getUses() > 0) {
            return "used";
        }
        if (today.isAfter(p.getValidUntil())) {
            return "expired";
        }
        if (today.isBefore(p.getValidFrom())) {
            return "upcoming";
        }
        return "active";
    }

    private Flat flatFor(AuthPrincipal caller, String wing, String number) {
        if (caller.isManagement() && Text.blankToNull(wing) != null && Text.blankToNull(number) != null) {
            String w = wing.trim().toUpperCase();
            String n = number.trim();
            return flats.findByWingAndFlatNumber(w, n).filter(Flat::isActive)
                    .orElseThrow(() -> ApiException.badRequest("There is no flat " + w + "-" + n + "."));
        }
        if (caller.flatId() == null) {
            throw ApiException.badRequest("Your account isn't linked to a flat, so say which flat the pass is for.");
        }
        return flats.findById(caller.flatId()).filter(Flat::isActive)
                .orElseThrow(() -> ApiException.badRequest("Your flat is no longer active."));
    }
}
