ALTER TABLE sample_request
ADD COLUMN IF NOT EXISTS shipper_code VARCHAR(32);
