package io.github.hardyalsinghdeora.orders.api;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Order Service API",
        version = "0.1.0",
        description = "Reference implementation of a small order-to-cash slice: idempotent creation, "
                + "optimistic locking with If-Match, an explicit state machine and RFC 9457 errors.",
        contact = @Contact(name = "Hardyal Singh Deora", url = "https://hardyalsinghdeora.pages.dev"),
        license = @License(name = "MIT")))
class OpenApiConfiguration {
}
