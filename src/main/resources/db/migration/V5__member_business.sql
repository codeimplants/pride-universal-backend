-- Businesses run by members: home tiffin, tuition classes, a boutique, a CA
-- practice. Members used to advertise them on the society WhatsApp group,
-- where a post scrolls away in a day and every post pings every phone. Here a
-- listing is written once, found by category, checked by the committee before
-- it shows, and contacted directly by call or WhatsApp.
--
-- The contact numbers are the business's, published by its owner on purpose;
-- they are not the flat register's private numbers.

CREATE TABLE member_business (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Who manages the listing. Deleting the account deletes its listings.
    owner_user_id  UUID          NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    flat_id        UUID          REFERENCES flat(id) ON DELETE SET NULL,
    wing           VARCHAR(8),
    flat_number    VARCHAR(16),
    owner_name     VARCHAR(160)  NOT NULL,

    name           VARCHAR(160)  NOT NULL,
    category       VARCHAR(32)   NOT NULL,
    description    TEXT,
    -- A deal for society members, shown first while it lasts.
    offer          VARCHAR(255),
    offer_until    DATE,
    contact        VARCHAR(10)   NOT NULL,
    whatsapp       VARCHAR(10),
    link           VARCHAR(255),
    hours          VARCHAR(120),
    delivers       BOOLEAN       NOT NULL DEFAULT FALSE,

    status         VARCHAR(16)   NOT NULL DEFAULT 'pending',
    reviewed_by    VARCHAR(160),
    review_note    VARCHAR(255),

    created_date   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_date   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version        BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_biz_status   CHECK (status IN ('pending','approved','rejected','hidden')),
    CONSTRAINT ck_biz_category CHECK (category IN
        ('home_food','bakery_sweets','grocery_dairy','clothing_fashion','beauty_wellness',
         'health_medical','tuition_classes','fitness_yoga','home_services','electronics',
         'real_estate','finance_insurance','legal','travel','events_decor',
         'handicrafts_gifts','it_digital','other')),
    CONSTRAINT ck_biz_contact  CHECK (contact ~ '^[6-9][0-9]{9}$'),
    CONSTRAINT ck_biz_whatsapp CHECK (whatsapp IS NULL OR whatsapp ~ '^[6-9][0-9]{9}$')
);

CREATE INDEX idx_biz_status ON member_business (status);
CREATE INDEX idx_biz_owner  ON member_business (owner_user_id);
