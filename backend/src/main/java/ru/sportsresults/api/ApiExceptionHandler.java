package ru.sportsresults.api;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.sportsresults.api.dto.ApiErrorResponse;
import ru.sportsresults.service.InvalidRequestException;
import ru.sportsresults.service.RequestConflictException;
import ru.sportsresults.service.ResourceNotFoundException;
import ru.sportsresults.service.ResultIssueShareStorageUnavailableException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(ResourceNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getCode(), exception.getMessage(), List.of());
    }

    @ExceptionHandler(RequestConflictException.class)
    ResponseEntity<ApiErrorResponse> conflict(RequestConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage(), List.of());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ApiErrorResponse> invalid(InvalidRequestException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getCode(), exception.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        List<ApiErrorResponse.FieldViolation> violations = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ApiErrorResponse.FieldViolation(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage()
                ))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> constraintViolation(ConstraintViolationException exception) {
        List<ApiErrorResponse.FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(violation -> new ApiErrorResponse.FieldViolation(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", violations);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    ResponseEntity<ApiErrorResponse> malformed(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request could not be parsed", List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrorResponse> uploadTooLarge(MaxUploadSizeExceededException exception) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD_TOO_LARGE", "Uploaded file is too large", List.of());
    }

    @ExceptionHandler(ResultIssueShareStorageUnavailableException.class)
    ResponseEntity<ApiErrorResponse> shareStorageUnavailable(ResultIssueShareStorageUnavailableException exception) {
        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SHARE_STORAGE_UNAVAILABLE",
                "Shared attachment storage is temporarily unavailable",
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
        LOGGER.error("Unhandled API error", exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                List.of()
        );
    }

    private static ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorResponse.FieldViolation> violations
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                violations
        ));
    }
}
