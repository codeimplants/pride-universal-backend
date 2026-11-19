package com.prideuniversal.domain.community;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class CommunityDtos {

    private CommunityDtos() {}

    // ---- SOS ----

    public record SosRequest(
            @NotBlank @Pattern(regexp = "^(medical|fire|security|lift|other)$", message = "Unknown kind of emergency") String kind,
            @Size(max = 500) String message) {}

    public record SosResponse(UUID id, String raisedBy, String mobile, String flatLabel, String kind, String message,
                              String status, String acknowledgedBy, Instant acknowledgedAt, Instant resolvedAt,
                              Instant createdDate) {
        static SosResponse from(SosAlert s) {
            return new SosResponse(s.getId(), s.getRaisedBy(), s.getMobile(), s.getFlatLabel(), s.getKind(),
                    s.getMessage(), s.getStatus(), s.getAcknowledgedBy(), s.getAcknowledgedAt(), s.getResolvedAt(),
                    s.getCreatedDate());
        }
    }

    // ---- Amenities ----

    public record AmenityRequest(
            @NotBlank(message = "Name is required") @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull LocalTime openTime,
            @NotNull LocalTime closeTime,
            @Min(1) @Max(24) int maxHours,
            BigDecimal fee) {}

    public record AmenityResponse(UUID id, String name, String description, LocalTime openTime, LocalTime closeTime,
                                  int maxHours, BigDecimal fee) {
        static AmenityResponse from(Amenity a) {
            return new AmenityResponse(a.getId(), a.getName(), a.getDescription(), a.getOpenTime(), a.getCloseTime(),
                    a.getMaxHours(), a.getFee());
        }
    }

    public record BookingRequest(
            @NotNull(message = "Choose a day") LocalDate bookingDate,
            @NotNull(message = "Choose a start time") LocalTime startTime,
            @NotNull(message = "Choose an end time") LocalTime endTime,
            @Size(max = 255) String purpose) {}

    public record BookingResponse(UUID id, UUID amenityId, String amenityName, String flatLabel, String bookedBy,
                                  LocalDate bookingDate, LocalTime startTime, LocalTime endTime, String purpose,
                                  boolean mine) {}

    // ---- Parking ----

    public record SlotRequest(
            @NotBlank(message = "Slot number is required") @Size(max = 24) String label,
            @NotBlank @Pattern(regexp = "^(two_wheeler|four_wheeler|visitor)$", message = "Unknown kind of slot") String kind,
            @Size(max = 24) String flat,
            @Size(max = 24) String vehicleNumber,
            @Size(max = 255) String notes) {}

    public record SlotResponse(UUID id, String label, String kind, String flat, String vehicleNumber, String notes) {}

    // ---- Polls ----

    public record PollRequest(
            @NotBlank(message = "Write the question") @Size(max = 300) String question,
            @NotNull @Size(min = 2, max = 6, message = "Give 2 to 6 choices") List<@NotBlank @Size(max = 120) String> options,
            @NotNull(message = "Choose the closing day") LocalDate closesOn) {}

    public record VoteRequest(@Min(0) int option) {}

    public record PollResponse(UUID id, String question, List<String> options, List<Long> votes, long totalVotes,
                               Integer myVote, LocalDate closesOn, boolean open, String createdBy,
                               Instant createdDate) {}
}
