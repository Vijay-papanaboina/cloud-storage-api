package github.vijay_papanaboina.cloud_storage_api.exception;

import java.util.Arrays;

public class CloudStorageApiException extends RuntimeException {

    private final String errorCode;
    private final Object[] args;

    public CloudStorageApiException(String message) {
        super(message);
        this.errorCode = null;
        this.args = null;
    }

    public CloudStorageApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.args = null;
    }

    public CloudStorageApiException(String errorCode, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args == null ? null : Arrays.copyOf(args, args.length);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Object[] getArgs() {
        if (args == null) {
            return null;
        }
        return Arrays.copyOf(args, args.length);
    }
}
