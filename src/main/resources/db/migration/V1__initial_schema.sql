-- =====================================================================
-- Pride Universal - initial schema
-- Ported from the base44 entity definitions, with the real constraints,
-- foreign keys and indexes the JSON-schema originals never had.
-- =====================================================================

-- gen_random_uuid() is core since PostgreSQL 13.

-- ---------------------------------------------------------------------
-- Reference: wings
-- base44 hardcoded the A/B enum in 12 places. A table means adding
-- Wing C later is an INSERT, not a schema migration + redeploy.
-- ---------------------------------------------------------------------
CREATE TABLE wing (
    code          VARCHAR(8)   PRIMARY KEY,
    display_name  VARCHAR(64)  NOT NULL,
    sort_order    INT          NOT NULL DEFAULT 0
);

INSERT INTO wing (code, display_name, sort_order) VALUES
    ('A', 'Wing A', 1),
    ('B', 'Wing B', 2);

-- ---------------------------------------------------------------------
-- Flats
-- ---------------------------------------------------------------------
CREATE TABLE flat (
    id                        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    wing                      VARCHAR(8)    NOT NULL REFERENCES wing(code),
    flat_number               VARCHAR(16)   NOT NULL,
    floor                     INT,
    area_sqft                 NUMERIC(10,2),

    owner_name                VARCHAR(160)  NOT NULL,
    owner_contact             VARCHAR(10),
    owner_alternate_contact   VARCHAR(10),

    is_tenant                 BOOLEAN       NOT NULL DEFAULT FALSE,
    tenant_name               VARCHAR(160),
    tenant_contact            VARCHAR(10),
    is_staying                BOOLEAN       NOT NULL DEFAULT TRUE,

    two_wheeler               BOOLEAN       NOT NULL DEFAULT FALSE,
    two_wheeler_count         INT           NOT NULL DEFAULT 0,
    two_wheeler_numbers       VARCHAR(255),
    four_wheeler              BOOLEAN       NOT NULL DEFAULT FALSE,
    four_wheeler_count        INT           NOT NULL DEFAULT 0,
    four_wheeler_numbers      VARCHAR(255),
    permanent_parking         BOOLEAN       NOT NULL DEFAULT FALSE,

    share_certificate_number  VARCHAR(64),
    membership_date           DATE,
    is_active                 BOOLEAN       NOT NULL DEFAULT TRUE,
    notes                     TEXT,

    created_date              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_date              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version                   BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uq_flat_wing_number UNIQUE (wing, flat_number),
    CONSTRAINT ck_flat_mobile_owner  CHECK (owner_contact IS NULL OR owner_contact ~ '^[6-9][0-9]{9}$'),
    CONSTRAINT ck_flat_mobile_alt    CHECK (owner_alternate_contact IS NULL OR owner_alternate_contact ~ '^[6-9][0-9]{9}$'),
    CONSTRAINT ck_flat_mobile_tenant CHECK (tenant_contact IS NULL OR tenant_contact ~ '^[6-9][0-9]{9}$'),
    CONSTRAINT ck_flat_counts CHECK (two_wheeler_count >= 0 AND four_wheeler_count >= 0),
    -- If the flat is marked tenanted, we need to know who the tenant is.
    CONSTRAINT ck_flat_tenant_named CHECK (NOT is_tenant OR tenant_name IS NOT NULL)
);

CREATE INDEX idx_flat_wing      ON flat (wing);
CREATE INDEX idx_flat_active    ON flat (is_active);
CREATE INDEX idx_flat_owner_ct  ON flat (owner_contact);
CREATE INDEX idx_flat_tenant_ct ON flat (tenant_contact);

-- ---------------------------------------------------------------------
-- Users
-- "user" is reserved in Postgres, hence app_user.
-- Role lives here and ONLY here. The old app trusted sessionStorage for
-- this, which is why any resident could promote themselves to admin.
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile        VARCHAR(10)  NOT NULL UNIQUE,
    display_name  VARCHAR(160) NOT NULL,
    email         VARCHAR(255),
    role          VARCHAR(32)  NOT NULL DEFAULT 'resident',
    flat_id       UUID         REFERENCES flat(id) ON DELETE SET NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,

    created_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_user_role CHECK (role IN
        ('admin','chairman','secretary','treasurer','committee_member','resident','security')),
    CONSTRAINT ck_user_mobile CHECK (mobile ~ '^[6-9][0-9]{9}$'),
    -- Residents must be tied to a flat; committee roles need not be.
    CONSTRAINT ck_user_resident_flat CHECK (role <> 'resident' OR flat_id IS NOT NULL)
);

