package com.prideuniversal.domain.complaint.dto;

import com.prideuniversal.domain.complaint.Complaint;
import com.prideuniversal.domain.complaint.ComplaintCategory;
import com.prideuniversal.domain.complaint.ComplaintPriority;
import com.prideuniversal.domain.complaint.ComplaintStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ComplaintDtos {

    private ComplaintDtos() {}

    /**
     * Raising a complaint. A resident's complaint is always about their own flat:
     * {@code wing}, {@code flatNumber}, {@code raisedBy} and {@code contact} are
     * ignored for them and filled from their account. Committee and gate staff
     * raise on behalf of a flat and must name it.
     */
    public record CreateComplaintRequest(
            @NotNull(message = "Category is required") ComplaintCategory category,
            @NotBlank(message = "Subject is required") @Size(max = 255, message = "Subject is too long") String title,
            @Size(max = 10_000, message = "Description is too long") String description,
            ComplaintPriority priority,
            @Size(max = 8) String wing,
            @Size(max = 16) String flatNumber,
            @Size(max = 160, message = "Name is too long") String raisedBy,
            @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a valid 10-digit Indian mobile number") String contact
    ) {}

    /** The committee's update: status, priority, who is on it, and notes. */
    public record UpdateComplaintRequest(
            @NotNull(message = "Status is required") ComplaintStatus status,
            ComplaintPriority priority,
            @Size(max = 160, message = "Assignee is too long") String assignedTo,
            @Size(max = 10_000, message = "Notes are too long") String adminNotes,
            Long version
    ) {}

    public record ComplaintResponse(
            UUID id,
            String complaintNumber,
            UUID flatId,
            String wing,
            String flatNumber,
            String raisedBy,
            String contact,
            ComplaintCategory category,
            String title,
            String description,
            ComplaintStatus status,
            ComplaintPriority priority,
            String assignedTo,
            String adminNotes,
            LocalDate resolvedDate,
            String photoUrl,
            Instant createdDate,
            Instant updatedDate,
            Long version,
            Instant dueAt,
            boolean overdue
    ) {
        /** Helpdesk deadline by priority: urgent 1 day, high 2, medium 3, low 7. */
        public static Instant dueAt(Complaint c) {
            if (c.getCreatedDate() == null) {
                return null;
            }
            int days = switch (c.getPriority() == null ? ComplaintPriority.MEDIUM : c.getPriority()) {
                case URGENT -> 1;
                case HIGH -> 2;
                case MEDIUM -> 3;
                case LOW -> 7;
            };
            return c.getCreatedDate().plus(java.time.Duration.ofDays(days));
        }

        private static boolean finished(ComplaintStatus s) {
            String code = s == null ? "" : s.getCode();
            return code.equals("resolved") || code.equals("closed") || code.equals("rejected");
        }

        public static ComplaintResponse from(Complaint c) {
            return new ComplaintResponse(c.getId(), c.getComplaintNumber(), c.getFlatId(), c.getWing(),
                    c.getFlatNumber(), c.getRaisedBy(), c.getContact(), c.getCategory(), c.getTitle(),
                    c.getDescription(), c.getStatus(), c.getPriority(), c.getAssignedTo(), c.getAdminNotes(),
                    c.getResolvedDate(), c.getPhotoUrl(), c.getCreatedDate(), c.getUpdatedDate(), c.getVersion(),
                    dueAt(c), c.getResolvedDate() == null && !finished(c.getStatus()) && dueAt(c) != null
                            && Instant.now().isAfter(dueAt(c)));
        }
    }
}
