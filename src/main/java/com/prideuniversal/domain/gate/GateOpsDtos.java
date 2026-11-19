package com.prideuniversal.domain.gate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public final class GateOpsDtos {

    private GateOpsDtos() {}

    public record ParcelRequest(
            @NotBlank(message = "Wing is required") @Size(max = 8) String wing,
            @NotBlank(message = "Flat number is required") @Size(max = 16) String flatNumber,
            @Size(max = 80) String carrier,
            @Size(max = 255) String description) {}

    public record ParcelResponse(UUID id, String wing, String flatNumber, String carrier, String description,
                                 String receivedBy, Instant createdDate, Instant collectedAt, String collectedBy) {
        static ParcelResponse from(Parcel p) {
            return new ParcelResponse(p.getId(), p.getWing(), p.getFlatNumber(), p.getCarrier(), p.getDescription(),
                    p.getReceivedBy(), p.getCreatedDate(), p.getCollectedAt(), p.getCollectedBy());
        }
    }

    public record BlacklistRequest(
            @NotBlank(message = "Name is required") @Size(max = 160) String name,
            @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a valid 10-digit Indian mobile number") String mobile,
            @Size(max = 24) String vehicleNumber,
            @Size(max = 255) String reason) {}

    public record BlacklistResponse(UUID id, String name, String mobile, String vehicleNumber, String reason,
                                    String addedBy, Instant createdDate) {
        static BlacklistResponse from(BlacklistEntry b) {
            return new BlacklistResponse(b.getId(), b.getName(), b.getMobile(), b.getVehicleNumber(), b.getReason(),
                    b.getAddedBy(), b.getCreatedDate());
        }
    }

    public record ChildExitRequest(
            @NotBlank(message = "Wing is required") @Size(max = 8) String wing,
            @NotBlank(message = "Flat number is required") @Size(max = 16) String flatNumber,
            @NotBlank(message = "Name is required") @Size(max = 120) String childName) {}

    public record CheckpointRequest(@NotBlank(message = "Name is required") @Size(max = 120) String name) {}

    public record CheckpointResponse(UUID id, String name, String code, Instant lastScan, String lastGuard) {}

    public record ScanRequest(@NotBlank(message = "Enter the checkpoint code")
                              @Pattern(regexp = "^[0-9]{6}$", message = "The code is 6 digits") String code,
                              @Size(max = 255) String note) {}

    public record ScanResponse(UUID id, String checkpoint, String guardName, String note, Instant createdDate) {}

    /** Gate activity between two days, for the committee. */
    public record GateReport(LocalDate from, LocalDate to, long visitors, Map<String, Long> visitorsByPurpose,
                             Map<String, Long> visitorsByStatus, Map<LocalDate, Long> visitorsByDay,
                             long parcels, long helpVisits, long patrolScans, long sosAlerts) {}
}
