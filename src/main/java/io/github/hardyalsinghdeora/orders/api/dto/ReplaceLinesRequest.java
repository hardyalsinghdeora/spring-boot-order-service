package io.github.hardyalsinghdeora.orders.api.dto;

import java.util.List;

import io.github.hardyalsinghdeora.orders.domain.OrderLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReplaceLinesRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid OrderLineRequest> lines) {

    public List<OrderLine> toLines() {
        return lines.stream().map(OrderLineRequest::toDomain).toList();
    }
}
