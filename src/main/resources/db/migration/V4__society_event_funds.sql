-- Festival and event funds: Ganesh Festival, Kojagiri, Navratri...
--
-- Kept apart from financial_transaction on purpose. That ledger is the
-- society's maintenance books, monthly, per wing, read by the finance roles
-- only. Event money is collected (vargani) and spent per event, by a festival
-- committee, and its accounts are shown to every member, as the committee
-- used to post them on the WhatsApp group.

CREATE TABLE society_event (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(160)  NOT NULL,
    start_date        DATE          NOT NULL,
    end_date          DATE,
    description       TEXT,

    -- Where the opening balance comes from. Either another event's closing
    -- balance (Kojagiri spends what the Ganesh Festival left), so it follows
    -- that event's books as they change, or an amount typed in (last year's
    -- balance brought forward). opening_balance is ignored while
    -- carried_from_id is set.
    carried_from_id   UUID          REFERENCES society_event(id) ON DELETE RESTRICT,
    opening_balance   NUMERIC(12,2) NOT NULL DEFAULT 0,

    is_published      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by        VARCHAR(160),

    created_date      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_date      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version           BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_event_dates    CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_event_not_self CHECK (carried_from_id IS NULL OR carried_from_id <> id)
);

CREATE INDEX idx_event_start ON society_event (start_date DESC);

-- A balance is spent once: it can be carried into one event only.
CREATE UNIQUE INDEX uq_event_carried_from ON society_event (carried_from_id)
    WHERE carried_from_id IS NOT NULL;

CREATE TABLE society_event_entry (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID          NOT NULL REFERENCES society_event(id) ON DELETE CASCADE,
    type            VARCHAR(16)   NOT NULL,
    -- A known head's code ('vargani', 'mahaprasad', 'visarjan'...), which the
    -- app shows in the reader's language, or the committee's own words.
    head            VARCHAR(80)   NOT NULL,
    description     VARCHAR(255),
    amount          NUMERIC(12,2) NOT NULL,
    entry_date      DATE,
    reference       VARCHAR(128),
    recorded_by     VARCHAR(160),
    recorded_by_id  UUID          REFERENCES app_user(id) ON DELETE SET NULL,

    created_date    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_date    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_event_entry_type   CHECK (type IN ('income','expense')),
    CONSTRAINT ck_event_entry_amount CHECK (amount > 0)
);

CREATE INDEX idx_event_entry_event ON society_event_entry (event_id);
