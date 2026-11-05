package io.github.hardyalsinghdeora.orders.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.hardyalsinghdeora.orders.api.dto.CreateOrderRequest;
import io.github.hardyalsinghdeora.orders.api.dto.OrderResponse;
import io.github.hardyalsinghdeora.orders.api.dto.PageResponse;
import io.github.hardyalsinghdeora.orders.api.dto.ReplaceLinesRequest;
import io.github.hardyalsinghdeora.orders.application.CreateOrderResult;
import io.github.hardyalsinghdeora.orders.application.OrderSearch;
import io.github.hardyalsinghdeora.orders.application.OrderService;
import io.github.hardyalsinghdeora.orders.domain.Order;
import io.github.hardyalsinghdeora.orders.domain.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Create orders idempotently, move them through their lifecycle, edit draft lines.")
public class OrderController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED_HEADER = "Idempotent-Replayed";

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an order",
            description = "Requires an Idempotency-Key. Repeating a request with the same key and payload returns "
                    + "the original order with 200 and Idempotent-Replayed: true; the same key with a different "
                    + "payload is rejected with 422.")
    public ResponseEntity<OrderResponse> create(@RequestHeader(IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
                                                @Valid @RequestBody CreateOrderRequest request) {
        requireUsable(idempotencyKey);
        CreateOrderResult result = orders.create(idempotencyKey, request.toCommand());
        Order order = result.order();
        if (result.replayed()) {
            return ResponseEntity.ok()
                    .eTag(EntityTags.of(order.version()))
                    .header(IDEMPOTENT_REPLAYED_HEADER, "true")
                    .body(OrderResponse.from(order));
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(order.id()).toUri();
        return ResponseEntity.created(location).eTag(EntityTags.of(order.version())).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order", description = "The ETag header carries the version to send back in If-Match.")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID id) {
        return ok(orders.get(id));
    }

    @GetMapping
    @Operation(summary = "List orders", description = "Newest first; filter by customerId and/or status.")
    public PageResponse<OrderResponse> search(@RequestParam(required = false) String customerId,
                                              @RequestParam(required = false) OrderStatus status,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(orders.search(new OrderSearch(customerId, status, page, size)).map(OrderResponse::from));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm a draft order")
    public ResponseEntity<OrderResponse> confirm(@PathVariable UUID id) {
        return ok(orders.confirm(id));
    }

    @PostMapping("/{id}/fulfil")
    @Operation(summary = "Fulfil a confirmed order")
    public ResponseEntity<OrderResponse> fulfil(@PathVariable UUID id) {
        return ok(orders.fulfil(id));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a draft or confirmed order")
    public ResponseEntity<OrderResponse> cancel(@PathVariable UUID id) {
        return ok(orders.cancel(id));
    }

    @PutMapping(path = "/{id}/lines", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace the lines of a draft order",
            description = "Send the version from the last ETag in If-Match. A stale version is rejected with 412; "
                    + "a write that loses a concurrent race is rejected with 409.")
    public ResponseEntity<OrderResponse> replaceLines(@PathVariable UUID id,
                                                      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                                      @Valid @RequestBody ReplaceLinesRequest request) {
        if (ifMatch == null) {
            throw Problems.exception(HttpStatus.PRECONDITION_REQUIRED, "precondition-required",
                    "If-Match with the current order version is required, for example If-Match: \"0\"");
        }
        return ok(orders.replaceLines(id, EntityTags.parseVersion(ifMatch), request.toLines()));
    }

    private static ResponseEntity<OrderResponse> ok(Order order) {
        return ResponseEntity.ok().eTag(EntityTags.of(order.version())).body(OrderResponse.from(order));
    }

    private static void requireUsable(String idempotencyKey) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw Problems.exception(HttpStatus.BAD_REQUEST, "invalid-idempotency-key",
                    "Idempotency-Key must be between 1 and " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
    }
}
