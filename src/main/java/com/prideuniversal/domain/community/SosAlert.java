package com.prideuniversal.domain.community;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** An emergency raised by a member; every guard and the committee are alerted. */
@Entity
@Table(name = "sos_alert")
@Getter
@Setter
public class SosAlert extends BaseEntity {

    @Column(name = "raised_by", length = 160)
    private String raisedBy;

    @Column(name = "raised_by_id")
    private UUID raisedById;

    @Column(name = "mobile", length = 10)
    private String mobile;

    @Column(name = "flat_label", length = 24)
    private String flatLabel;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "acknowledged_by", length = 160)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
