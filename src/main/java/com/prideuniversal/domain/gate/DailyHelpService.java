package com.prideuniversal.domain.gate;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Text;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.gate.GateDtos.AttendanceResponse;
import com.prideuniversal.domain.gate.GateDtos.HelpRequest;
import com.prideuniversal.domain.gate.GateDtos.HelpResponse;
import com.prideuniversal.domain.notification.GateAlerts;
import com.prideuniversal.domain.visitor.VisitorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Daily help: the people who come to work every day, and their attendance at the gate.
 *
 * <ul>
 *   <li>Residents add help to their own flat (someone already registered by mobile is shared, not
 *       duplicated), see who serves them and when they came, and remove them from their flat.</li>
 *   <li>The committee adds help for any flats and removes them from the society.</li>
 *   <li>Gate staff mark them in by code and out, and see who is inside.</li>
 * </ul>
 */
@Service
public class DailyHelpService {

    private final DailyHelpRepository helpers;
    private final DailyHelpAttendanceRepository attendance;
    private final FlatRepository flats;
    private final GateAlerts alerts;

    public DailyHelpService(DailyHelpRepository helpers, DailyHelpAttendanceRepository attendance,
                            FlatRepository flats, GateAlerts alerts) {
        this.helpers = helpers;
        this.attendance = attendance;
        this.flats = flats;
        this.alerts = alerts;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<HelpResponse> list() {
        AuthPrincipal caller = AuthPrincipal.current();
        List<DailyHelp> list;
        if (VisitorService.isGate(caller)) {
            list = helpers.allActive();
        } else if (caller.flatId() != null) {
            list = helpers.forFlat(caller.flatId());
        } else {
            list = List.of();
        }
        return respond(list);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public HelpResponse add(HelpRequest r) {
        AuthPrincipal caller = AuthPrincipal.current();
        Set<UUID> flatIds = flatsFor(caller, r.flats());
        String mobile = Text.blankToNull(r.mobile());

        DailyHelp h = mobile == null ? null : helpers.findFirstByMobileAndActiveTrue(mobile).orElse(null);
        if (h == null) {
            h = new DailyHelp();
            h.setCode(Codes.fresh(helpers::existsByCode));
            h.setName(r.name().trim());
            h.setMobile(mobile);
            h.setHelpType(r.helpType());
            h.setNotes(Text.blankToNull(r.notes()));
            h.setAddedBy(caller.displayName());
        } else if (caller.isManagement()) {
            h.setName(r.name().trim());
            h.setHelpType(r.helpType());
            h.setNotes(Text.blankToNull(r.notes()));
        }
        h.getFlatIds().addAll(flatIds);
        return respond(List.of(helpers.saveAndFlush(h))).get(0);
    }

    /** Committee: edit the details and the exact list of flats. */
    @PreAuthorize("hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','TREASURER','COMMITTEE_MEMBER')")
    @Transactional
    public HelpResponse update(UUID id, HelpRequest r) {
        DailyHelp h = find(id);
        h.setName(r.name().trim());
        h.setMobile(Text.blankToNull(r.mobile()));
        h.setHelpType(r.helpType());
        h.setNotes(Text.blankToNull(r.notes()));
        h.getFlatIds().clear();
        h.getFlatIds().addAll(flatsFor(AuthPrincipal.current(), r.flats()));
        return respond(List.of(helpers.saveAndFlush(h))).get(0);
    }

    /** A resident takes them off their flat; the committee takes them off the society. */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void remove(UUID id) {
        AuthPrincipal caller = AuthPrincipal.current();
        DailyHelp h = find(id);
        if (caller.isManagement()) {
            h.setActive(false);
        } else if (caller.flatId() != null && h.getFlatIds().remove(caller.flatId())) {
            if (h.getFlatIds().isEmpty()) {
                h.setActive(false);
            }
        } else {
            throw ApiException.notFound("Daily help", id);
        }
        helpers.saveAndFlush(h);
    }

    @PreAuthorize(GatePassService.GATE)
    @Transactional
    public HelpResponse checkIn(String code) {
        DailyHelp h = helpers.findByCode(code.trim()).filter(DailyHelp::isActive)
                .orElseThrow(() -> ApiException.badRequest("No daily help has that code."));
        if (attendance.findFirstByDailyHelpIdAndExitTimeIsNull(h.getId()).isPresent()) {
            throw ApiException.conflict(h.getName() + " is already inside.");
        }
        DailyHelpAttendance a = new DailyHelpAttendance();
        a.setDailyHelpId(h.getId());
        a.setEntryTime(Instant.now());
        a.setLoggedBy(AuthPrincipal.current().displayName());
        attendance.saveAndFlush(a);
        tell(h, "help_in");
        return respond(List.of(h)).get(0);
    }

    @PreAuthorize(GatePassService.GATE)
    @Transactional
    public HelpResponse checkOut(UUID id) {
        DailyHelp h = find(id);
        DailyHelpAttendance a = attendance.findFirstByDailyHelpIdAndExitTimeIsNull(h.getId())
                .orElseThrow(() -> ApiException.conflict(h.getName() + " isn't inside."));
        a.setExitTime(Instant.now());
        attendance.saveAndFlush(a);
        tell(h, "help_out");
        return respond(List.of(h)).get(0);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<AttendanceResponse> attendance(UUID id) {
        AuthPrincipal caller = AuthPrincipal.current();
        DailyHelp h = find(id);
        boolean mine = caller.flatId() != null && h.getFlatIds().contains(caller.flatId());
        if (!VisitorService.isGate(caller) && !mine) {
            throw ApiException.notFound("Daily help", id);
        }
        return attendance.recent(id).stream().map(AttendanceResponse::from).toList();
    }

    // ---------------------------------------------------------------------

    private void tell(DailyHelp h, String event) {
        List<UUID> served = new ArrayList<>(h.getFlatIds());
        if (!served.isEmpty()) {
            alerts.toFlats(served, event, h.getName(), labels(served).get(0), h.getHelpType(), h.getId().toString());
        }
    }

    private DailyHelp find(UUID id) {
        return helpers.findById(id).filter(DailyHelp::isActive)
                .orElseThrow(() -> ApiException.notFound("Daily help", id));
    }

    /** A resident: their own flat, whatever they sent. The committee: the flats they listed. */
    private Set<UUID> flatsFor(AuthPrincipal caller, List<String> labels) {
        if (!caller.isManagement() || labels == null || labels.isEmpty()) {
            if (caller.flatId() == null) {
                throw ApiException.badRequest("Say which flats they work at, for example A-101.");
            }
            return Set.of(caller.flatId());
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (String raw : labels) {
            String label = raw.toUpperCase().replaceAll("\\s+", "");
            int dash = label.indexOf('-');
            if (dash < 1 || dash == label.length() - 1) {
                throw ApiException.badRequest("Write each flat as wing-number, for example A-101.");
            }
            Flat f = flats.findByWingAndFlatNumber(label.substring(0, dash), label.substring(dash + 1))
                    .filter(Flat::isActive)
                    .orElseThrow(() -> ApiException.badRequest("There is no flat " + label + "."));
            ids.add(f.getId());
        }
        return ids;
    }

    private List<String> labels(java.util.Collection<UUID> ids) {
        return flats.findAllById(ids).stream().map(f -> f.getWing() + "-" + f.getFlatNumber()).sorted().toList();
    }

    private List<HelpResponse> respond(List<DailyHelp> list) {
        Set<UUID> inside = new HashSet<>(attendance.insideNow());
        Set<UUID> allFlats = list.stream().flatMap(h -> h.getFlatIds().stream()).collect(Collectors.toSet());
        Map<UUID, String> label = flats.findAllById(allFlats).stream()
                .collect(Collectors.toMap(Flat::getId, f -> f.getWing() + "-" + f.getFlatNumber(), (a, b) -> a));
        Function<DailyHelp, List<String>> flatsOf = h -> h.getFlatIds().stream()
                .map(label::get).filter(java.util.Objects::nonNull).sorted().toList();
        return list.stream().map(h -> new HelpResponse(h.getId(), h.getName(), h.getMobile(), h.getHelpType(),
                h.getCode(), flatsOf.apply(h), inside.contains(h.getId()), h.getNotes(), h.getAddedBy())).toList();
    }
}
