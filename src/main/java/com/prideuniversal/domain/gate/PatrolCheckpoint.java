package com.prideuniversal.domain.gate;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A point on the guards' round, with a code posted there. */
@Entity
@Table(name = "patrol_checkpoint")
@Getter
@Setter
public class PatrolCheckpoint extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 6, unique = true)
    private String code;

    @Column(nullable = false)
    private boolean active = true;
}
