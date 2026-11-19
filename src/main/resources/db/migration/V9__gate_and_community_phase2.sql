-- Phase 2: the rest of the gate, and the community features around it.

-- Parcels left at the gate: logged by the guard, the flat told, handed over later.
CREATE TABLE parcel (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    flat_id       UUID         NOT NULL REFERENCES flat(id) ON DELETE CASCADE,
    wing          VARCHAR(8)   NOT NULL,
    flat_number   VARCHAR(16)  NOT NULL,
    carrier       VARCHAR(80),
    description   VARCHAR(255),
    received_by   VARCHAR(160),
    collected_at  TIMESTAMPTZ,
    collected_by  VARCHAR(160),
    created_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_parcel_flat ON parcel (flat_id, created_date DESC);

-- People the society won't let in, matched by mobile or vehicle when the gate logs a visitor.
CREATE TABLE visitor_blacklist (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(160) NOT NULL,
    mobile         VARCHAR(10),
    vehicle_number VARCHAR(24),
    reason         VARCHAR(255),
    added_by       VARCHAR(160),
    active         BOOLEAN      NOT NULL DEFAULT true,
    created_date   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_bl_some CHECK (mobile IS NOT NULL OR vehicle_number IS NOT NULL)
);

-- Guard rounds: named points around the society, each with a code posted there.
CREATE TABLE patrol_checkpoint (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    code          VARCHAR(6)   NOT NULL UNIQUE,
    active        BOOLEAN      NOT NULL DEFAULT true,
    created_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0
);
CREATE TABLE patrol_scan (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    checkpoint_id  UUID         NOT NULL REFERENCES patrol_checkpoint(id) ON DELETE CASCADE,
    guard_name     VARCHAR(160),
    guard_id       UUID         REFERENCES app_user(id) ON DELETE SET NULL,
    note           VARCHAR(255),
    created_date   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_scan_time ON patrol_scan (created_date DESC);

-- Emergencies raised by a member: every guard and the committee are alerted.
CREATE TABLE sos_alert (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    raised_by        VARCHAR(160),
    raised_by_id     UUID         REFERENCES app_user(id) ON DELETE SET NULL,
    mobile           VARCHAR(10),
    flat_label       VARCHAR(24),
    kind             VARCHAR(16)  NOT NULL,
    message          VARCHAR(500),
    status           VARCHAR(16)  NOT NULL DEFAULT 'open',
    acknowledged_by  VARCHAR(160),
    acknowledged_at  TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ,
    created_date     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_sos_kind   CHECK (kind IN ('medical','fire','security','lift','other')),
    CONSTRAINT ck_sos_status CHECK (status IN ('open','acknowledged','resolved'))
);
CREATE INDEX idx_sos_time ON sos_alert (created_date DESC);

-- Amenities the society books out by slot: clubhouse, hall, court...
CREATE TABLE amenity (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120)  NOT NULL,
    description   VARCHAR(500),
    open_time     TIME          NOT NULL DEFAULT '06:00',
    close_time    TIME          NOT NULL DEFAULT '22:00',
    max_hours     INT           NOT NULL DEFAULT 4,
    fee           NUMERIC(10,2) NOT NULL DEFAULT 0,
    active        BOOLEAN       NOT NULL DEFAULT true,
    created_date  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_date  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_amenity_hours CHECK (close_time > open_time AND max_hours BETWEEN 1 AND 24)
);
CREATE TABLE amenity_booking (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    amenity_id    UUID         NOT NULL REFERENCES amenity(id) ON DELETE CASCADE,
    flat_id       UUID         REFERENCES flat(id) ON DELETE SET NULL,
    flat_label    VARCHAR(24),
    booked_by     VARCHAR(160),
    booked_by_id  UUID         REFERENCES app_user(id) ON DELETE SET NULL,
    booking_date  DATE         NOT NULL,
    start_time    TIME         NOT NULL,
    end_time      TIME         NOT NULL,
    purpose       VARCHAR(255),
    cancelled     BOOLEAN      NOT NULL DEFAULT false,
    created_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_booking_time CHECK (end_time > start_time)
);
CREATE INDEX idx_booking_day ON amenity_booking (amenity_id, booking_date);

-- Parking slots, each assigned to at most one flat.
CREATE TABLE parking_slot (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    label           VARCHAR(24)  NOT NULL UNIQUE,
    kind            VARCHAR(16)  NOT NULL DEFAULT 'four_wheeler',
    flat_id         UUID         REFERENCES flat(id) ON DELETE SET NULL,
    vehicle_number  VARCHAR(24),
    notes           VARCHAR(255),
    created_date    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_slot_kind CHECK (kind IN ('two_wheeler','four_wheeler','visitor'))
);

-- Polls: the committee asks, each flat answers once.
CREATE TABLE poll (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    question      VARCHAR(300)  NOT NULL,
    options       TEXT          NOT NULL,
    closes_on     DATE          NOT NULL,
    closed        BOOLEAN       NOT NULL DEFAULT false,
    created_by    VARCHAR(160),
    created_date  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_date  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version       BIGINT        NOT NULL DEFAULT 0
);
CREATE TABLE poll_vote (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    poll_id       UUID         NOT NULL REFERENCES poll(id) ON DELETE CASCADE,
    flat_id       UUID         NOT NULL REFERENCES flat(id) ON DELETE CASCADE,
    option_index  INT          NOT NULL,
    voted_by      VARCHAR(160),
    created_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_poll_flat UNIQUE (poll_id, flat_id)
);

ALTER TABLE notification DROP CONSTRAINT ck_notif_audience;
ALTER TABLE notification ADD CONSTRAINT ck_notif_audience
    CHECK (audience IN ('all','wing','flat','committee','security','user','gate_staff'));
