package io.github.hardyalsinghdeora.orders.api;

import java.net.URI;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Builds RFC 9457 problem details with a documented type and the request id for correlation. */
final class Problems {

    static final String TYPE_BASE = "https://github.com/hardyalsinghdeora/spring-boot-order-service/blob/main/docs/problem-types.md#";
    static final String REQUEST_ID_PROPERTY = "requestId";

    private Problems() {
    }

    static ProblemDetail of(HttpStatus status, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + type));
        return withRequestId(problem);
    }

    static ProblemDetail withRequestId(ProblemDetail problem) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problem.setProperty(REQUEST_ID_PROPERTY, requestId);
        }
        return problem;
    }

    /** For failures detected in the controller itself; Spring renders the problem as-is. */
    static ErrorResponseException exception(HttpStatus status, String type, String detail) {
        return new ErrorResponseException(status, of(status, type, detail), null);
    }
}
