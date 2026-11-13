package com.prideuniversal.domain.complaint;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Role;
import com.prideuniversal.common.Text;
import com.prideuniversal.common.Versions;
import com.prideuniversal.domain.complaint.dto.ComplaintDtos.ComplaintResponse;
import com.prideuniversal.domain.complaint.dto.ComplaintDtos.CreateComplaintRequest;
import com.prideuniversal.domain.complaint.dto.ComplaintDtos.UpdateComplaintRequest;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The helpdesk.
 *
 * <ul>
 *   <li><b>Raise:</b> anyone signed in. A resident's ticket is always about their own
 *       flat and in their own name; committee and gate staff raise on behalf of a
 *       named flat.</li>
 *   <li><b>Read:</b> the committee (every management role) sees all tickets. Anyone
 *       else sees their own flat's tickets and those they raised. In the Base44 app
 *       every resident could read every ticket, phone numbers included.</li>
 *   <li><b>Update:</b> the communications roles, who run the helpdesk. Resolving or
 *       closing stamps the resolved date the schema requires; reopening clears it.</li>
 * </ul>
 */
@Service
public class ComplaintService {

    private static final String CAN_MANAGE = "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','COMMITTEE_MEMBER')";
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final ComplaintRepository complaintRepository;
    private final FlatRepository flatRepository;

    public ComplaintService(ComplaintRepository complaintRepository, FlatRepository flatRepository) {
        this.complaintRepository = complaintRepository;
        this.flatRepository = flatRepository;
    }

    @Transactional(readOnly = true)
    public List<ComplaintResponse> list(ComplaintStatus status, String wing) {
        AuthPrincipal caller = AuthPrincipal.current();
        List<Complaint> complaints = caller.isManagement()
                ? complaintRepository.search(status, wing == null || wing.isBlank() ? null : wing.trim().toUpperCase())
                : complaintRepository.forResident(caller.flatId(), caller.userId(), status);
        return complaints.stream().map(ComplaintResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ComplaintResponse get(UUID id) {
        AuthPrincipal caller = AuthPrincipal.current();
        Complaint complaint = complaintRepository.findById(id)
                .filter(c -> canSee(caller, c))
                .orElseThrow(() -> ApiException.notFound("Complaint", id));
        return ComplaintResponse.from(complaint);
    }

    @Transactional
    public ComplaintResponse create(CreateComplaintRequest request) {
        AuthPrincipal caller = AuthPrincipal.current();
        Complaint complaint = new Complaint();

        if (caller.role() == Role.RESIDENT) {
            Flat flat = caller.flatId() == null ? null : flatRepository.findById(caller.flatId()).orElse(null);
            if (flat == null) {
                throw ApiException.badRequest("Your account is not linked to a flat. Please contact the society office.");
            }
            complaint.setFlatId(flat.getId());
            complaint.setWing(flat.getWing());
            complaint.setFlatNumber(flat.getFlatNumber());
            complaint.setRaisedBy(caller.displayName());
            complaint.setContact(caller.mobile());
        } else {
            Flat flat = resolveNamedFlat(request.wing(), request.flatNumber(), caller);
            complaint.setFlatId(flat.getId());
            complaint.setWing(flat.getWing());
            complaint.setFlatNumber(flat.getFlatNumber());
            String raisedBy = Text.blankToNull(request.raisedBy());
            complaint.setRaisedBy(raisedBy != null ? raisedBy : caller.displayName());
            complaint.setContact(Text.blankToNull(request.contact()));
        }

        complaint.setRaisedByUserId(caller.userId());
        complaint.setComplaintNumber("CMP-" + complaintRepository.nextNumber());
        complaint.setCategory(request.category());
        complaint.setTitle(request.title().trim());
        String description = Text.blankToNull(request.description());
        complaint.setDescription(description == null ? "" : description);
        complaint.setPriority(request.priority() == null ? ComplaintPriority.MEDIUM : request.priority());
        complaint.setStatus(ComplaintStatus.OPEN);

        return ComplaintResponse.from(complaintRepository.save(complaint));
    }

    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public ComplaintResponse update(UUID id, UpdateComplaintRequest request) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Complaint", id));
        Versions.requireCurrent(complaint.getVersion(), request.version(), "complaint");

        complaint.setStatus(request.status());
        if (request.priority() != null) {
            complaint.setPriority(request.priority());
        }
        complaint.setAssignedTo(Text.blankToNull(request.assignedTo()));
        complaint.setAdminNotes(Text.blankToNull(request.adminNotes()));

        if (request.status().isResolved()) {
            if (complaint.getResolvedDate() == null) {
                complaint.setResolvedDate(LocalDate.now(IST));
            }
        } else {
            complaint.setResolvedDate(null);
        }
        return ComplaintResponse.from(complaintRepository.saveAndFlush(complaint));
    }

    // ---------------------------------------------------------------------

    private boolean canSee(AuthPrincipal caller, Complaint c) {
        return caller.isManagement()
                || (caller.flatId() != null && caller.flatId().equals(c.getFlatId()))
                || Objects.equals(caller.userId(), c.getRaisedByUserId());
    }

    /** Staff name the flat; a staff member who lives in the society may default to their own. */
    private Flat resolveNamedFlat(String wing, String flatNumber, AuthPrincipal caller) {
        String w = Text.blankToNull(wing);
        String n = Text.blankToNull(flatNumber);
        if (w == null || n == null) {
            if (caller.flatId() != null) {
                return flatRepository.findById(caller.flatId())
                        .orElseThrow(() -> ApiException.badRequest("Choose the wing and flat this complaint is about."));
            }
            throw ApiException.badRequest("Choose the wing and flat this complaint is about.");
        }
        return flatRepository.findByWingAndFlatNumber(w.toUpperCase(), n)
                .orElseThrow(() -> ApiException.badRequest("There is no flat " + w.toUpperCase() + "-" + n + "."));
    }
}
