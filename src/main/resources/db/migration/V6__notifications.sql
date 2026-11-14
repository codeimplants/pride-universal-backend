-- Notifications: what the committee sends to members (everyone, a wing, one
-- flat), and what the society sends by itself (maintenance dues reminders).
--
-- Every notification is kept per recipient, so it is in the app's inbox even
-- when the phone never showed it: push was off, the app was not yet set up
-- for it, or the member reads on the web. Push is a doorbell; the inbox is
-- the letter.

-- Phones that may receive push. A token is one app install; it moves to
-- whoever signs in on that phone. Deleting the account deletes its phones.
CREATE TABLE push_device (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID          NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token         VARCHAR(255)  NOT NULL UNIQUE,
    platform      VARCHAR(16)   NOT NULL,
    device_name   VARCHAR(120),
    -- The app language on that phone, so reminders the society writes itself
    -- arrive in Marathi, Hindi or English.
    language      VARCHAR(8)    NOT NULL DEFAULT 'en',
    last_seen_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    created_date  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_date  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version       BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_push_platform CHECK (platform IN ('android','ios')),
    CONSTRAINT ck_push_language CHECK (language IN ('en','mr','hi'))
);

CREATE INDEX idx_push_device_user ON push_device (user_id);

CREATE TABLE notification (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    -- 'message': written by the committee, shown as written.
    -- 'dues_reminder': written by the app from params, in each reader's language.
    kind            VARCHAR(24)   NOT NULL,
    title           VARCHAR(120)  NOT NULL,
    body            VARCHAR(1000) NOT NULL,
    params          TEXT,
    audience        VARCHAR(16)   NOT NULL,
    audience_value  VARCHAR(64),
    -- Where a tap opens the app: 'notices', 'my-maintenance', 'event:<id>'...
    screen          VARCHAR(64),
    sent_by         VARCHAR(160),
    sent_by_id      UUID          REFERENCES app_user(id) ON DELETE SET NULL,
    recipients      INT           NOT NULL DEFAULT 0,
    devices         INT           NOT NULL DEFAULT 0,
    delivered       INT           NOT NULL DEFAULT 0,

    created_date    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_date    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_notif_kind     CHECK (kind IN ('message','dues_reminder')),
    CONSTRAINT ck_notif_audience CHECK (audience IN ('all','wing','flat','committee','security','user'))
);

CREATE INDEX idx_notif_created ON notification (created_date DESC);

CREATE TABLE notification_recipient (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    notification_id  UUID         NOT NULL REFERENCES notification(id) ON DELETE CASCADE,
    user_id          UUID         NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    read_at          TIMESTAMPTZ,

    CONSTRAINT uq_notif_recipient UNIQUE (notification_id, user_id)
);

CREATE INDEX idx_notif_recipient_user ON notification_recipient (user_id);
