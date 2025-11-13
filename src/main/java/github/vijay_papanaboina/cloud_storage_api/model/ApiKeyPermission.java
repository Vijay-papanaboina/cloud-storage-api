package github.vijay_papanaboina.cloud_storage_api.model;

/**
 * Enum representing the permission level for API keys.
 * Controls what operations an API key can perform.
 */
public enum ApiKeyPermission {
    /**
     * Read-only access. Can only perform GET operations.
     */
    READ_ONLY,

    /**
     * Read and write access. Can perform GET, POST, and PUT operations, but not
     * DELETE.
     */
    READ_WRITE,

    /**
     * Full access. Can perform all operations including DELETE and API key
     * management.
     */
    FULL_ACCESS;

    /**
     * Check if this permission allows read operations.
     *
     * @return true if read operations are allowed
     */
    public boolean canRead() {
        return this == READ_ONLY || this == READ_WRITE || this == FULL_ACCESS;
    }

    /**
     * Check if this permission allows write operations (POST, PUT).
     *
     * @return true if write operations are allowed
     */
    public boolean canWrite() {
        return this == READ_WRITE || this == FULL_ACCESS;
    }

    /**
     * Check if this permission allows delete operations.
     *
     * @return true if delete operations are allowed
     */
    public boolean canDelete() {
        return this == FULL_ACCESS;
    }

    /**
     * Check if this permission allows API key management operations.
     *
     * @return true if API key management is allowed
     */
    public boolean canManageApiKeys() {
        return this == FULL_ACCESS;
    }
}
