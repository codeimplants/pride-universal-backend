package com.prideuniversal.domain.gate;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Text;
import com.prideuniversal.domain.community.SosRepository;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.gate.GateOpsDtos.BlacklistRequest;
import com.prideuniversal.domain.gate.GateOpsDtos.BlacklistResponse;
import com.prideuniversal.domain.gate.GateOpsDtos.CheckpointResponse;
import com.prideuniversal.domain.gate.GateOpsDtos.GateReport;
import com.prideuniversal.domain.gate.GateOpsDtos.ParcelRequest;
import com.prideuniversal.domain.gate.GateOpsDtos.ParcelResponse;
import com.prideuniversal.domain.gate.GateOpsDtos.ScanResponse;
import com.prideuniversal.domain.notification.GateAlerts;
import com.prideuniversal.domain.visitor.Visitor;
import com.prideuniversal.domain.visitor.VisitorRepository;
import com.prideuniversal.domain.visitor.VisitorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The rest of the gate: parcels left for flats, the blacklist, telling parents a child went out,
 * the guards' patrol rounds, and the committee's report.
 */
@Service
public class GateOpsService {

    static final String COMMITTEE = "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','TREASURER','COMMITTEE_MEMBER')";

    private final ParcelRepository parcels;
    private final BlacklistRepository blacklist;
    private final CheckpointRepository checkpoints;
    private final ScanRepository scans;
    private final FlatRepository flats;
    private final VisitorRepository visitors;
    private final DailyHelpAttendanceRepository attendance;
    private final SosRepository sos;
    private final GateAlerts alerts;

    public GateOpsService(ParcelRepository parcels, BlacklistRepository blacklist, CheckpointRepository checkpoints,
                          ScanRepository scans, FlatRepository flats, VisitorRepository visitors,
                          DailyHelpAttendanceRepository attendance, SosRepository sos, GateAlerts alerts) {
        this.parcels = parcels;
        this.blacklist = blacklist;
        this.checkpoints = checkpoints;
        this.scans = scans;
        this.flats = flats;
        this.visitors = visitors;
        this.attendance = attendance;
        this.sos = sos;
        this.alerts = alerts;
    }

    // ---- Parcels --------------------------------------------------------

