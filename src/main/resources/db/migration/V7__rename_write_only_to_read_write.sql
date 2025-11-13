-- Migration to rename WRITE_ONLY to READ_WRITE for backward compatibility
-- Update existing WRITE_ONLY records to READ_WRITE
UPDATE api_keys SET permissions = 'READ_WRITE' WHERE permissions = 'WRITE_ONLY';

-- Drop the old CHECK constraint
ALTER TABLE api_keys DROP CONSTRAINT IF EXISTS chk_api_keys_permissions;

-- Add new CHECK constraint with READ_WRITE instead of WRITE_ONLY
ALTER TABLE api_keys
ADD CONSTRAINT chk_api_keys_permissions 
CHECK (permissions IN ('READ_ONLY', 'READ_WRITE', 'FULL_ACCESS'));

