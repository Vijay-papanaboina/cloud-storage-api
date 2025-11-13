-- Add permissions column to api_keys table
ALTER TABLE api_keys
ADD COLUMN permissions VARCHAR(20) NOT NULL DEFAULT 'FULL_ACCESS';

-- Add CHECK constraint to ensure valid permission values
ALTER TABLE api_keys
ADD CONSTRAINT chk_api_keys_permissions 
CHECK (permissions IN ('READ_ONLY', 'READ_WRITE', 'FULL_ACCESS'));

-- Update existing records to have FULL_ACCESS permission (already done by DEFAULT, but explicit for clarity)
UPDATE api_keys SET permissions = 'FULL_ACCESS' WHERE permissions IS NULL;

