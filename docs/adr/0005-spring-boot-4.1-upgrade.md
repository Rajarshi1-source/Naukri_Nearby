# ADR 0005 — Upgrade Spring Boot 4.0.6 → 4.1.1

## Status
Accepted.

## Context
The backend started on Spring Boot 4.0.6 (Java 21, Gradle 9.5.1). Spring Boot 4.1.0 went GA on
2026-06-10, and 4.1.1 (2026-08-20) is its first patch, with 98 fixes. 4.1 brings Spring Security 7.1,
Spring Data 2026.0, Hibernate 7.4, Lettuce 7, Flyway 12, Micrometer 1.17 and Elasticsearch Java
client 9.4.5. It also **removes every API and property deprecated in 4.0**. Staying on 4.0.x means
falling behind the supported minor and making the eventual 4.2 jump larger.

## Decision
- Move the Gradle plugin to `org.springframework.boot` **4.1.1**. Java stays 21, Gradle stays 9.5.1,
  `io.spring.dependency-management` stays 1.1.7, and `resilience4j-spring-boot4` stays 2.4.0
  (pinned, since it is not in the Boot BOM).
- Move the Elasticsearch **server** image (Compose and Testcontainers) from 9.2.8 to **9.4.5**, so it
  equals the Boot-managed Java client. This is a standing rule for every future Boot upgrade.
- No application code or `application.yml` changes were required: the code had zero deprecation or
  removal warnings on 4.0.6, and none of our property keys are renamed or removed in 4.1.
- New guardrail: main and test sources must compile clean under `-Xlint:deprecation -Xlint:removal`,
  so the next minor upgrade is equally mechanical.

## Consequences
- Supported OSS line until 2027-07. Security, JPA, Redis and Flyway drivers move a minor or major
  version underneath the Spring abstractions we use, and the full Testcontainers suite, a real-stack
  smoke test, browser E2E and the eval gate verified there were no behavioural changes.
- Indices written by ES 9.4.5 can't be opened by 9.2.x. A rollback must drop the local ES volume and
  reindex from Postgres through the outbox/alias-flip path (ADR 0001, ADR 0003). Postgres needs no
  rollback because no migration was added.
- Plan and verification log: `docs/migration/spring-boot-4.1.1-migration-plan.md`.
