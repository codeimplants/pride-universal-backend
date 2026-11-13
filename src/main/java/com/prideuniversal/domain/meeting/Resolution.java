package com.prideuniversal.domain.meeting;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** A resolution put to a meeting. Deleted with its meeting (ON DELETE CASCADE). */
@Entity
@Table(name = "resolution")
@Getter
@Setter
public class Resolution extends BaseEntity {

    @Column(name = "meeting_id", nullable = false, updatable = false)
    private UUID meetingId;

    @Column(name = "resolution_number", nullable = false, length = 32, updatable = false)
    private String resolutionNumber;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 16)
    private ResolutionStatus status = ResolutionStatus.PASSED;

    @Column(name = "votes_for", nullable = false)
    private int votesFor;

    @Column(name = "votes_against", nullable = false)
    private int votesAgainst;

    @Column(name = "votes_abstained", nullable = false)
    private int votesAbstained;
}
