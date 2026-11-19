-- The store reviewer's account (Google Play / App Store "App access") signs in as 1234567890,
-- which is not a real Indian mobile. Allow exactly that number besides real ones; the API lets it
-- sign in only while REVIEWER_CODE is configured, and never sends it an SMS.
ALTER TABLE app_user DROP CONSTRAINT ck_user_mobile;
ALTER TABLE app_user ADD CONSTRAINT ck_user_mobile
    CHECK (mobile ~ '^[6-9][0-9]{9}$' OR mobile = '1234567890');

-- Its OTP requests, and the listings it posts (whose contact defaults to the poster's number).
ALTER TABLE otp_request DROP CONSTRAINT ck_otp_mobile;
ALTER TABLE otp_request ADD CONSTRAINT ck_otp_mobile
    CHECK (mobile ~ '^[6-9][0-9]{9}$' OR mobile = '1234567890');
ALTER TABLE marketplace_listing DROP CONSTRAINT ck_mkt_contact;
ALTER TABLE marketplace_listing ADD CONSTRAINT ck_mkt_contact
    CHECK (contact_number ~ '^[6-9][0-9]{9}$' OR contact_number = '1234567890');
