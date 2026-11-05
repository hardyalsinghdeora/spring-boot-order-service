package io.github.hardyalsinghdeora.orders.api;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.hardyalsinghdeora.orders.application.IdempotencyKeyInUseException;
import io.github.hardyalsinghdeora.orders.application.IdempotencyKeyPayloadMismatchException;
import io.github.hardyalsinghdeora.orders.application.VersionMismatchException;
import io.github.hardyalsinghdeora.orders.domain.InvalidStateTransitionException;
import io.github.hardyalsinghdeora.orders.domain.OrderNotEditableException;
import io.github.hardyalsinghdeora.orders.domain.OrderNotFoundException;

/**
 * Turns every failure into an RFC 9457 problem. Framework-level failures (binding,
 * validation, unreadable JSON, unsupported media types, ...) are inherited from
 * {@link ResponseEntityExceptionHandler}; domain and application failures are mapped here.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail orderNotFound(OrderNotFoundException e) {
        return Problems.of(HttpStatus.NOT_FOUND, "order-not-found", e.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ProblemDetail invalidStateTransition(InvalidStateTransitionException e) {
        ProblemDetail problem = Problems.of(HttpStatus.CONFLICT, "invalid-state-transition", e.getMessage());
        problem.setProperty("currentStatus", e.currentStatus());
        problem.setProperty("requestedStatus", e.requestedStatus());
        problem.setProperty("allowedTransitions", e.allowedTransitions());
        return problem;
    }

    @ExceptionHandler(OrderNotEditableException.class)
    ProblemDetail orderNotEditable(OrderNotEditableException e) {
        ProblemDetail problem = Problems.of(HttpStatus.CONFLICT, "order-not-editable", e.getMessage());
        problem.setProperty("currentStatus", e.currentStatus());
        return problem;
    }

    @ExceptionHandler(VersionMismatchException.class)
    ProblemDetail versionMismatch(VersionMismatchException e) {
        ProblemDetail problem = Problems.of(HttpStatus.PRECONDITION_FAILED, "version-mismatch", e.getMessage());
        problem.setProperty("expectedVersion", e.expectedVersion());
        problem.setProperty("currentVersion", e.currentVersion());
        return problem;
    }

    /** Raised by Spring (as ObjectOptimisticLockingFailureException) when a @Version check loses a race. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentModification(OptimisticLockingFailureException e) {
        return Problems.of(HttpStatus.CONFLICT, "concurrent-modification",
                "The order was modified by another request; fetch it again and retry");
    }

    @ExceptionHandler(IdempotencyKeyPayloadMismatchException.class)
    ProblemDetail idempotencyKeyPayloadMismatch(IdempotencyKeyPayloadMismatchException e) {
        return Problems.of(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency-key-payload-mismatch", e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyInUseException.class)
    ProblemDetail idempotencyKeyInUse(IdempotencyKeyInUseException e) {
        return Problems.of(HttpStatus.CONFLICT, "idempotency-key-in-use", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("unhandled exception", e);
        return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> fieldError(error.getField(), error))
                .toList();
        return validationProblem(ex, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex, HttpHeaders headers,
                                                                            HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> fieldError(result.getMethodParameter().getParameterName(), error)))
                .toList();
        return validationProblem(ex, errors, headers, request);
    }

    /** The parser's message can name internal classes, so the client gets a fixed detail instead. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "malformed-request", "Request body is not valid JSON for this endpoint");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Adds the request id to the problems the base class builds for framework exceptions. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            Problems.withRequestId(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> validationProblem(Exception ex, List<Map<String, String>> errors, HttpHeaders headers, WebRequest request) {
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "validation-error", "Request validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    private static Map<String, String> fieldError(String field, MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return Map.of("field", field == null ? "" : field, "message", message == null ? "invalid" : message);
    }
}
