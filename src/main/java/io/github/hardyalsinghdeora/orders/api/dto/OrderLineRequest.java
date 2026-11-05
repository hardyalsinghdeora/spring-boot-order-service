package io.github.hardyalsinghdeora.orders.api.dto;

import java.math.BigDecimal;

import io.github.hardyalsinghdeora.orders.domain.OrderLine;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderLineRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 255) String description,
        @NotNull @Min(1) @Max(1_000_000) Integer quantity,
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice) {

    public OrderLine toDomain() {
        return new OrderLine(sku, description, quantity, unitPrice);
    }
}
