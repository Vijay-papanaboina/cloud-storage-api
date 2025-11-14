-- Make expires_at required for all API keys
-- First, set expiration for any existing keys that don't have one (default to 90 days from creation)
UPDATE api_keys 
SET expires_at = created_at + INTERVAL '90 days'
WHERE expires_at IS NULL;

-- Now make the column NOT NULL
ALTER TABLE api_keys 
ALTER COLUMN expires_at SET NOT NULL;

