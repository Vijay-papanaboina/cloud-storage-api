-- Create batch_jobs table
CREATE TABLE batch_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    user_id UUID NOT NULL,
    job_type VARCHAR(50) NOT NULL CHECK (
        job_type IN (
            'UPLOAD',
            'DELETE',
            'TRANSFORM'
        )
    ),
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED' CHECK (
        status IN (
            'QUEUED',
            'PROCESSING',
            'COMPLETED',
            'FAILED',
            'CANCELLED'
        )
    ),
    total_items INTEGER NOT NULL CHECK (total_items > 0),
    processed_items INTEGER NOT NULL DEFAULT 0,
    failed_items INTEGER NOT NULL DEFAULT 0,
    progress INTEGER DEFAULT 0 CHECK (
        progress >= 0
        AND progress <= 100
    ),
    error_message TEXT,
    metadata_json JSONB,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_processed_items CHECK (
        processed_items <= total_items
    ),
    CONSTRAINT chk_processed_and_failed_items CHECK (
        processed_items + failed_items <= total_items
    ),    CONSTRAINT fk_batch_job_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_batch_job_user_id ON batch_jobs (user_id);

CREATE INDEX idx_batch_job_type ON batch_jobs (job_type);

CREATE INDEX idx_batch_job_status ON batch_jobs (status);

CREATE INDEX idx_batch_job_created_at ON batch_jobs (created_at);