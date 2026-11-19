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

/** A question the committee asks the society; each flat answers once. Options are a JSON array. */
@Entity
@Table(name = "poll")
@Getter
@Setter
public class Poll extends BaseEntity {

    @Column(name = "question", nullable = false, length = 300)
    private String question;

    @Column(name = "options", nullable = false, columnDefinition = "text")
    private String options;

    @Column(name = "closes_on", nullable = false)
    private LocalDate closesOn;

    @Column(name = "closed", nullable = false)
    private boolean closed;

    @Column(name = "created_by", length = 160)
    private String createdBy;
}
