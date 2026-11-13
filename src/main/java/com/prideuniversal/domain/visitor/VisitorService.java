package com.prideuniversal.domain.visitor;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Role;
import com.prideuniversal.common.Text;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.visitor.dto.VisitorDtos.LogVisitorRequest;
import com.prideuniversal.domain.visitor.dto.VisitorDtos.VisitorResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The gate register.
 *
 * <p>A visit moves {@code pending → approved | rejected}, then {@code approved →
 * checked_out}. Each step is a dedicated endpoint, not a free status edit, so a
 * visitor can't be checked out before being let in or approved twice. Two guards
 * acting on the same visit at once are serialised by the row's {@code @Version}:
 * the second gets a 409.
 *
 * <ul>
 *   <li><b>Gate staff</b> (security and the committee) log visitors, see the whole
 *       register, and check visitors out.</li>
 *   <li><b>Residents</b> see their own flat's visitors and approve or reject the
 *       ones waiting for them. Gate staff may also approve, e.g. after phoning the flat.</li>
 * </ul>
 */
@Service
public class VisitorService {

    private static final String GATE =
            "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','TREASURER','COMMITTEE_MEMBER','SECURITY')";

    private final VisitorRepository visitorRepository;
    private final FlatRepository flatRepository;

    public VisitorService(VisitorRepository visitorRepository, FlatRepository flatRepository) {
        this.visitorRepository = visitorRepository;
        this.flatRepository = flatRepository;
    }

    @Transactional(readOnly = true)
    public List<VisitorResponse> list(VisitorStatus status) {
        AuthPrincipal caller = AuthPrincipal.current();
        List<Visitor> visitors;
        if (isGate(caller)) {
            visitors = visitorRepository.search(status);
        } else if (caller.flatId() != null) {
            visitors = visitorRepository.forFlat(caller.flatId(), status);
        } else {
            visitors = List.of();
        }
        return visitors.stream().map(VisitorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VisitorResponse get(UUID id) {
        return VisitorResponse.from(findVisible(id, AuthPrincipal.current()));
    }

    @PreAuthorize(GATE)
    @Transactional
    public VisitorResponse log(LogVisitorRequest request) {
        AuthPrincipal guard = AuthPrincipal.current();
        String wing = request.wing().trim().toUpperCase();
        String number = request.flatNumber().trim();
        Flat flat = flatRepository.findByWingAndFlatNumber(wing, number)
                .filter(Flat::isActive)
                .orElseThrow(() -> ApiException.badRequest("There is no flat " + wing + "-" + number + "."));

        Visitor v = new Visitor();
        v.setVisitorName(request.visitorName().trim());
        v.setVisitorContact(Text.blankToNull(request.visitorContact()));
        v.setPurpose(request.purpose());
        v.setFlatId(flat.getId());
        v.setWing(flat.getWing());
        v.setFlatNumber(flat.getFlatNumber());
        v.setResidentName(flat.residentName());
        v.setVehicleNumber(normaliseVehicle(request.vehicleNumber()));
        v.setNotes(Text.blankToNull(request.notes()));
        v.setCreatedBySecurity(guard.displayName());

        if (request.awaitApproval()) {
            v.setStatus(VisitorStatus.PENDING);
        } else {
            admit(v, guard);
        }
        return VisitorResponse.from(visitorRepository.save(v));
    }

    @Transactional
    public VisitorResponse approve(UUID id) {
        AuthPrincipal caller = AuthPrincipal.current();
        Visitor v = findActionable(id, caller);
        requireStatus(v, VisitorStatus.PENDING, "approved");
        admit(v, caller);
        return VisitorResponse.from(visitorRepository.saveAndFlush(v));
    }

    @Transactional
    public VisitorResponse reject(UUID id, String reason) {
        AuthPrincipal caller = AuthPrincipal.current();
        Visitor v = findActionable(id, caller);
        requireStatus(v, VisitorStatus.PENDING, "rejected");
        v.setStatus(VisitorStatus.REJECTED);
        v.setApprovedBy(caller.displayName());
        v.setApprovedById(caller.userId());
        v.setRejectedReason(Text.blankToNull(reason));
        return VisitorResponse.from(visitorRepository.saveAndFlush(v));
    }

    @PreAuthorize(GATE)
    @Transactional
    public VisitorResponse checkOut(UUID id) {
        Visitor v = visitorRepository.findById(id).orElseThrow(() -> ApiException.notFound("Visitor", id));
        requireStatus(v, VisitorStatus.APPROVED, "checked out");
        v.setStatus(VisitorStatus.CHECKED_OUT);
        v.setExitTime(Instant.now());
        return VisitorResponse.from(visitorRepository.saveAndFlush(v));
    }

    // ---------------------------------------------------------------------

    static boolean isGate(AuthPrincipal caller) {
        return caller.isManagement() || caller.role() == Role.SECURITY;
    }

    private void admit(Visitor v, AuthPrincipal by) {
        v.setStatus(VisitorStatus.APPROVED);
        v.setEntryTime(Instant.now());
        v.setApprovedBy(by.displayName());
        v.setApprovedById(by.userId());
    }

    private Visitor findVisible(UUID id, AuthPrincipal caller) {
        return visitorRepository.findById(id)
                .filter(v -> isGate(caller) || (caller.flatId() != null && caller.flatId().equals(v.getFlatId())))
                .orElseThrow(() -> ApiException.notFound("Visitor", id));
    }

    /** Approve/reject: gate staff, or a resident of the flat being visited. */
    private Visitor findActionable(UUID id, AuthPrincipal caller) {
        return findVisible(id, caller);
    }

    private static void requireStatus(Visitor v, VisitorStatus expected, String action) {
        if (v.getStatus() != expected) {
            throw ApiException.conflict("This visitor can't be " + action + ": they are already "
                    + v.getStatus().getCode().replace('_', ' ') + ".");
        }
    }

    private static String normaliseVehicle(String vehicle) {
        String v = Text.blankToNull(vehicle);
        return v == null ? null : v.replaceAll("\\s+", "").toUpperCase();
    }
}
