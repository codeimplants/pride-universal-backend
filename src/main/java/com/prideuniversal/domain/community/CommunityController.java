package com.prideuniversal.domain.community;

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
import com.prideuniversal.domain.community.CommunityDtos.VoteRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Community", description = "SOS, amenity booking, parking and polls")
public class CommunityController {

    private final CommunityService service;

    public CommunityController(CommunityService service) {
        this.service = service;
    }

    // ---- SOS ----

    @PostMapping("/sos")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Raise an emergency: every guard and the committee are alerted")
    public SosResponse raise(@Valid @RequestBody SosRequest request) {
        return service.raise(request);
    }

    @GetMapping("/sos")
    public List<SosResponse> sos() {
        return service.sosList();
    }

    @PostMapping("/sos/{id}/acknowledge")
    public SosResponse acknowledge(@PathVariable UUID id) {
        return service.acknowledge(id);
    }

    @PostMapping("/sos/{id}/resolve")
    public SosResponse resolve(@PathVariable UUID id) {
        return service.resolve(id);
    }

    // ---- Amenities ----

    @GetMapping("/amenities")
    public List<AmenityResponse> amenities() {
        return service.amenities();
    }

    @PostMapping("/amenities")
    @ResponseStatus(HttpStatus.CREATED)
    public AmenityResponse addAmenity(@Valid @RequestBody AmenityRequest request) {
        return service.saveAmenity(null, request);
    }

    @PutMapping("/amenities/{id}")
    public AmenityResponse updateAmenity(@PathVariable UUID id, @Valid @RequestBody AmenityRequest request) {
        return service.saveAmenity(id, request);
    }

    @DeleteMapping("/amenities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAmenity(@PathVariable UUID id) {
        service.removeAmenity(id);
    }

    @GetMapping("/amenities/{id}/bookings")
    public List<BookingResponse> bookingsOn(@PathVariable UUID id, @RequestParam LocalDate date) {
        return service.bookingsOn(id, date);
    }

    @PostMapping("/amenities/{id}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(@PathVariable UUID id, @Valid @RequestBody BookingRequest request) {
        return service.book(id, request);
    }

    @GetMapping("/bookings")
    @Operation(summary = "Upcoming bookings: the caller's flat's, or all for the committee")
    public List<BookingResponse> myBookings() {
        return service.myBookings();
    }

    @PostMapping("/bookings/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelBooking(@PathVariable UUID id) {
        service.cancelBooking(id);
    }

    // ---- Parking ----

    @GetMapping("/parking")
    public List<SlotResponse> parking() {
        return service.slots();
    }

    @PostMapping("/parking")
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse addSlot(@Valid @RequestBody SlotRequest request) {
        return service.saveSlot(null, request);
    }

    @PutMapping("/parking/{id}")
    public SlotResponse updateSlot(@PathVariable UUID id, @Valid @RequestBody SlotRequest request) {
        return service.saveSlot(id, request);
    }

    @DeleteMapping("/parking/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSlot(@PathVariable UUID id) {
        service.removeSlot(id);
    }

    // ---- Polls ----

    @GetMapping("/polls")
    public List<PollResponse> polls() {
        return service.polls();
    }

    @PostMapping("/polls")
    @ResponseStatus(HttpStatus.CREATED)
    public PollResponse createPoll(@Valid @RequestBody PollRequest request) {
        return service.createPoll(request);
    }

    @PostMapping("/polls/{id}/vote")
    public PollResponse vote(@PathVariable UUID id, @Valid @RequestBody VoteRequest request) {
        return service.vote(id, request.option());
    }

    @PostMapping("/polls/{id}/close")
    public PollResponse closePoll(@PathVariable UUID id) {
        return service.closePoll(id);
    }
}
