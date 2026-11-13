package com.prideuniversal.domain.complaint;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A helpdesk ticket raised by or for a flat. Wing, flat number and the raiser's
 * name are snapshots: the ticket records who complained, even if the flat changes
 * hands later. {@code flatId} and {@code raisedByUserId} are plain ids for the
 * same reason {@code Notice.publishedById} is.
 */
@Entity
@Table(name = "complaint")
@Getter
@Setter
public class Complaint extends BaseEntity {

    @Column(name = "complaint_number", nullable = false, length = 32, updatable = false)
    private String complaintNumber;

    @Column(name = "flat_id")
    private UUID flatId;

    @Column(nullable = false, length = 8)
    private String wing;

    @Column(name = "flat_number", nullable = false, length = 16)
    private String flatNumber;

    @Column(name = "raised_by", nullable = false, length = 160)
    private String raisedBy;

    @Column(name = "raised_by_user_id")
    private UUID raisedByUserId;

    @Column(length = 10)
    private String contact;

    @Column(nullable = false, length = 32)
    private ComplaintCategory category;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 16)
    private ComplaintStatus status = ComplaintStatus.OPEN;

    @Column(nullable = false, length = 16)
    private ComplaintPriority priority = ComplaintPriority.MEDIUM;

    @Column(name = "assigned_to", length = 160)
    private String assignedTo;

    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    @Column(name = "resolved_date")
    private LocalDate resolvedDate;

    @Column(name = "photo_url", length = 512)
    private String photoUrl;
}
