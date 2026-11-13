package com.prideuniversal.domain.visitor.dto;

import com.prideuniversal.domain.visitor.Visitor;
import com.prideuniversal.domain.visitor.VisitorPurpose;
import com.prideuniversal.domain.visitor.VisitorStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class VisitorDtos {

    private VisitorDtos() {}

    /**
     * Logged at the gate. With {@code awaitApproval} the visitor waits as
     * {@code pending} until the flat (or the committee) approves; without it the
     * gate lets them straight in, as the Base44 app did.
     */
    public record LogVisitorRequest(
            @NotBlank(message = "Visitor name is required") @Size(max = 160, message = "Name is too long") String visitorName,
            @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a valid 10-digit Indian mobile number") String visitorContact,
            @NotNull(message = "Purpose is required") VisitorPurpose purpose,
            @NotBlank(message = "Wing is required") @Size(max = 8) String wing,
            @NotBlank(message = "Flat number is required") @Size(max = 16) String flatNumber,
            @Size(max = 24, message = "Vehicle number is too long") String vehicleNumber,
            @Size(max = 2_000, message = "Notes are too long") String notes,
            boolean awaitApproval
    ) {}

    public record RejectVisitorRequest(@Size(max = 255, message = "Reason is too long") String reason) {}

    public record VisitorResponse(
            UUID id,
            String visitorName,
            String visitorContact,
            VisitorPurpose purpose,
            UUID flatId,
            String wing,
            String flatNumber,
            String residentName,
            VisitorStatus status,
            Instant entryTime,
            Instant exitTime,
            String approvedBy,
            String rejectedReason,
            String photoUrl,
            String vehicleNumber,
            String notes,
            String createdBySecurity,
            Instant createdDate,
            Long version
    ) {
        public static VisitorResponse from(Visitor v) {
            return new VisitorResponse(v.getId(), v.getVisitorName(), v.getVisitorContact(), v.getPurpose(),
                    v.getFlatId(), v.getWing(), v.getFlatNumber(), v.getResidentName(), v.getStatus(),
                    v.getEntryTime(), v.getExitTime(), v.getApprovedBy(), v.getRejectedReason(), v.getPhotoUrl(),
                    v.getVehicleNumber(), v.getNotes(), v.getCreatedBySecurity(), v.getCreatedDate(), v.getVersion());
        }
    }
}
