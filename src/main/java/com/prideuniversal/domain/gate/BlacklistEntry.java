package com.prideuniversal.domain.gate;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Someone the society won't let in, matched by mobile or vehicle number. */
@Entity
@Table(name = "visitor_blacklist")
@Getter
@Setter
public class BlacklistEntry extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 10)
    private String mobile;

    @Column(name = "vehicle_number", length = 24)
    private String vehicleNumber;

    @Column(length = 255)
    private String reason;

    @Column(name = "added_by", length = 160)
    private String addedBy;

    @Column(nullable = false)
    private boolean active = true;
}