    @PreAuthorize(GatePassService.GATE)
    @Transactional
    public ParcelResponse logParcel(ParcelRequest r) {
        Flat flat = flat(r.wing(), r.flatNumber());
        Parcel p = new Parcel();
        p.setFlatId(flat.getId());
        p.setWing(flat.getWing());
        p.setFlatNumber(flat.getFlatNumber());
        p.setCarrier(Text.blankToNull(r.carrier()));
        p.setDescription(Text.blankToNull(r.description()));
        p.setReceivedBy(AuthPrincipal.current().displayName());
        Parcel saved = parcels.save(p);
        alerts.toFlats(List.of(flat.getId()), "parcel", saved.getCarrier() == null ? "A" : saved.getCarrier(),
                label(flat), null, saved.getId().toString());
        return ParcelResponse.from(saved);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<ParcelResponse> parcels(boolean waitingOnly) {
        AuthPrincipal caller = AuthPrincipal.current();
        List<Parcel> list;
        if (VisitorService.isGate(caller)) {
            list = parcels.latest(waitingOnly);
        } else if (caller.flatId() != null) {
            list = parcels.forFlat(caller.flatId()).stream()
                    .filter(p -> !waitingOnly || p.getCollectedAt() == null).toList();
        } else {
            list = List.of();
        }
        return list.stream().map(ParcelResponse::from).toList();
    }

    /** Handed over at the gate (the guard), or the flat saying they've got it. */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ParcelResponse collect(UUID id) {
        AuthPrincipal caller = AuthPrincipal.current();
        Parcel p = parcels.findById(id)
                .filter(x -> VisitorService.isGate(caller) || x.getFlatId().equals(caller.flatId()))
                .orElseThrow(() -> ApiException.notFound("Parcel", id));
        if (p.getCollectedAt() != null) {
            throw ApiException.conflict("This parcel was already collected.");
        }
        p.setCollectedAt(Instant.now());
        p.setCollectedBy(caller.displayName());
        return ParcelResponse.from(parcels.saveAndFlush(p));
    }

    // ---- Blacklist ------------------------------------------------------

    @PreAuthorize(GatePassService.GATE)
    @Transactional(readOnly = true)
    public List<BlacklistResponse> blacklist() {
        return blacklist.allActive().stream().map(BlacklistResponse::from).toList();
    }

    @PreAuthorize(COMMITTEE)
    @Transactional
    public BlacklistResponse ban(BlacklistRequest r) {
        String mobile = Text.blankToNull(r.mobile());
        String vehicle = vehicle(r.vehicleNumber());
        if (mobile == null && vehicle == null) {
            throw ApiException.badRequest("Give a mobile number or a vehicle number to recognise them by.");
        }
        BlacklistEntry b = new BlacklistEntry();
        b.setName(r.name().trim());
        b.setMobile(mobile);
        b.setVehicleNumber(vehicle);
        b.setReason(Text.blankToNull(r.reason()));
        b.setAddedBy(AuthPrincipal.current().displayName());
        return BlacklistResponse.from(blacklist.save(b));
    }

    @PreAuthorize(COMMITTEE)
    @Transactional
    public void unban(UUID id) {
        BlacklistEntry b = blacklist.findById(id).orElseThrow(() -> ApiException.notFound("Blacklist entry", id));
        b.setActive(false);
        blacklist.saveAndFlush(b);
    }

    // ---- Child exit -----------------------------------------------------

    @PreAuthorize(GatePassService.GATE)
    @Transactional(readOnly = true)
    public void childExit(String wing, String number, String childName) {
        Flat flat = flat(wing, number);
        alerts.toFlats(List.of(flat.getId()), "child_exit", childName.trim(), label(flat), null, null);
    }

    // ---- Patrol ---------------------------------------------------------

    @PreAuthorize(GatePassService.GATE)
    @Transactional(readOnly = true)
    public List<CheckpointResponse> checkpoints() {
        List<PatrolScan> recent = scans.since(Instant.now().minus(7, ChronoUnit.DAYS));
        Map<UUID, PatrolScan> last = new HashMap<>();
        for (PatrolScan s : recent) {
            last.putIfAbsent(s.getCheckpointId(), s);   // newest first
        }
        return checkpoints.allActive().stream().map(c -> {
            PatrolScan s = last.get(c.getId());
            return new CheckpointResponse(c.getId(), c.getName(), c.getCode(),
                    s == null ? null : s.getCreatedDate(), s == null ? null : s.getGuardName());
        }).toList();
    }

    @PreAuthorize(COMMITTEE)
    @Transactional
    public CheckpointResponse addCheckpoint(String name) {
        PatrolCheckpoint c = new PatrolCheckpoint();
        c.setName(name.trim());
        c.setCode(Codes.fresh(checkpoints::existsByCode));
        checkpoints.save(c);
        return new CheckpointResponse(c.getId(), c.getName(), c.getCode(), null, null);
    }

    @PreAuthorize(COMMITTEE)
    @Transactional
    public void removeCheckpoint(UUID id) {
        PatrolCheckpoint c = checkpoints.findById(id).orElseThrow(() -> ApiException.notFound("Checkpoint", id));
        c.setActive(false);
        checkpoints.saveAndFlush(c);
    }

    @PreAuthorize(GatePassService.GATE)
    @Transactional
    public ScanResponse scan(String code, String note) {
        AuthPrincipal guard = AuthPrincipal.current();
        PatrolCheckpoint c = checkpoints.findByCodeAndActiveTrue(code.trim())
                .orElseThrow(() -> ApiException.badRequest("No checkpoint has that code."));
        PatrolScan s = new PatrolScan();
        s.setCheckpointId(c.getId());
        s.setGuardName(guard.displayName());
        s.setGuardId(guard.userId());
        s.setNote(Text.blankToNull(note));
        scans.save(s);
        return new ScanResponse(s.getId(), c.getName(), s.getGuardName(), s.getNote(), s.getCreatedDate());
    }

    @PreAuthorize(GatePassService.GATE)
    @Transactional(readOnly = true)
    public List<ScanResponse> recentScans() {
        Map<UUID, String> names = checkpoints.findAll().stream()
                .collect(Collectors.toMap(PatrolCheckpoint::getId, PatrolCheckpoint::getName));
        return scans.since(Instant.now().minus(2, ChronoUnit.DAYS)).stream().limit(200)
                .map(s -> new ScanResponse(s.getId(), names.getOrDefault(s.getCheckpointId(), "?"), s.getGuardName(),
                        s.getNote(), s.getCreatedDate())).toList();
    }

    // ---- Report ---------------------------------------------------------

    @PreAuthorize(COMMITTEE)
    @Transactional(readOnly = true)
    public GateReport report(LocalDate from, LocalDate to) {
        LocalDate today = Codes.today();
        LocalDate f = from == null ? today.minusDays(29) : from;
        LocalDate t = to == null ? today : to;
        if (t.isBefore(f) || ChronoUnit.DAYS.between(f, t) > 366) {
            throw ApiException.badRequest("Choose a range of up to a year.");
        }
        Instant start = f.atStartOfDay(Codes.SOCIETY_ZONE).toInstant();
        Instant end = t.plusDays(1).atStartOfDay(Codes.SOCIETY_ZONE).toInstant();
        List<Visitor> vs = visitors.between(start, end);
        Function<Visitor, LocalDate> day = v -> v.getCreatedDate().atZone(Codes.SOCIETY_ZONE).toLocalDate();
        return new GateReport(f, t, vs.size(),
                vs.stream().collect(Collectors.groupingBy(v -> v.getPurpose().getCode(), TreeMap::new, Collectors.counting())),
                vs.stream().collect(Collectors.groupingBy(v -> v.getStatus().getCode(), TreeMap::new, Collectors.counting())),
                vs.stream().collect(Collectors.groupingBy(day, TreeMap::new, Collectors.counting())),
                parcels.countBetween(start, end), attendance.countBetween(start, end),
                scans.countBetween(start, end), sos.countBetween(start, end));
    }

    // ---------------------------------------------------------------------

    private Flat flat(String wing, String number) {
        String w = wing.trim().toUpperCase();
        String n = number.trim();
        return flats.findByWingAndFlatNumber(w, n).filter(Flat::isActive)
                .orElseThrow(() -> ApiException.badRequest("There is no flat " + w + "-" + n + "."));
    }

    private static String label(Flat f) {
        return f.getWing() + "-" + f.getFlatNumber();
    }

    static String vehicle(String raw) {
        String v = Text.blankToNull(raw);
        return v == null ? null : v.replaceAll("\\s+", "").toUpperCase();
    }
}
