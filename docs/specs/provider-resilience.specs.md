# Provider Resilience

**As an operator, I want the service to stop calling the rates API when it keeps failing, and to see its health, so that failures don't spread and I can react quickly.**

## Rules and Examples

### Rule: Must abandon a provider call after 3 seconds and count it as a failure

- **Example:** The one where the provider takes 5 s — the call is cut at 3 s, counted as a failure, and the fallback applies.
- **Counter-example:** The one where the provider answers in 2.9 s — counted as a success.

---

### Rule: Must stop calling the provider when 50% or more of the calls in the last 10 minutes failed, with a minimum of 3 calls

Failures are 5xx responses, connection errors and timeouts.

| Calls in window | Failures | Circuit |
|---|---|---|
| 3 | 2 | Open |
| 4 | 2 | Open (exactly 50%) |
| 3 | 1 | Closed (33%) |
| 2 | 2 | Closed (below the 3-call minimum) |

The 4/2 row is the rate boundary; the 2/2 row is the minimum-calls boundary.

---

### Rule: Must allow one trial call once the circuit has been open for 5 minutes

| Circuit opened | Next request | Trial call | Circuit after |
|---|---|---|---|
| 10:00 | 10:04 | None — fallback served | Open |
| 10:00 | 10:05 | Succeeds | Closed |
| 10:00 | 10:05 | Fails | Open again for another 5 min |

---

### Rule: Must not count provider client errors (4xx) as failures

- **Example:** The one where 3 calls in a row return 404 and the circuit stays closed.

---

### Rule: Should show application health and circuit state to operators

- **Example:** The one where the circuit is open and the health check reports the application UP and the rates circuit OPEN.

---

### Rule: Should log every provider call's duration and outcome

- **Example:** The one where a timed-out call is logged with a duration of about 3000 ms and outcome "fallback"; successful and failed calls log "success" and "failure".

---

### Rule: Should log every circuit state change

- **Example:** The one where the circuit moves CLOSED → OPEN and later OPEN → HALF_OPEN, and each change is logged with its from- and to-state.

---

## Resolved decisions (for implementation)

- **Scope:** rate calls and currency-list calls share the same timeout and circuit breaker.
- **Health:** exposed via `GET /actuator/health`, including the circuit breaker state.
