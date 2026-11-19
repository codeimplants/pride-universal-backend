-- Gate, phase 1: what makes the gate work without phoning the flat.
--
--  * gate_pass: a resident lets a guest in ahead of time. The guest shows a
--    6-digit code at the gate; the guard types it and the visit is logged as
--    already approved. A pass is for one day or a range of days, and is used
--    once or any number of times within it (a frequent visitor).
--  * daily_help: maids, cooks, drivers... who come every day to one or more
--    flats. Each has a gate code; the guard marks them in and out, which is
--    their attendance, and the flats they serve are told they have arrived.
--  * Visitor alerts: the flat is notified when someone waits at the gate, and
--    the gate when the flat answers. They are kept as kind 'gate'.

CREATE TABLE gate_pass (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(6)   NOT NULL UNIQUE,
    flat_id         UUID         NOT NULL REFERENCES flat(id) ON DELETE CASCADE,
    wing            VARCHAR(8)   NOT NULL,
    flat_number     VARCHAR(16)  NOT NULL,
    guest_name      VARCHAR(160) NOT NULL,
    guest_contact   VARCHAR(10),
    purpose         VARCHAR(24)  NOT NULL,
    valid_from      DATE         NOT NULL,
    valid_until     DATE         NOT NULL,
    multi_use       BOOLEAN      NOT NULL DEFAULT false,
    uses            INT          NOT NULL DEFAULT 0,
    cancelled       BOOLEAN      NOT NULL DEFAULT false,
    notes           VARCHAR(500),
    created_by      VARCHAR(160),
    created_by_id   UUID         REFERENCES app_user(id) ON DELETE SET NULL,

    created_date    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_pass_code    CHECK (code ~ '^[0-9]{6}$'),
    CONSTRAINT ck_pass_contact CHECK (guest_contact IS NULL OR guest_contact ~ '^[6-9][0-9]{9}$'),
    CONSTRAINT ck_pass_purpose CHECK (purpose IN
        ('personal','delivery','service','maid','guest','cab','other')),
    CONSTRAINT ck_pass_dates   CHECK (valid_until >= valid_from)
);

CREATE INDEX idx_pass_flat ON gate_pass (flat_id, valid_until DESC);

ALTER TABLE visitor ADD COLUMN pass_id UUID REFERENCES gate_pass(id) ON DELETE SET NULL;

CREATE TABLE daily_help (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(160) NOT NULL,
    mobile        VARCHAR(10),
    help_type     VARCHAR(24)  NOT NULL,
    code          VARCHAR(6)   NOT NULL UNIQUE,
    active        BOOLEAN      NOT NULL DEFAULT true,
    notes         VARCHAR(500),
    added_by      VARCHAR(160),

    created_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_help_code   CHECK (code ~ '^[0-9]{6}$'),
    CONSTRAINT ck_help_mobile CHECK (mobile IS NULL OR mobile ~ '^[6-9][0-9]{9}$'),
    CONSTRAINT ck_help_type   CHECK (help_type IN
        ('maid','cook','driver','nanny','cleaner','gardener','car_wash','newspaper','milk','laundry','other'))
);

CREATE TABLE daily_help_flat (
    daily_help_id UUID NOT NULL REFERENCES daily_help(id) ON DELETE CASCADE,
    flat_id       UUID NOT NULL REFERENCES flat(id) ON DELETE CASCADE,
    PRIMARY KEY (daily_help_id, flat_id)
);

CREATE INDEX idx_help_flat ON daily_help_flat (flat_id);

CREATE TABLE daily_help_attendance (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    daily_help_id  UUID         NOT NULL REFERENCES daily_help(id) ON DELETE CASCADE,
    entry_time     TIMESTAMPTZ  NOT NULL,
    exit_time      TIMESTAMPTZ,
    logged_by      VARCHAR(160),

    created_date   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_att_exit CHECK (exit_time IS NULL OR exit_time >= entry_time)
);

CREATE INDEX idx_att_help ON daily_help_attendance (daily_help_id, entry_time DESC);
-- At most one open visit per person: they can't be "in" twice.
CREATE UNIQUE INDEX uq_att_open ON daily_help_attendance (daily_help_id) WHERE exit_time IS NULL;

ALTER TABLE notification DROP CONSTRAINT ck_notif_kind;
ALTER TABLE notification ADD CONSTRAINT ck_notif_kind CHECK (kind IN ('message','dues_reminder','gate'));
