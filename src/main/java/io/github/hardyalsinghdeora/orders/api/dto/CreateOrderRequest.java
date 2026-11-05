package io.github.hardyalsinghdeora.orders.api.dto;

import java.util.List;

import io.github.hardyalsinghdeora.orders.application.CreateOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The currency and total are not accepted from the client: both are fixed by the service. */
public record CreateOrderRequest(
        @NotBlank @Size(max = 64) String customerId,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid OrderLineRequest> lines) {

    public CreateOrderCommand toCommand() {
        return new CreateOrderCommand(customerId, lines.stream().map(OrderLineRequest::toDomain).toList());
    }
}
