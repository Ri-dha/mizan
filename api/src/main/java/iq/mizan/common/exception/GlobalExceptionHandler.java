package iq.mizan.common.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import iq.mizan.common.web.CorrelationIdFilter;

import jakarta.servlet.http.HttpServletRequest;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Every error leaves as RFC 7807 problem+json with a stable {@code code}. */
@RestControllerAdvice
@AllArgsConstructor
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiProblemProperties problemProperties;

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException e, HttpServletRequest request) {
        ProblemDetail problem = base(e.getErrorCode(), e.getMessage(), request);
        e.getProperties().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        ProblemDetail problem = base(
                ErrorCode.VALIDATION_FAILED, "One or more fields failed validation.", request);
        List<FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String expected = e.getRequiredType() == null
                ? "the expected type" : e.getRequiredType().getSimpleName();
        ProblemDetail problem = base(ErrorCode.VALIDATION_FAILED,
                "'%s' is not a valid %s.".formatted(e.getName(), expected), request);
        problem.setProperty("errors", List.of(new FieldError(
                e.getName(), "Could not read '%s' as %s".formatted(e.getValue(), expected))));
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        return base(ErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleUnauthenticated(AuthenticationException e, HttpServletRequest request) {
        return base(ErrorCode.INVALID_CREDENTIALS, "Authentication is required.", request);
    }

    /** Never echoes the message: an unreviewed exception can carry internal detail. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception [correlationId={}]", CorrelationIdFilter.current(), e);
        return base(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", request);
    }

    private ProblemDetail base(ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setType(URI.create(problemProperties.typeFor(code)));
        problem.setTitle(code.title());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code.code());
        problem.setProperty("correlationId", CorrelationIdFilter.current());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private record FieldError(String field, String message) {
    }
}
