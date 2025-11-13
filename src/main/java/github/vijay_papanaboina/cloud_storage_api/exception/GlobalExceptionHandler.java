package github.vijay_papanaboina.cloud_storage_api.exception;

import github.vijay_papanaboina.cloud_storage_api.dto.ErrorResponse;
import github.vijay_papanaboina.cloud_storage_api.dto.ValidationErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ControllerAdvice(basePackages = "github.vijay_papanaboina.cloud_storage_api.controller")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CloudFileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFileNotFound(
            CloudFileNotFoundException ex, HttpServletRequest request) {
        log.warn("File not found: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.NOT_FOUND.value());
        error.setError("Not Found");
        error.setMessage(ex.getMessage());
        error.setPath(request.getRequestURI());
        error.setErrorCode(ex.getErrorCode() != null ? ex.getErrorCode() : "FILE_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            ForbiddenException ex, HttpServletRequest request) {
        log.warn("Forbidden access attempt: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.FORBIDDEN.value());
        error.setError("Forbidden");
        error.setMessage(ex.getMessage());
        error.setPath(request.getRequestURI());
        error.setErrorCode(ex.getErrorCode());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {
        log.warn("Unauthorized access attempt: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .errorCode(ex.getErrorCode())
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.FORBIDDEN.value());
        error.setError("Forbidden");
        error.setMessage(ex.getMessage());
        error.setPath(request.getRequestURI());
        error.setErrorCode("ACCESS_DENIED");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationException(
            AuthorizationException ex, HttpServletRequest request) {
        log.warn("Authorization failed: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.FORBIDDEN.value());
        error.setError("Forbidden");
        error.setMessage(ex.getMessage());
        error.setPath(request.getRequestURI());
        error.setErrorCode(ex.getErrorCode() != null ? ex.getErrorCode() : "AUTHORIZATION_FAILED");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException ex, HttpServletRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.BAD_REQUEST.value());
        error.setError("Bad Request");
        error.setMessage(ex.getMessage());
        error.setPath(request.getRequestURI());
        error.setErrorCode(ex.getErrorCode() != null ? ex.getErrorCode() : "BAD_REQUEST");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.NOT_FOUND.value());
        error.setError("Not Found");
        error.setMessage(ex.getMessage());
        error.setPath(request.getRequestURI());
        error.setErrorCode(ex.getErrorCode() != null ? ex.getErrorCode() : "NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.NOT_FOUND.value());
        error.setError("Not Found");
        error.setMessage(ex.getMessage());
        error.setPath(request.getRequestURI());
        error.setErrorCode(ex.getErrorCode() != null ? ex.getErrorCode() : "RESOURCE_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.CONFLICT.value());
        error.setError("Conflict");
        error.setMessage(ex.getMessage());
        error.setPath(request.getRequestURI());
        error.setErrorCode(ex.getErrorCode() != null ? ex.getErrorCode() : "CONFLICT");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorageException(
            StorageException ex, HttpServletRequest request) {
        log.error("Storage error: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        error.setError("Service Unavailable");
        error.setMessage("Storage service error occurred");
        error.setPath(request.getRequestURI());
        error.setErrorCode(ex.getErrorCode() != null ? ex.getErrorCode() : "STORAGE_ERROR");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(TokenRotationException.class)
    public ResponseEntity<ErrorResponse> handleTokenRotationException(
            TokenRotationException ex, HttpServletRequest request) {
        log.error("Token rotation error: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.setError("Internal Server Error");
        error.setMessage(ex.getMessage());
        error.setMessage("Token rotation error occurred");
        error.setPath(request.getRequestURI());
        error.setErrorCode(ex.getErrorCode() != null ? ex.getErrorCode() : "TOKEN_ROTATION_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());
        List<ValidationErrorResponse> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            ValidationErrorResponse validationError = new ValidationErrorResponse();
            validationError.setField(fieldError.getField());
            validationError.setMessage(fieldError.getDefaultMessage());
            details.add(validationError);
        }
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.BAD_REQUEST.value());
        error.setError("Bad Request");
        error.setMessage("Validation failed");
        error.setPath(request.getRequestURI());
        error.setErrorCode("VALIDATION_ERROR");
        error.setDetails(details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid argument: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.BAD_REQUEST.value());
        error.setError("Bad Request");
        error.setMessage(ex.getMessage());
        error.setPath(request.getRequestURI());
        error.setErrorCode("INVALID_ARGUMENT");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());
        List<ValidationErrorResponse> details = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            ValidationErrorResponse validationError = new ValidationErrorResponse();
            String propertyPath = violation.getPropertyPath().toString();
            validationError.setField(propertyPath.substring(propertyPath.lastIndexOf('.') + 1));
            validationError.setMessage(violation.getMessage());
            details.add(validationError);
        }
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.BAD_REQUEST.value());
        error.setError("Bad Request");
        error.setMessage("Validation failed");
        error.setPath(request.getRequestURI());
        error.setErrorCode("VALIDATION_ERROR");
        error.setDetails(details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing request parameter: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.BAD_REQUEST.value());
        error.setError("Bad Request");
        error.setMessage("Required parameter '" + ex.getParameterName() + "' is missing");
        error.setPath(request.getRequestURI());
        error.setErrorCode("MISSING_PARAMETER");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex, HttpServletRequest request) {
        // Re-throw ResponseStatusException to let Spring handle it properly
        // This ensures the correct HTTP status code is returned
        throw ex;
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, HttpServletRequest request) {
        // Skip ResponseStatusException - it should be handled by Spring
        if (ex instanceof ResponseStatusException) {
            throw (ResponseStatusException) ex;
        }
        log.error("Runtime exception occurred", ex);
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.setError("Internal Server Error");
        error.setMessage("An unexpected error occurred");
        error.setPath(request.getRequestURI());
        error.setErrorCode("INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse();
        error.setTimestamp(Instant.now());
        error.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.setError("Internal Server Error");
        error.setMessage("An unexpected error occurred");
        error.setPath(request.getRequestURI());
        error.setErrorCode("INTERNAL_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
