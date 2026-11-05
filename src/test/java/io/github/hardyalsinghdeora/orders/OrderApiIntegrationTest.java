package io.github.hardyalsinghdeora.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.hardyalsinghdeora.orders.application.OrderRepository;
import io.github.hardyalsinghdeora.orders.application.OrderService;
import io.github.hardyalsinghdeora.orders.domain.Order;

/**
 * The whole stack against H2 with the real Flyway migration: HTTP in, JPA and SQL out.
 * Each test uses its own customer id, so tests stay independent within the shared context.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderApiIntegrationTest {

    private static final String ORDER = """
            {"customerId":"%s","lines":[
              {"sku":"SKU-1","description":"Exercise book","quantity":2,"unitPrice":"10.00"},
              {"sku":"SKU-2","description":"Pen","quantity":3,"unitPrice":"4.50"}]}""";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Fixtures.T0);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderRepository repository;
    @Autowired MutableClock clock;
    @Autowired Clock injectedClock;
    @Autowired TransactionTemplate tx;

    private String customer;

    @BeforeEach
    void newCustomer() {
        customer = "cust-" + UUID.randomUUID();
    }

    @Test
    void flywayCreatedTheSchemaAndRecordedTheMigration() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success = true", Integer.class);
        assertThat(applied).isEqualTo(1);
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tables).contains("orders", "order_lines", "idempotency_keys");
        assertThat(injectedClock).isSameAs(clock);
    }

    @Test
    void createConfirmFulfilHappyPath() throws Exception {
        JsonNode created = create("key-" + customer, order(customer), 201);
        String id = created.get("id").asText();
        // The total is derived by the service, never sent by the client: 2 x 10.00 + 3 x 4.50.
        assertThat(created.get("total").decimalValue()).isEqualByComparingTo("33.50");
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");

        mvc.perform(post("/orders/{id}/confirm", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(post("/orders/{id}/fulfil", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"));
        mvc.perform(get("/orders/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""));
    }

    @Test
    void cancellingAFulfilledOrderIsAConflict() throws Exception {
        String id = create("key-" + customer, order(customer), 201).get("id").asText();
        mvc.perform(post("/orders/{id}/confirm", id)).andExpect(status().isOk());
        mvc.perform(post("/orders/{id}/fulfil", id)).andExpect(status().isOk());
        mvc.perform(post("/orders/{id}/cancel", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void repeatingACreateWithTheSameKeyReplaysTheOriginalOrder() throws Exception {
        String key = "key-" + customer;
        String first = create(key, order(customer), 201).get("id").asText();

        MvcResult replay = mvc.perform(post("/orders").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(order(customer)))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn();
        assertThat(json.readTree(replay.getResponse().getContentAsString()).get("id").asText()).isEqualTo(first);
        assertThat(ordersFor(customer)).isEqualTo(1);
    }

    @Test
    void reusingAKeyWithADifferentPayloadIs422() throws Exception {
        String key = "key-" + customer;
        create(key, order(customer), 201);
        mvc.perform(post("/orders").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(order(customer).replace("\"quantity\":3", "\"quantity\":4")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE));
        assertThat(ordersFor(customer)).isEqualTo(1);
    }

    @Test
    void anExpiredKeyCreatesANewOrder() throws Exception {
        String key = "key-" + customer;
        String first = create(key, order(customer), 201).get("id").asText();
        clock.advance(OrderService.IDEMPOTENCY_KEY_TTL.plus(Duration.ofSeconds(1)));

        String second = create(key, order(customer), 201).get("id").asText();
        assertThat(second).isNotEqualTo(first);
        assertThat(ordersFor(customer)).isEqualTo(2);
    }

    @Test
    void replaceLinesWithAStaleIfMatchIs412AndLeavesTheOrderUnchanged() throws Exception {
        String id = create("key-" + customer, order(customer), 201).get("id").asText();
        String oneLine = """
                {"lines":[{"sku":"SKU-9","description":"Ruler","quantity":1,"unitPrice":"25.00"}]}""";

        mvc.perform(put("/orders/{id}/lines", id).header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(oneLine))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(25.00))
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));

        mvc.perform(put("/orders/{id}/lines", id).header(HttpHeaders.IF_MATCH, "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(oneLine.replace("25.00", "99.00")))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(get("/orders/{id}", id)).andExpect(jsonPath("$.total").value(25.00));
    }

    @Test
    void savingAStaleCopyLosesTheOptimisticLockRace() throws Exception {
        UUID id = UUID.fromString(create("key-" + customer, order(customer), 201).get("id").asText());
        // Two writers read the same version in separate transactions, as two requests would.
        Order first = tx.execute(status -> repository.findById(id).orElseThrow());
        Order second = tx.execute(status -> repository.findById(id).orElseThrow());

        first.confirm(clock.instant());
        tx.executeWithoutResult(status -> repository.update(first));

        second.cancel(clock.instant());
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> repository.update(second)))
                .isInstanceOf(OptimisticLockingFailureException.class);
        mvc.perform(get("/orders/{id}", id)).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void listingFiltersByCustomerAndStatusNewestFirst() throws Exception {
        String a = create("a-" + customer, order(customer), 201).get("id").asText();
        clock.advance(Duration.ofMinutes(1));
        String b = create("b-" + customer, order(customer), 201).get("id").asText();
        clock.advance(Duration.ofMinutes(1));
        create("c-" + customer, order(customer), 201);
        mvc.perform(post("/orders/{id}/confirm", a)).andExpect(status().isOk());
        mvc.perform(post("/orders/{id}/confirm", b)).andExpect(status().isOk());

        mvc.perform(get("/orders").param("customerId", customer).param("status", "CONFIRMED").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(b));
        mvc.perform(get("/orders").param("customerId", customer).param("status", "CONFIRMED").param("page", "1").param("size", "1"))
                .andExpect(jsonPath("$.content[0].id").value(a));
    }

    @Test
    void actuatorHealthIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    private JsonNode create(String key, String body, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/orders").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private Integer ordersFor(String customerId) {
        return jdbc.queryForObject("select count(*) from orders where customer_id = ?", Integer.class, customerId);
    }

    private static String order(String customerId) {
        return ORDER.formatted(customerId);
    }
}
