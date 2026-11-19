package com.prideuniversal.domain.gate;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One day's visit of a daily help: in at the gate, and out. */
@Entity
@Table(name = "daily_help_attendance")
@Getter
@Setter
public class DailyHelpAttendance extends BaseEntity {

    @Column(name = "daily_help_id", nullable = false)
    private UUID dailyHelpId;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "exit_time")
    private Instant exitTime;

    @Column(name = "logged_by", length = 160)
    private String loggedBy;
}
