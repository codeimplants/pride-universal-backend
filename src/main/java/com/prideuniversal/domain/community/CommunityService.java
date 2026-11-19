package com.prideuniversal.domain.community;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Role;
import com.prideuniversal.common.Text;
import com.prideuniversal.domain.community.CommunityDtos.AmenityRequest;
import com.prideuniversal.domain.community.CommunityDtos.AmenityResponse;
import com.prideuniversal.domain.community.CommunityDtos.BookingRequest;
import com.prideuniversal.domain.community.CommunityDtos.BookingResponse;
import com.prideuniversal.domain.community.CommunityDtos.PollRequest;
import com.prideuniversal.domain.community.CommunityDtos.PollResponse;
import com.prideuniversal.domain.community.CommunityDtos.SlotRequest;
import com.prideuniversal.domain.community.CommunityDtos.SlotResponse;
import com.prideuniversal.domain.community.CommunityDtos.SosRequest;
import com.prideuniversal.domain.community.CommunityDtos.SosResponse;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.notification.GateAlerts;
import com.prideuniversal.domain.visitor.VisitorService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Community features around the gate: SOS, amenity booking, parking slots and polls.
 *
 * <ul>
 *   <li>SOS: anyone signed in raises one; guards and the committee are alerted, acknowledge and resolve.</li>
 *   <li>Amenities: the committee sets them up; flats book free slots within opening hours, without clashes.</li>
 *   <li>Parking: the committee assigns slots; a flat sees its own, the gate sees all.</li>
 *   <li>Polls: the committee asks; each flat has one vote, which it may change until the poll closes.</li>
 * </ul>
 */
@Service
public class CommunityService {

    private static final String COMMITTEE = "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','TREASURER','COMMITTEE_MEMBER')";
    private static final String GATE = "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','TREASURER','COMMITTEE_MEMBER','SECURITY')";
    private static final EnumSet<Role> RESPONDERS = EnumSet.of(Role.SECURITY, Role.ADMIN, Role.CHAIRMAN,
            Role.SECRETARY, Role.TREASURER, Role.COMMITTEE_MEMBER);
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int BOOK_AHEAD_DAYS = 60;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SosRepository sos;
    private final AmenityRepository amenities;
    private final AmenityBookingRepository bookings;
    private final ParkingSlotRepository slots;
    private final PollRepository polls;
    private final PollVoteRepository votes;
    private final FlatRepository flats;
    private final GateAlerts alerts;

    public CommunityService(SosRepository sos, AmenityRepository amenities, AmenityBookingRepository bookings,
                            ParkingSlotRepository slots, PollRepository polls, PollVoteRepository votes,
                            FlatRepository flats, GateAlerts alerts) {
        this.sos = sos;
        this.amenities = amenities;
        this.bookings = bookings;
        this.slots = slots;
        this.polls = polls;
        this.votes = votes;
        this.flats = flats;
        this.alerts = alerts;
    }

