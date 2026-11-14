package com.prideuniversal.domain.vehicle;

import java.util.List;
import java.util.UUID;

public final class VehicleDtos {

    private VehicleDtos() {}

    /** The gate's answer to "whose vehicle is this?". An empty list means not registered with the society. */
    public record VehicleLookupResponse(String query, List<VehicleMatch> matches) {}

    /**
     * One registered vehicle that answers the query.
     *
     * @param kind  {@code two_wheeler} or {@code four_wheeler}
     * @param match {@code exact}, {@code close} (OCR-tolerant) or {@code partial} (end of the plate)
     * @param contacts who to call, the occupant first: the tenant when the flat is let, then the owner
     */
    public record VehicleMatch(
            String plate,
            String kind,
            String match,
            UUID flatId,
            String wing,
            String flatNumber,
            boolean permanentParking,
            List<Contact> contacts
    ) {}

    /** @param relation {@code tenant}, {@code owner} or {@code owner_alternate} */
    public record Contact(String relation, String name, String mobile) {}
}
