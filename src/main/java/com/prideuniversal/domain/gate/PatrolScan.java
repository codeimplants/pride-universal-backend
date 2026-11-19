package com.prideuniversal.domain.gate;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** A guard reaching a checkpoint on their round. */
@Entity
@Table(name = "patrol_scan")
@Getter
@Setter
public class PatrolScan extends BaseEntity {

    @Column(name = "checkpoint_id", nullable = false)
    private UUID checkpointId;

    @Column(name = "guard_name", length = 160)
    private String guardName;

    @Column(name = "guard_id")
    private UUID guardId;

    @Column(length = 255)
    private String note;
}
