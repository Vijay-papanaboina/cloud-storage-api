-- Add unique constraint on (user_id, folder_path, filename) for non-deleted files
-- This ensures that no two files can have the same filename in the same folder for the same user
-- Note: COALESCE is used to handle NULL folder_path (root folder) as empty string for comparison
-- The WHERE clause ensures only non-deleted files are considered for uniqueness
CREATE UNIQUE INDEX idx_files_user_folder_filename_unique 
ON files (user_id, COALESCE(folder_path, ''), filename) 
WHERE deleted = false;