    // =====================================================================
    // SOS
    // =====================================================================

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public SosResponse raise(SosRequest r) {
        AuthPrincipal caller = AuthPrincipal.current();
        SosAlert s = new SosAlert();
        s.setRaisedBy(caller.displayName());
        s.setRaisedById(caller.userId());
        s.setMobile(caller.mobile());
        s.setFlatLabel(caller.flatId() == null ? null
                : flats.findById(caller.flatId()).map(f -> f.getWing() + "-" + f.getFlatNumber()).orElse(null));
        s.setKind(r.kind());
        s.setMessage(Text.blankToNull(r.message()));
        s.setStatus("open");
        SosAlert saved = sos.save(s);
        alerts.toRoles(RESPONDERS, "sos", saved.getRaisedBy(),
                saved.getFlatLabel() == null ? "-" : saved.getFlatLabel(), saved.getKind(), saved.getId().toString(), "sos");
        return SosResponse.from(saved);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<SosResponse> sosList() {
        AuthPrincipal caller = AuthPrincipal.current();
        List<SosAlert> list = VisitorService.isGate(caller) ? sos.latest() : sos.mine(caller.userId());
        return list.stream().map(SosResponse::from).toList();
    }

    @PreAuthorize(GATE)
    @Transactional
    public SosResponse acknowledge(UUID id) {
        SosAlert s = sos.findById(id).orElseThrow(() -> ApiException.notFound("SOS", id));
        if (!"open".equals(s.getStatus())) {
            throw ApiException.conflict("Someone is already on it: " + s.getAcknowledgedBy() + ".");
        }
        s.setStatus("acknowledged");
        s.setAcknowledgedBy(AuthPrincipal.current().displayName());
        s.setAcknowledgedAt(Instant.now());
        return SosResponse.from(sos.saveAndFlush(s));
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public SosResponse resolve(UUID id) {
        AuthPrincipal caller = AuthPrincipal.current();
        SosAlert s = sos.findById(id)
                .filter(x -> VisitorService.isGate(caller) || caller.userId().equals(x.getRaisedById()))
                .orElseThrow(() -> ApiException.notFound("SOS", id));
        if ("resolved".equals(s.getStatus())) {
            throw ApiException.conflict("Already resolved.");
        }
        s.setStatus("resolved");
        s.setResolvedAt(Instant.now());
        if (s.getAcknowledgedBy() == null) {
            s.setAcknowledgedBy(caller.displayName());
            s.setAcknowledgedAt(s.getResolvedAt());
        }
        return SosResponse.from(sos.saveAndFlush(s));
    }

    // =====================================================================
    // Amenities
    // =====================================================================

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<AmenityResponse> amenities() {
        return amenities.allActive().stream().map(AmenityResponse::from).toList();
    }

    @PreAuthorize(COMMITTEE)
    @Transactional
    public AmenityResponse saveAmenity(UUID id, AmenityRequest r) {
        if (!r.closeTime().isAfter(r.openTime())) {
            throw ApiException.badRequest("Closing time must be after opening time.");
        }
        Amenity a = id == null ? new Amenity() : amenities.findById(id).filter(Amenity::isActive)
                .orElseThrow(() -> ApiException.notFound("Amenity", id));
        a.setName(r.name().trim());
        a.setDescription(Text.blankToNull(r.description()));
        a.setOpenTime(r.openTime());
        a.setCloseTime(r.closeTime());
        a.setMaxHours(r.maxHours());
        a.setFee(r.fee() == null ? BigDecimal.ZERO : r.fee());
        a.setActive(true);
        return AmenityResponse.from(amenities.saveAndFlush(a));
    }

    @PreAuthorize(COMMITTEE)
    @Transactional
    public void removeAmenity(UUID id) {
        Amenity a = amenities.findById(id).orElseThrow(() -> ApiException.notFound("Amenity", id));
        a.setActive(false);
        amenities.saveAndFlush(a);
    }

    /** One amenity's bookings on a day, so a flat can see what's free. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<BookingResponse> bookingsOn(UUID amenityId, LocalDate day) {
        Amenity a = amenity(amenityId);
        UUID me = AuthPrincipal.current().flatId();
        return bookings.onDay(amenityId, day).stream().map(b -> booking(b, a.getName(), me)).toList();
    }

    /** The caller's flat's upcoming bookings; the committee sees all upcoming. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings() {
        AuthPrincipal caller = AuthPrincipal.current();
        LocalDate today = LocalDate.now(ZONE);
        List<AmenityBooking> list = caller.isManagement() ? bookings.upcoming(today)
                : caller.flatId() == null ? List.of() : bookings.upcomingForFlat(caller.flatId(), today);
        Map<UUID, String> names = amenities.findAll().stream().collect(Collectors.toMap(Amenity::getId, Amenity::getName));
        return list.stream().map(b -> booking(b, names.getOrDefault(b.getAmenityId(), "?"), caller.flatId())).toList();
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public BookingResponse book(UUID amenityId, BookingRequest r) {
        AuthPrincipal caller = AuthPrincipal.current();
        if (caller.flatId() == null && !caller.isManagement()) {
            throw ApiException.badRequest("Only a flat can book an amenity.");
        }
        Amenity a = amenity(amenityId);
        LocalDate today = LocalDate.now(ZONE);
        if (r.bookingDate().isBefore(today) || r.bookingDate().isAfter(today.plusDays(BOOK_AHEAD_DAYS))) {
            throw ApiException.badRequest("Book between today and " + BOOK_AHEAD_DAYS + " days ahead.");
        }
        if (!r.endTime().isAfter(r.startTime())) {
            throw ApiException.badRequest("The end time must be after the start time.");
        }
        if (r.startTime().isBefore(a.getOpenTime()) || r.endTime().isAfter(a.getCloseTime())) {
            throw ApiException.badRequest(a.getName() + " is open " + a.getOpenTime() + " to " + a.getCloseTime() + ".");
        }
        if (Duration.between(r.startTime(), r.endTime()).toMinutes() > a.getMaxHours() * 60L) {
            throw ApiException.badRequest("At most " + a.getMaxHours() + " hours per booking.");
        }
        for (AmenityBooking b : bookings.onDay(amenityId, r.bookingDate())) {
            if (r.startTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(r.endTime())) {
                throw ApiException.conflict("Already booked " + b.getStartTime() + "–" + b.getEndTime()
                        + (b.getFlatLabel() == null ? "" : " by " + b.getFlatLabel()) + ".");
            }
        }
        AmenityBooking b = new AmenityBooking();
        b.setAmenityId(amenityId);
        b.setFlatId(caller.flatId());
        b.setFlatLabel(caller.flatId() == null ? "Committee"
                : flats.findById(caller.flatId()).map(f -> f.getWing() + "-" + f.getFlatNumber()).orElse(null));
        b.setBookedBy(caller.displayName());
        b.setBookedById(caller.userId());
        b.setBookingDate(r.bookingDate());
        b.setStartTime(r.startTime());
        b.setEndTime(r.endTime());
        b.setPurpose(Text.blankToNull(r.purpose()));
        return booking(bookings.saveAndFlush(b), a.getName(), caller.flatId());
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void cancelBooking(UUID id) {
        AuthPrincipal caller = AuthPrincipal.current();
        AmenityBooking b = bookings.findById(id)
                .filter(x -> caller.isManagement() || (caller.flatId() != null && caller.flatId().equals(x.getFlatId()))
                        || caller.userId().equals(x.getBookedById()))
                .orElseThrow(() -> ApiException.notFound("Booking", id));
        b.setCancelled(true);
        bookings.saveAndFlush(b);
    }

    // =====================================================================
    // Parking
    // =====================================================================

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<SlotResponse> slots() {
        AuthPrincipal caller = AuthPrincipal.current();
        List<ParkingSlot> list = VisitorService.isGate(caller) ? slots.allSlots()
                : caller.flatId() == null ? List.of() : slots.findByFlatIdOrderByLabel(caller.flatId());
        Map<UUID, String> label = flats.findAllById(list.stream().map(ParkingSlot::getFlatId)
                        .filter(java.util.Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(Flat::getId, f -> f.getWing() + "-" + f.getFlatNumber()));
        return list.stream().map(s -> new SlotResponse(s.getId(), s.getLabel(), s.getKind(),
                s.getFlatId() == null ? null : label.get(s.getFlatId()), s.getVehicleNumber(), s.getNotes())).toList();
    }

    @PreAuthorize(COMMITTEE)
    @Transactional
    public SlotResponse saveSlot(UUID id, SlotRequest r) {
        String label = r.label().trim().toUpperCase();
        ParkingSlot s;
        if (id == null) {
            if (slots.existsByLabel(label)) {
                throw ApiException.conflict("Slot " + label + " already exists.");
            }
            s = new ParkingSlot();
        } else {
            s = slots.findById(id).orElseThrow(() -> ApiException.notFound("Parking slot", id));
        }
        s.setLabel(label);
        s.setKind(r.kind());
        Flat flat = null;
        String f = Text.blankToNull(r.flat());
        if (f != null) {
            String l = f.toUpperCase().replaceAll("\\s+", "");
            int dash = l.indexOf('-');
            if (dash < 1 || dash == l.length() - 1) {
                throw ApiException.badRequest("Write the flat as wing-number, for example A-101.");
            }
            flat = flats.findByWingAndFlatNumber(l.substring(0, dash), l.substring(dash + 1)).filter(Flat::isActive)
                    .orElseThrow(() -> ApiException.badRequest("There is no flat " + l + "."));
        }
        s.setFlatId(flat == null ? null : flat.getId());
        String v = Text.blankToNull(r.vehicleNumber());
        s.setVehicleNumber(v == null ? null : v.replaceAll("\\s+", "").toUpperCase());
        s.setNotes(Text.blankToNull(r.notes()));
        slots.saveAndFlush(s);
        return new SlotResponse(s.getId(), s.getLabel(), s.getKind(),
                flat == null ? null : flat.getWing() + "-" + flat.getFlatNumber(), s.getVehicleNumber(), s.getNotes());
    }

    @PreAuthorize(COMMITTEE)
    @Transactional
    public void removeSlot(UUID id) {
        slots.delete(slots.findById(id).orElseThrow(() -> ApiException.notFound("Parking slot", id)));
    }

    // =====================================================================
    // Polls
    // =====================================================================

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public List<PollResponse> polls() {
        UUID flat = AuthPrincipal.current().flatId();
        return polls.latest().stream().map(p -> poll(p, flat)).toList();
    }

    @PreAuthorize(COMMITTEE)
    @Transactional
    public PollResponse createPoll(PollRequest r) {
        if (r.closesOn().isBefore(LocalDate.now(ZONE))) {
            throw ApiException.badRequest("The closing day can't be in the past.");
        }
        Poll p = new Poll();
        p.setQuestion(r.question().trim());
        p.setOptions(json(r.options().stream().map(String::trim).toList()));
        p.setClosesOn(r.closesOn());
        p.setCreatedBy(AuthPrincipal.current().displayName());
        Poll saved = polls.saveAndFlush(p);
        alerts.toAll("poll", saved.getQuestion(), null, saved.getId().toString(), "polls");
        return poll(saved, AuthPrincipal.current().flatId());
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public PollResponse vote(UUID id, int option) {
        AuthPrincipal caller = AuthPrincipal.current();
        if (caller.flatId() == null) {
            throw ApiException.badRequest("Votes are one per flat; your account isn't linked to a flat.");
        }
        Poll p = polls.findById(id).orElseThrow(() -> ApiException.notFound("Poll", id));
        if (!open(p)) {
            throw ApiException.conflict("This poll has closed.");
        }
        if (option < 0 || option >= options(p).size()) {
            throw ApiException.badRequest("Unknown choice.");
        }
        PollVote v = votes.findByPollIdAndFlatId(id, caller.flatId()).orElseGet(PollVote::new);
        v.setPollId(id);
        v.setFlatId(caller.flatId());
        v.setOptionIndex(option);
        v.setVotedBy(caller.displayName());
        votes.saveAndFlush(v);
        return poll(p, caller.flatId());
    }

    @PreAuthorize(COMMITTEE)
    @Transactional
    public PollResponse closePoll(UUID id) {
        Poll p = polls.findById(id).orElseThrow(() -> ApiException.notFound("Poll", id));
        p.setClosed(true);
        return poll(polls.saveAndFlush(p), AuthPrincipal.current().flatId());
    }

    // ---------------------------------------------------------------------

    private Amenity amenity(UUID id) {
        return amenities.findById(id).filter(Amenity::isActive).orElseThrow(() -> ApiException.notFound("Amenity", id));
    }

    private static BookingResponse booking(AmenityBooking b, String name, UUID myFlat) {
        return new BookingResponse(b.getId(), b.getAmenityId(), name, b.getFlatLabel(), b.getBookedBy(),
                b.getBookingDate(), b.getStartTime(), b.getEndTime(), b.getPurpose(),
                myFlat != null && myFlat.equals(b.getFlatId()));
    }

    private static boolean open(Poll p) {
        return !p.isClosed() && !LocalDate.now(ZONE).isAfter(p.getClosesOn());
    }

    private PollResponse poll(Poll p, UUID myFlat) {
        List<String> opts = options(p);
        List<PollVote> all = votes.findByPollId(p.getId());
        Map<Integer, Long> counts = all.stream().collect(Collectors.groupingBy(PollVote::getOptionIndex, Collectors.counting()));
        List<Long> tally = new ArrayList<>();
        for (int i = 0; i < opts.size(); i++) {
            tally.add(counts.getOrDefault(i, 0L));
        }
        Integer mine = myFlat == null ? null : all.stream().filter(v -> myFlat.equals(v.getFlatId()))
                .map(PollVote::getOptionIndex).findFirst().orElse(null);
        return new PollResponse(p.getId(), p.getQuestion(), opts, tally, all.size(), mine, p.getClosesOn(), open(p),
                p.getCreatedBy(), p.getCreatedDate());
    }

    private static List<String> options(Poll p) {
        try {
            return JSON.readValue(p.getOptions(), new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
