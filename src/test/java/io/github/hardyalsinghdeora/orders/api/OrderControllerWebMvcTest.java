package io.github.hardyalsinghdeora.orders.api;

import static io.github.hardyalsinghdeora.orders.Fixtures.T0;
import static io.github.hardyalsinghdeora.orders.Fixtures.line;
import static io.github.hardyalsinghdeora.orders.Fixtures.orderIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.hardyalsinghdeora.orders.application.CreateOrderResult;
import io.github.hardyalsinghdeora.orders.application.OrderService;
import io.github.hardyalsinghdeora.orders.domain.InvalidStateTransitionException;
import io.github.hardyalsinghdeora.orders.domain.Order;
import io.github.hardyalsinghdeora.orders.domain.OrderNotFoundException;
import io.github.hardyalsinghdeora.orders.domain.OrderStatus;

@WebMvcTest(OrderController.class)
class OrderControllerWebMvcTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String VALID_ORDER = """
            {"customerId": "cust-1", "lines": [{"sku": "SKU-1", "description": "Widget", "quantity": 2, "unitPrice": 19.99}]}
            """;
    private static final String VALID_LINES = """
            {"lines": [{"sku": "SKU-1", "description": "Widget", "quantity": 2, "unitPrice": 19.99}]}
            """;
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderService orders;

    @Test
    void createWithInvalidFieldsIsAValidationProblemListingEachField() throws Exception {
        String body = """
                {"customerId": " ", "lines": [{"sku": "", "description": "Widget", "quantity": 0, "unitPrice": 1.005}]}
                """;

        mvc.perform(post("/orders").header(OrderController.IDEMPOTENCY_KEY_HEADER, "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(endsWith("#validation-error")))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[?(@.field == 'customerId')].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[?(@.field == 'lines[0].sku')].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[?(@.field == 'lines[0].quantity')].message").value("must be greater than or equal to 1"))
                .andExpect(jsonPath("$.errors[?(@.field == 'lines[0].unitPrice')]").exists());
        verifyNoInteractions(orders);
    }

    @Test
    void createWithoutLinesIsRejected() throws Exception {
        mvc.perform(post("/orders").header(OrderController.IDEMPOTENCY_KEY_HEADER, "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerId\": \"cust-1\", \"lines\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lines"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be empty"));
    }

    @Test
    void createWithoutIdempotencyKeyIsA400Problem() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Required header 'Idempotency-Key' is not present."))
                .andExpect(jsonPath("$.requestId").exists());
        verifyNoInteractions(orders);
    }

    @Test
    void createWithBlankIdempotencyKeyIsA400Problem() throws Exception {
        mvc.perform(post("/orders").header(OrderController.IDEMPOTENCY_KEY_HEADER, "   ")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("#invalid-idempotency-key")));
        verifyNoInteractions(orders);
    }

    @Test
    void malformedJsonIsA400ProblemThatLeaksNoInternals() throws Exception {
        mvc.perform(post("/orders").header(OrderController.IDEMPOTENCY_KEY_HEADER, "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerId\": \"cust-1\", \"lines\": [{\"quantity\": \"two\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(endsWith("#malformed-request")))
                .andExpect(jsonPath("$.detail").value("Request body is not valid JSON for this endpoint"))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void createdOrderIs201WithLocationEtagAndDerivedTotal() throws Exception {
        Order order = Order.create(UUID.randomUUID(), "cust-1", List.of(line("SKU-1", 2, "19.99")), T0);
        when(orders.create(eq("k-1"), any())).thenReturn(new CreateOrderResult(order, false));

        mvc.perform(post("/orders").header(OrderController.IDEMPOTENCY_KEY_HEADER, "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, endsWith("/orders/" + order.id())))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(header().doesNotExist(OrderController.IDEMPOTENT_REPLAYED_HEADER))
                .andExpect(jsonPath("$.id").value(order.id().toString()))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.total").value(39.98))
                .andExpect(jsonPath("$.lines[0].lineTotal").value(39.98));
    }

    @Test
    void replayedCreateIs200WithReplayHeader() throws Exception {
        Order order = orderIn(OrderStatus.CONFIRMED);
        when(orders.create(eq("k-1"), any())).thenReturn(new CreateOrderResult(order, true));

        mvc.perform(post("/orders").header(OrderController.IDEMPOTENCY_KEY_HEADER, "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isOk())
                .andExpect(header().string(OrderController.IDEMPOTENT_REPLAYED_HEADER, "true"))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").value(order.id().toString()));
    }

    @Test
    void requestIdIsEchoedAndVisibleInTheMdcWhileHandling() throws Exception {
        Order order = orderIn(OrderStatus.DRAFT);
        AtomicReference<String> seenInMdc = new AtomicReference<>();
        when(orders.get(order.id())).thenAnswer(invocation -> {
            seenInMdc.set(MDC.get(RequestIdFilter.MDC_KEY));
            return order;
        });

        mvc.perform(get("/orders/{id}", order.id()).header(RequestIdFilter.HEADER, "trace-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-123"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""));
        assertThat(seenInMdc.get()).isEqualTo("trace-123");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).as("cleared after the request").isNull();
    }

    @Test
    void requestIdIsGeneratedWhenMissingOrMalformed() throws Exception {
        Order order = orderIn(OrderStatus.DRAFT);
        when(orders.get(order.id())).thenReturn(order);

        mvc.perform(get("/orders/{id}", order.id()))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
        mvc.perform(get("/orders/{id}", order.id()).header(RequestIdFilter.HEADER, "not safe\u0007"))
                .andExpect(header().string(RequestIdFilter.HEADER, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void unknownOrderIsA404Problem() throws Exception {
        UUID id = UUID.randomUUID();
        when(orders.get(id)).thenThrow(new OrderNotFoundException(id));

        mvc.perform(get("/orders/{id}", id).header(RequestIdFilter.HEADER, "trace-404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(endsWith("#order-not-found")))
                .andExpect(jsonPath("$.detail").value("Order " + id + " not found"))
                .andExpect(jsonPath("$.instance").value("/orders/" + id))
                .andExpect(jsonPath("$.requestId").value("trace-404"));
    }

    @Test
    void invalidTransitionIsA409ProblemNamingCurrentStatusAndAllowedTransitions() throws Exception {
        UUID id = UUID.randomUUID();
        when(orders.fulfil(id)).thenThrow(new InvalidStateTransitionException(id, OrderStatus.DRAFT, OrderStatus.FULFILLED));

        mvc.perform(post("/orders/{id}/fulfil", id))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(endsWith("#invalid-state-transition")))
                .andExpect(jsonPath("$.currentStatus").value("DRAFT"))
                .andExpect(jsonPath("$.requestedStatus").value("FULFILLED"))
                .andExpect(jsonPath("$.allowedTransitions").value(org.hamcrest.Matchers.contains("CONFIRMED", "CANCELLED")))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("allowed transitions: [CONFIRMED, CANCELLED]")));
    }

    @Test
    void replaceLinesWithoutIfMatchIs428() throws Exception {
        mvc.perform(put("/orders/{id}/lines", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(VALID_LINES))
                .andExpect(status().isPreconditionRequired())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(endsWith("#precondition-required")));
        verifyNoInteractions(orders);
    }

    @Test
    void replaceLinesWithMalformedIfMatchIs400() throws Exception {
        mvc.perform(put("/orders/{id}/lines", UUID.randomUUID()).header(HttpHeaders.IF_MATCH, "\"latest\"")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_LINES))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("#malformed-if-match")));
        verifyNoInteractions(orders);
    }

    @Test
    void replaceLinesPassesTheVersionFromIfMatchAndReturnsTheNewEtag() throws Exception {
        Order saved = Order.rehydrate(UUID.randomUUID(), "cust-1", List.of(line("SKU-1", 2, "19.99")), OrderStatus.DRAFT, 8L, T0, T0);
        when(orders.replaceLines(eq(saved.id()), eq(7L), any())).thenReturn(saved);

        mvc.perform(put("/orders/{id}/lines", saved.id()).header(HttpHeaders.IF_MATCH, "\"7\"")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_LINES))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"8\""))
                .andExpect(jsonPath("$.version").value(8));
        verify(orders).replaceLines(saved.id(), 7L, List.of(new io.github.hardyalsinghdeora.orders.domain.OrderLine("SKU-1", "Widget", 2, new java.math.BigDecimal("19.99"))));
    }

    @Test
    void pageSizeOutsideTheLimitIsAValidationProblem() throws Exception {
        mvc.perform(get("/orders").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("#validation-error")))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].message").value("must be less than or equal to 100"));
        verifyNoInteractions(orders);
    }

    @Test
    void unexpectedFailuresAreA500ProblemWithoutInternals() throws Exception {
        when(orders.create(anyString(), any())).thenThrow(new IllegalStateException("connection string contains hunter2"));

        mvc.perform(post("/orders").header(OrderController.IDEMPOTENCY_KEY_HEADER, "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("hunter2"))));
    }
}
