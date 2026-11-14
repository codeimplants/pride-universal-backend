package com.prideuniversal.domain.vehicle;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Text;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.vehicle.VehicleDtos.Contact;
import com.prideuniversal.domain.vehicle.VehicleDtos.VehicleLookupResponse;
import com.prideuniversal.domain.vehicle.VehicleDtos.VehicleMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The security guard's question at the gate: is this vehicle the society's, and whose is it?
 *
 * <p>Registrations live on the flat as free text ({@code two_wheeler_numbers},
 * {@code four_wheeler_numbers}), so a lookup scans the active flats in memory. A society has a few
 * hundred flats; one indexed read and a string compare per plate is cheaper than keeping a second,
 * normalised copy of the register in step with every flat edit.
 *
 * <p>The answer carries mobile numbers, which {@code FlatSummary} deliberately hides from residents.
 * So this is the gate's and the committee's only, and it answers one plate at a time: a query must be
 * at least {@value Plates#MIN_QUERY} characters, and no more than {@value #MAX_MATCHES} vehicles come back.
 */
@Service
public class VehicleLookupService {

    private static final Logger log = LoggerFactory.getLogger(VehicleLookupService.class);

    private static final String GATE =
            "hasAnyRole('ADMIN','CHAIRMAN','SECRETARY','TREASURER','COMMITTEE_MEMBER','SECURITY')";

    static final int MAX_MATCHES = 10;

    private final FlatRepository flatRepository;

    public VehicleLookupService(FlatRepository flatRepository) {
        this.flatRepository = flatRepository;
    }

    private record Found(VehicleMatch match, Plates.Match strength) {}

    @PreAuthorize(GATE)
    @Transactional(readOnly = true)
    public VehicleLookupResponse lookup(String plate) {
        String query = Plates.normalise(plate);
        if (query.length() < Plates.MIN_QUERY) {
            throw ApiException.badRequest(
                    "Enter at least " + Plates.MIN_QUERY + " letters or digits of the number plate.");
        }

        List<Found> found = new ArrayList<>();
        for (Flat flat : flatRepository.findAllByActiveTrueOrderByWingAscFlatNumberAsc()) {
            collect(found, flat, flat.getFourWheelerNumbers(), "four_wheeler", query);
            collect(found, flat, flat.getTwoWheelerNumbers(), "two_wheeler", query);
        }

        // EXACT, then CLOSE, then PARTIAL; the sort is stable, so wing and flat order holds within each.
        List<VehicleMatch> matches = found.stream()
                .sorted(Comparator.comparing(Found::strength))
                .limit(MAX_MATCHES)
                .map(Found::match)
                .toList();

        log.info("Vehicle lookup by {}: {} match(es)", AuthPrincipal.current().userId(), matches.size());
        return new VehicleLookupResponse(query, matches);
    }

    private static void collect(List<Found> sink, Flat flat, String numbers, String kind, String query) {
        for (String registered : Plates.registered(numbers)) {
            Plates.Match strength = Plates.compare(query, registered);
            if (strength != null) {
                VehicleMatch match = new VehicleMatch(Plates.normalise(registered), kind, strength.code(),
                        flat.getId(), flat.getWing(), flat.getFlatNumber(), flat.isPermanentParking(), contacts(flat));
                sink.add(new Found(match, strength));
            }
        }
    }

    /** The occupant first: when the flat is let, the tenant is the one who parked. */
    static List<Contact> contacts(Flat flat) {
        List<Contact> out = new ArrayList<>(3);
        if (flat.isTenant() && Text.blankToNull(flat.getTenantContact()) != null) {
            out.add(new Contact("tenant", flat.getTenantName(), flat.getTenantContact()));
        }
        if (Text.blankToNull(flat.getOwnerContact()) != null) {
            out.add(new Contact("owner", flat.getOwnerName(), flat.getOwnerContact()));
        }
        if (Text.blankToNull(flat.getOwnerAlternateContact()) != null) {
            out.add(new Contact("owner_alternate", flat.getOwnerName(), flat.getOwnerAlternateContact()));
        }
        return out;
    }
}
