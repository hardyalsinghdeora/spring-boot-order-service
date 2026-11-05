package io.github.hardyalsinghdeora.orders;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    /**
     * Injected wherever "now" is needed so tests can control time. Millisecond precision
     * survives the round trip through the database unchanged, so a timestamp reads the same
     * in the creation response and in every later GET.
     */
    @Bean
    Clock clock() {
        return Clock.tickMillis(ZoneOffset.UTC);
    }
}
