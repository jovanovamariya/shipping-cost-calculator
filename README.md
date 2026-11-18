# Currency Converter

A Spring Boot REST service that converts an amount between currencies using live rates from the [Frankfurter](https://frankfurter.dev) provider. It caches rates, falls back to the last good rates when the provider is unavailable, and wraps provider calls in a circuit breaker so the API stays responsive when the provider is slow or down.

It is built with a **Spec-Driven Development (SDD)** workflow using Claude Code: business rules live in `docs/specs/` as markdown, each rule is driven by an acceptance test, and the full suite is enforced automatically on every change via the `.claude/` toolchain — skills, agents, and hooks.

## About This Course

This project is a resource from the Udemy course **[Spec-Driven Development in Java with TDD and Claude Code](https://www.udemy.com/course/spring-boot-ai-tdd/?referralCode=3170E302C61D48703A94)**. The course walks through building a fully fledged Spring Boot application from the ground up, using all the features of Claude Code — skills, agents, hooks, and the Spec-Driven Development workflow that this project is built around.

## Requirements

- **JDK 21** (Spring Boot 4.1 / Spring Framework 7 require Java 17+; this project targets 21). Check with `java -version`.
- No Gradle install needed — the Gradle Wrapper (`./gradlew`) is included and downloads the right version on first use.
- Internet access on first build so Gradle can fetch dependencies, and at runtime so the service can reach the Frankfurter provider.

## Build

```bash
./gradlew build          # compile, run tests, and assemble the jar
./gradlew assemble       # build the jar without running tests
./gradlew clean build    # clean rebuild
```

The runnable jar is written to `build/libs/`.

On Windows, use `gradlew.bat` in place of `./gradlew`.

## Test

```bash
./gradlew test                                   # run the full suite
./gradlew test --tests '*ConversionServiceTest'  # a single test class
./gradlew test --tests '*.acceptance.*'          # only acceptance tests
./gradlew test --info                            # verbose output
```

Test report (HTML): `build/reports/tests/test/index.html`.

Tests come in two tiers (see `CLAUDE.md` → *Testing Conventions*):

- **Acceptance tests** — `src/test/java/.../acceptance/`, `@SpringBootTest` + `MockMvc`, exercising the full HTTP cycle.
- **Service / unit tests** — `src/test/java/.../service/`, plain JUnit 6, no Spring context.

Testing stack: **JUnit 6.1.0 (Jupiter)** and **AssertJ**, both pulled in via `spring-boot-starter-test`.

## Run

```bash
./gradlew bootRun                                # run from source (dev)
```

Or build and run the jar:

```bash
./gradlew bootJar
java -jar build/libs/*.jar
```

The service starts on **http://localhost:8080** by default.

Convert an amount between two currencies:

```bash
curl -X POST http://localhost:8080/api/convert \
  -H 'Content-Type: application/json' \
  -d '{ "amount": 100, "from": "EUR", "to": "USD" }'
```

```json
{ "amount": 100, "from": "EUR", "to": "USD", "result": 108.50, "rate": 1.0850, "rateDate": "2026-11-11", "stale": false }
```

`result` is the converted amount (scale 2, half-up); `rate` is the exchange rate used; `rateDate` is the provider's publication date for that rate; `stale` is `true` only when the rate was served from the fallback because the provider was unavailable.

List the supported currencies, sorted by code:

```bash
curl http://localhost:8080/api/currencies
```

Errors use a single shape — `{ timestamp, status, error, message, path }`, never a stack trace. An unsupported currency returns `400` with `"Unsupported currency: XYZ"`; when no usable rates are available the service returns `503`.

To change the port or other settings, edit `src/main/resources/application.properties` (e.g. `server.port=9090`) or pass it at runtime: `./gradlew bootRun --args='--server.port=9090'`.

### API documentation (Swagger UI)

With the app running, interactive API docs are at **http://localhost:8080/swagger-ui.html**, generated automatically from the controllers by springdoc-openapi. The raw OpenAPI spec is at `/v3/api-docs`.

## Configuration

All settings live in `src/main/resources/application.properties`. Override any of them at runtime (`./gradlew bootRun --args='--rates.max-stale-age-days=4'`) or via an environment variable (`RATES_MAX_STALE_AGE_DAYS=4`). The knobs specific to the currency converter:

| Property | Default | Purpose |
|---|---|---|
| `cors.allowed-origins` | `http://localhost:5173` | Comma-separated list of browser origins allowed to call `/api/**`. Matching is exact, including scheme and port. Set this to your frontend's origin(s) in production. |
| `rates.max-stale-age-days` | `-1` (no limit) | How many days old a fallback rate may be and still be served when the provider is unavailable. `-1` disables the limit; **≥ 4 is recommended** so a Friday publication still covers a long weekend plus a bank holiday. Beyond this age the service returns `503` instead of a stale rate. |
| `currencies.list-ttl` | `24h` | How long the supported-currencies list is cached before it is refetched. |
| `rates.provider.base-url` | `https://api.frankfurter.dev/v1` | Base URL of the Frankfurter rates provider. |
| `rates.provider.timeout` | `3s` | Connect and read timeout applied to each provider call. |
| `rates.provider.cache-ttl` | `1h` | How long a fetched rate set is served from cache before the provider is called again. |

Durations use Spring's format (`3s`, `1h`, `24h`, `4d`). The circuit breaker around the provider — a 10-minute window that opens at ≥ 50% failures over a minimum of 3 calls, stays open 5 minutes, then allows one trial call — is fixed in `RatesConfig` and not currently exposed as a property.

`GET /actuator/health` reports the application status and the rates circuit state; `management.endpoint.health.show-details=always` (set in `application.properties`) is what makes the circuit detail visible.

## The SDD Workflow

Claude Code drives features through three steps, each backed by a skill you invoke by name:

1. **`/discover`** — Turn a feature idea into a spec using Example Mapping (rule → example → counter-example → edge cases → open questions). Saves a draft spec to `docs/specs/<feature>.specs.md`.
2. **`/accept`** — Write a failing acceptance test for the next rule in a spec, hitting the real endpoint. One rule at a time.
3. **`/tdd`** — Run one TDD inner-loop cycle (RED → GREEN → REFACTOR) to drive that test to green with the minimum code.

Two review agents enforce quality on demand:

- **`architecture-guardian`** — checks that code respects the layer boundaries (controllers stay thin, services hold the logic, models stay data-only).
- **`spec-compliance`** — checks that every spec rule has a test, that precision/ordering rules hold, and that the API contract matches.

Three hooks (configured in `.claude/settings.json`) run automatically:

- **PreToolUse** — `protect-files.sh` blocks edits to protected files (prod config, secrets, CI).
- **PostToolUse** — runs `./gradlew test` after every edit, so regressions surface immediately.
- **Stop** — runs the full suite when a turn ends, gating completion on green tests.

## Project Layout

```
build.gradle                     Dependencies, Java/Spring Boot versions
settings.gradle                  Root project name
src/main/java/...                Application code (controller / service / model)
src/main/resources/              application.properties
src/test/java/.../acceptance/    Acceptance tests (@SpringBootTest + MockMvc)
src/test/java/.../service/       Service/unit tests (plain JUnit 6)
docs/specs/                      Business rules, one .specs.md file per feature
CLAUDE.md                        Project context Claude reads before any work
.claude/skills/                  discover, accept, tdd, and other workflow skills
.claude/agents/                  architecture-guardian, spec-compliance reviewers
.claude/hooks/                   protect-files.sh (file guard)
.claude/settings.json            Hook wiring
```
