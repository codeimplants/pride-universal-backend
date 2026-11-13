-- Complaint numbers (CMP-1001, ...) come from a sequence. The Base44 app derived
-- them from the clock, which can collide and means nothing to a resident reading
-- one out on the phone.
CREATE SEQUENCE complaint_number_seq START WITH 1001;