CREATE INDEX idx_user_flat ON app_user (flat_id);
CREATE INDEX idx_user_role ON app_user (role);

-- ---------------------------------------------------------------------
-- Auth: OTP requests and refresh tokens
-- ---------------------------------------------------------------------
CREATE TABLE otp_request (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile        VARCHAR(10)  NOT NULL,
    -- BCrypt hash. The OTP itself is never stored in plaintext.
    code_hash     VARCHAR(255) NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    consumed_at   TIMESTAMPTZ,
    attempts      INT          NOT NULL DEFAULT 0,
    request_ip    VARCHAR(45),
    created_date  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_otp_mobile CHECK (mobile ~ '^[6-9][0-9]{9}$')
);

CREATE INDEX idx_otp_mobile_created ON otp_request (mobile, created_date DESC);
CREATE INDEX idx_otp_expires        ON otp_request (expires_at);

CREATE TABLE refresh_token (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash    VARCHAR(255) NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ  NOT NULL,
    revoked_at    TIMESTAMPTZ,
    user_agent    VARCHAR(255),
    created_date  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_user    ON refresh_token (user_id);
CREATE INDEX idx_refresh_expires ON refresh_token (expires_at);

-- ---------------------------------------------------------------------
-- Wing settings
-- ---------------------------------------------------------------------
CREATE TABLE wing_settings (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    wing                  VARCHAR(8)    NOT NULL UNIQUE REFERENCES wing(code),
    monthly_maintenance   NUMERIC(12,2) NOT NULL DEFAULT 0,
    maintenance_due_day   INT           NOT NULL DEFAULT 10,
    late_fee              NUMERIC(12,2) NOT NULL DEFAULT 0,
    sinking_fund_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
    water_charges         NUMERIC(12,2) NOT NULL DEFAULT 0,

    society_name          VARCHAR(255),
    registration_number   VARCHAR(64),
    address               TEXT,
    bank_name             VARCHAR(160),
    bank_account          VARCHAR(32),
    bank_ifsc             VARCHAR(16),
    upi_id                VARCHAR(128),
    secretary_name        VARCHAR(160),
    chairman_name         VARCHAR(160),
    treasurer_name        VARCHAR(160),
    total_flats           INT,

    created_date          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_date          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version               BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_ws_due_day CHECK (maintenance_due_day BETWEEN 1 AND 28),
    CONSTRAINT ck_ws_amounts CHECK (
        monthly_maintenance >= 0 AND late_fee >= 0
        AND sinking_fund_amount >= 0 AND water_charges >= 0)
);

INSERT INTO wing_settings (wing) VALUES ('A'), ('B');

-- ---------------------------------------------------------------------
-- Maintenance bills
-- owner_name is a deliberate snapshot: if a flat changes hands, last
-- year's bills must still show who was billed at the time.
-- ---------------------------------------------------------------------
CREATE TABLE maintenance_bill (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    flat_id            UUID          NOT NULL REFERENCES flat(id) ON DELETE RESTRICT,
    wing               VARCHAR(8)    NOT NULL REFERENCES wing(code),
    flat_number        VARCHAR(16)   NOT NULL,
    owner_name         VARCHAR(160),

    month              INT           NOT NULL,
    year               INT           NOT NULL,
    amount             NUMERIC(12,2) NOT NULL,
    late_fee           NUMERIC(12,2) NOT NULL DEFAULT 0,

    status             VARCHAR(16)   NOT NULL DEFAULT 'pending',
    due_date           DATE,
    paid_date          DATE,
    payment_mode       VARCHAR(24),
    payment_reference  VARCHAR(128),
    receipt_number     VARCHAR(64)   UNIQUE,
    notes              TEXT,

    created_date       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_date       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version            BIGINT        NOT NULL DEFAULT 0,

    -- One bill per flat per billing period. This is what makes the
    -- "generate bills" button safe to press twice.
    CONSTRAINT uq_bill_flat_period UNIQUE (flat_id, year, month),
    CONSTRAINT ck_bill_month  CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_bill_year   CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_bill_amount CHECK (amount >= 0 AND late_fee >= 0),
    CONSTRAINT ck_bill_status CHECK (status IN ('pending','paid','overdue','waived')),
    CONSTRAINT ck_bill_mode   CHECK (payment_mode IS NULL OR payment_mode IN
        ('cash','upi','cheque','bank_transfer','neft')),
    -- A paid bill must record when and how it was paid.
    CONSTRAINT ck_bill_paid_consistent CHECK (
        status <> 'paid' OR (paid_date IS NOT NULL AND payment_mode IS NOT NULL))
);

CREATE INDEX idx_bill_period ON maintenance_bill (year, month);
CREATE INDEX idx_bill_flat   ON maintenance_bill (flat_id);
CREATE INDEX idx_bill_status ON maintenance_bill (status);
CREATE INDEX idx_bill_lookup ON maintenance_bill (wing, year, month, status);

-- ---------------------------------------------------------------------
-- Complaints
-- ---------------------------------------------------------------------
CREATE TABLE complaint (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_number  VARCHAR(32)  NOT NULL UNIQUE,
    flat_id           UUID         REFERENCES flat(id) ON DELETE SET NULL,
    wing              VARCHAR(8)   NOT NULL REFERENCES wing(code),
    flat_number       VARCHAR(16)  NOT NULL,
    raised_by         VARCHAR(160) NOT NULL,
    raised_by_user_id UUID         REFERENCES app_user(id) ON DELETE SET NULL,
    contact           VARCHAR(10),

    category          VARCHAR(32)  NOT NULL,
    title             VARCHAR(255) NOT NULL,
    description       TEXT         NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'open',
    priority          VARCHAR(16)  NOT NULL DEFAULT 'medium',
    assigned_to       VARCHAR(160),
    admin_notes       TEXT,
    resolved_date     DATE,
    photo_url         VARCHAR(512),

    created_date      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_complaint_category CHECK (category IN
        ('plumbing','electrical','common_area','security','lift',
         'parking','noise','water','other')),
    CONSTRAINT ck_complaint_status   CHECK (status IN
        ('open','in_progress','resolved','closed','rejected')),
    CONSTRAINT ck_complaint_priority CHECK (priority IN ('low','medium','high','urgent')),
    CONSTRAINT ck_complaint_resolved CHECK (
        status NOT IN ('resolved','closed') OR resolved_date IS NOT NULL)
);

CREATE INDEX idx_complaint_status  ON complaint (status);
CREATE INDEX idx_complaint_wing    ON complaint (wing);
CREATE INDEX idx_complaint_flat    ON complaint (flat_id);
CREATE INDEX idx_complaint_created ON complaint (created_date DESC);

-- ---------------------------------------------------------------------
-- Notices
-- ---------------------------------------------------------------------
CREATE TABLE notice (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(255) NOT NULL,
    content         TEXT         NOT NULL,
    notice_type     VARCHAR(24)  NOT NULL DEFAULT 'general',
    scope           VARCHAR(16)  NOT NULL DEFAULT 'both',
    published_by    VARCHAR(160),
    published_by_id UUID         REFERENCES app_user(id) ON DELETE SET NULL,
    is_published    BOOLEAN      NOT NULL DEFAULT TRUE,
    pinned          BOOLEAN      NOT NULL DEFAULT FALSE,
    attachment_url  VARCHAR(512),
    meeting_date    DATE,
    meeting_venue   VARCHAR(255),

    created_date    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_notice_type  CHECK (notice_type IN
        ('general','agm','sgm','maintenance','circular','emergency','event')),
    CONSTRAINT ck_notice_scope CHECK (scope IN ('both','wing_a','wing_b'))
);

CREATE INDEX idx_notice_published ON notice (is_published, created_date DESC);
CREATE INDEX idx_notice_pinned    ON notice (pinned DESC, created_date DESC);

-- ---------------------------------------------------------------------
-- Meetings and resolutions
-- ---------------------------------------------------------------------
CREATE TABLE meeting (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_type     VARCHAR(16)  NOT NULL,
    title            VARCHAR(255) NOT NULL,
    scope            VARCHAR(16)  NOT NULL DEFAULT 'both',
    scheduled_date   DATE         NOT NULL,
    scheduled_time   VARCHAR(16),
    venue            VARCHAR(255),
    agenda           TEXT,
    status           VARCHAR(16)  NOT NULL DEFAULT 'scheduled',
    minutes          TEXT,
    minutes_url      VARCHAR(512),
    quorum_achieved  BOOLEAN,
    total_members    INT,
    members_present  INT,
    chairperson      VARCHAR(160),
    secretary        VARCHAR(160),

    created_date     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_meeting_type   CHECK (meeting_type IN ('agm','sgm','committee','emergency')),
    CONSTRAINT ck_meeting_scope  CHECK (scope IN ('both','wing_a','wing_b')),
    CONSTRAINT ck_meeting_status CHECK (status IN ('scheduled','completed','cancelled','postponed')),
    CONSTRAINT ck_meeting_attendance CHECK (
        members_present IS NULL OR total_members IS NULL OR members_present <= total_members)
);

CREATE INDEX idx_meeting_date ON meeting (scheduled_date DESC);

CREATE TABLE resolution (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id         UUID         NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    resolution_number  VARCHAR(32)  NOT NULL,
    title              VARCHAR(255) NOT NULL,
    description        TEXT,
    status             VARCHAR(16)  NOT NULL DEFAULT 'passed',
    votes_for          INT          NOT NULL DEFAULT 0,
    votes_against      INT          NOT NULL DEFAULT 0,
    votes_abstained    INT          NOT NULL DEFAULT 0,

    created_date       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_resolution_number UNIQUE (meeting_id, resolution_number),
    CONSTRAINT ck_resolution_status CHECK (status IN ('passed','rejected','deferred')),
    CONSTRAINT ck_resolution_votes  CHECK (
        votes_for >= 0 AND votes_against >= 0 AND votes_abstained >= 0)
);

CREATE INDEX idx_resolution_meeting ON resolution (meeting_id);

-- ---------------------------------------------------------------------
-- Society ledger
-- "transaction" is reserved in SQL, hence financial_transaction.
-- ---------------------------------------------------------------------
CREATE TABLE financial_transaction (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    wing           VARCHAR(8)    NOT NULL,
    type           VARCHAR(16)   NOT NULL,
    category       VARCHAR(32)   NOT NULL,
    amount         NUMERIC(12,2) NOT NULL,
    description    TEXT,
    txn_date       DATE          NOT NULL,
    reference      VARCHAR(128),
    recorded_by    VARCHAR(160),
    recorded_by_id UUID          REFERENCES app_user(id) ON DELETE SET NULL,
    receipt_url    VARCHAR(512),
    month          INT           NOT NULL,
    year           INT           NOT NULL,

    created_date   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_date   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,

    -- 'common' is valid here but is not a wing, so no FK to wing(code).
    CONSTRAINT ck_txn_wing   CHECK (wing IN ('A','B','common')),
    CONSTRAINT ck_txn_type   CHECK (type IN ('income','expense')),
    CONSTRAINT ck_txn_amount CHECK (amount >= 0),
    CONSTRAINT ck_txn_month  CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_txn_category CHECK (category IN
        ('maintenance_collected','sinking_fund','water_charges','parking_charges',
         'interest','donation','repair_expense','admin_expense','utility_expense',
         'security_expense','cleaning_expense','lift_maintenance','legal_expense',
         'audit_fee','other_income','other_expense'))
);

CREATE INDEX idx_txn_period ON financial_transaction (year, month);
CREATE INDEX idx_txn_wing   ON financial_transaction (wing);
CREATE INDEX idx_txn_date   ON financial_transaction (txn_date DESC);

-- ---------------------------------------------------------------------
-- Visitors
-- ---------------------------------------------------------------------
CREATE TABLE visitor (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    visitor_name        VARCHAR(160) NOT NULL,
    visitor_contact     VARCHAR(10),
    purpose             VARCHAR(24)  NOT NULL,
    flat_id             UUID         REFERENCES flat(id) ON DELETE SET NULL,
    wing                VARCHAR(8)   NOT NULL REFERENCES wing(code),
    flat_number         VARCHAR(16)  NOT NULL,
    resident_name       VARCHAR(160),
    status              VARCHAR(16)  NOT NULL DEFAULT 'pending',
    entry_time          TIMESTAMPTZ,
    exit_time           TIMESTAMPTZ,
    approved_by         VARCHAR(160),
    approved_by_id      UUID         REFERENCES app_user(id) ON DELETE SET NULL,
    rejected_reason     VARCHAR(255),
    photo_url           VARCHAR(512),
    vehicle_number      VARCHAR(24),
    notes               TEXT,
    created_by_security VARCHAR(160),

    created_date        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_visitor_purpose CHECK (purpose IN
        ('personal','delivery','service','maid','guest','cab','other')),
    CONSTRAINT ck_visitor_status  CHECK (status IN
        ('pending','approved','rejected','checked_out')),
    CONSTRAINT ck_visitor_exit    CHECK (
        exit_time IS NULL OR entry_time IS NULL OR exit_time >= entry_time)
);

CREATE INDEX idx_visitor_status  ON visitor (status);
CREATE INDEX idx_visitor_flat    ON visitor (flat_id);
CREATE INDEX idx_visitor_created ON visitor (created_date DESC);

-- ---------------------------------------------------------------------
-- Vendors
-- ---------------------------------------------------------------------
CREATE TABLE vendor (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(160) NOT NULL,
    service_type       VARCHAR(32)  NOT NULL,
    contact            VARCHAR(10)  NOT NULL,
    alternate_contact  VARCHAR(10),
    description        TEXT,
    availability       VARCHAR(255),
    approved           BOOLEAN      NOT NULL DEFAULT FALSE,
    added_by_flat      VARCHAR(16),
    added_by_name      VARCHAR(160),
    added_by_id        UUID         REFERENCES app_user(id) ON DELETE SET NULL,
    rating             NUMERIC(2,1),
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,

    created_date       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_date       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_vendor_service CHECK (service_type IN
        ('plumber','electrician','carpenter','painter','maid','cook','driver',
         'pest_control','ac_repair','appliance_repair','security','gardener','other')),
    CONSTRAINT ck_vendor_rating  CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5)),
    CONSTRAINT ck_vendor_contact CHECK (contact ~ '^[6-9][0-9]{9}$')
);

