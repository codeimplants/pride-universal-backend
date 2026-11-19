package com.prideuniversal.domain.gate;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Someone who comes to work in the society every day: a maid, cook, driver... for one or more flats. */
@Entity
@Table(name = "daily_help")
@Getter
@Setter
public class DailyHelp extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 10)
    private String mobile;

    @Column(name = "help_type", nullable = false, length = 24)
    private String helpType;

    /** Said or shown at the gate to be marked in. */
    @Column(nullable = false, length = 6, unique = true)
    private String code;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 500)
    private String notes;

    @Column(name = "added_by", length = 160)
    private String addedBy;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "daily_help_flat", joinColumns = @JoinColumn(name = "daily_help_id"))
    @Column(name = "flat_id")
    private Set<UUID> flatIds = new LinkedHashSet<>();
}
