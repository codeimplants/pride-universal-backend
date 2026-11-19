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

/** One flat's answer to a poll. */
@Entity
@Table(name = "poll_vote")
@Getter
@Setter
public class PollVote extends BaseEntity {

    @Column(name = "poll_id", nullable = false)
    private UUID pollId;

    @Column(name = "flat_id", nullable = false)
    private UUID flatId;

    @Column(name = "option_index", nullable = false)
    private int optionIndex;

    @Column(name = "voted_by", length = 160)
    private String votedBy;
}
