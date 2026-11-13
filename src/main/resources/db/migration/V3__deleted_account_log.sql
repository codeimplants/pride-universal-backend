-- Accounts that have been deleted, by their owner in the app or from Nexus.
--
-- Nexus keeps engagement telemetry keyed on our user id. When an account is
-- deleted here, Nexus must drop that telemetry too, and it does so only on
-- positive evidence: ids that appear in this log (GET /api/admin/users/deleted).
-- The log holds no personal data — the id, the role it had, who deleted it and
-- when — because deleting the account is the point.
CREATE TABLE deleted_account (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,   -- the deleted app_user.id; no FK, the row is gone
    role        VARCHAR(32)  NOT NULL,
    deleted_by  VARCHAR(16)  NOT NULL,
    deleted_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_deleted_by CHECK (deleted_by IN ('self', 'admin_console'))
);

CREATE INDEX idx_deleted_account_at ON deleted_account (deleted_at DESC);
