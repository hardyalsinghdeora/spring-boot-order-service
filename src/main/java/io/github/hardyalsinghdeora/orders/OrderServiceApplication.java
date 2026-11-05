package io.github.hardyalsinghdeora.orders;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    /** Injected wherever "now" is needed so tests can control time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
