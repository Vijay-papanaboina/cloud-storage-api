-- Create files table
CREATE TABLE files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    user_id UUID NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    folder_path VARCHAR(500),
    cloudinary_public_id VARCHAR(500) NOT NULL UNIQUE,
    cloudinary_url VARCHAR(1000) NOT NULL,
    cloudinary_secure_url VARCHAR(1000) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        folder_path IS NULL
        OR folder_path LIKE '/%'
    ),
    CONSTRAINT fk_files_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_files_user_id ON files (user_id);

CREATE INDEX idx_files_deleted ON files (deleted);

CREATE INDEX idx_files_content_type ON files (content_type);

CREATE INDEX idx_files_folder_path ON files (folder_path);

CREATE UNIQUE INDEX idx_files_cloudinary_public_id ON files (cloudinary_public_id);