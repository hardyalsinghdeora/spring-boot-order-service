# 0003: H2 for development and tests, PostgreSQL for deployment

## Context
The service should start with one command and its tests should need no
infrastructure, but it targets PostgreSQL.

## Decision
The default profile uses in-memory H2 in PostgreSQL compatibility mode. The
`postgres` profile reads the connection from `SPRING_DATASOURCE_*`. Flyway owns
the schema in both cases and Hibernate only validates the mapping
(`ddl-auto: validate`), so one migration file serves both databases.

## Consequences
- `./mvnw verify` and `./mvnw spring-boot:run` work on a laptop with nothing installed but a JDK.
- H2's compatibility mode is not PostgreSQL. Behaviour that depends on PostgreSQL itself
  (locking, isolation details, `jsonb`) is not covered by these tests; the next step would be
  Testcontainers in CI.
- The migration sticks to SQL both databases accept.
