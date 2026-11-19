package com.prideuniversal.domain.gate;

import com.prideuniversal.domain.visitor.VisitorPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class GateDtos {

    private GateDtos() {}

    private static final String MOBILE = "^[6-9][0-9]{9}$";
    private static final String MOBILE_MSG = "Enter a valid 10-digit Indian mobile number";

    // ---- Gate passes ----------------------------------------------------

    /**
     * A resident's pass is always for their own flat; the committee names the flat. No dates means today;
     * {@code multiUse} lets the guest in any number of times until {@code validUntil}.
     */
    public record CreatePassRequest(
            @NotBlank(message = "Guest name is required") @Size(max = 160) String guestName,
            @Pattern(regexp = MOBILE, message = MOBILE_MSG) String guestContact,
            @NotNull(message = "Purpose is required") VisitorPurpose purpose,
            LocalDate validFrom,
            LocalDate validUntil,
            boolean multiUse,
            @Size(max = 8) String wing,
            @Size(max = 16) String flatNumber,
            @Size(max = 500) String notes
    ) {}

    public record UsePassRequest(@NotBlank(message = "Enter the 6-digit code")
                                 @Pattern(regexp = "^[0-9]{6}$", message = "The code is 6 digits") String code) {}

    public record PassResponse(UUID id, String code, String wing, String flatNumber, String guestName,
                               String guestContact, VisitorPurpose purpose, LocalDate validFrom,
                               LocalDate validUntil, boolean multiUse, int uses, boolean cancelled,
                               String state, String notes, String createdBy, Instant createdDate) {
        static PassResponse from(GatePass p, String state) {
            return new PassResponse(p.getId(), p.getCode(), p.getWing(), p.getFlatNumber(), p.getGuestName(),
                    p.getGuestContact(), p.getPurpose(), p.getValidFrom(), p.getValidUntil(), p.isMultiUse(),
                    p.getUses(), p.isCancelled(), state, p.getNotes(), p.getCreatedBy(), p.getCreatedDate());
        }
    }

    // ---- Daily help -----------------------------------------------------

    /** A resident adds help to their own flat; the committee lists the flats as "A-101". */
    public record HelpRequest(
            @NotBlank(message = "Name is required") @Size(max = 160) String name,
            @Pattern(regexp = MOBILE, message = MOBILE_MSG) String mobile,
            @NotBlank(message = "Say what they do")
            @Pattern(regexp = "^(maid|cook|driver|nanny|cleaner|gardener|car_wash|newspaper|milk|laundry|other)$",
                    message = "Unknown kind of help") String helpType,
            List<@Size(max = 24) String> flats,
            @Size(max = 500) String notes
    ) {}

    public record CheckInRequest(@NotBlank(message = "Enter the 6-digit code")
                                 @Pattern(regexp = "^[0-9]{6}$", message = "The code is 6 digits") String code) {}

    public record HelpResponse(UUID id, String name, String mobile, String helpType, String code,
                               List<String> flats, boolean inside, String notes, String addedBy) {}

    public record AttendanceResponse(UUID id, Instant entryTime, Instant exitTime, String loggedBy) {
        static AttendanceResponse from(DailyHelpAttendance a) {
            return new AttendanceResponse(a.getId(), a.getEntryTime(), a.getExitTime(), a.getLoggedBy());
        }
    }
}