CREATE INDEX idx_vendor_service  ON vendor (service_type);
CREATE INDEX idx_vendor_approved ON vendor (approved, is_active);

-- ---------------------------------------------------------------------
-- Marketplace
-- ---------------------------------------------------------------------
CREATE TABLE marketplace_listing (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(255)  NOT NULL,
    description     TEXT,
    category        VARCHAR(32)   NOT NULL,
    price           NUMERIC(12,2) NOT NULL DEFAULT 0,
    is_free         BOOLEAN       NOT NULL DEFAULT FALSE,
    listing_type    VARCHAR(16)   NOT NULL DEFAULT 'sell',
    contact_name    VARCHAR(160)  NOT NULL,
    contact_number  VARCHAR(10)   NOT NULL,
    wing            VARCHAR(8)    REFERENCES wing(code),
    flat_number     VARCHAR(16),
    posted_by_id    UUID          REFERENCES app_user(id) ON DELETE SET NULL,
    photo_url       VARCHAR(512),
    status          VARCHAR(16)   NOT NULL DEFAULT 'active',

    created_date    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_date    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_mkt_category CHECK (category IN
        ('furniture','electronics','appliances','books','clothes',
         'vehicles','kids_items','sports','other')),
    CONSTRAINT ck_mkt_type    CHECK (listing_type IN ('sell','buy','give_away')),
    CONSTRAINT ck_mkt_status  CHECK (status IN ('active','sold','removed')),
    CONSTRAINT ck_mkt_price   CHECK (price >= 0),
    CONSTRAINT ck_mkt_contact CHECK (contact_number ~ '^[6-9][0-9]{9}$')
);

CREATE INDEX idx_mkt_status   ON marketplace_listing (status, created_date DESC);
CREATE INDEX idx_mkt_category ON marketplace_listing (category);
