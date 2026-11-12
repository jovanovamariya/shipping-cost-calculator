# Supported Currencies

**As a user, I want to pick currencies from a list so that I don't have to know or type currency codes.**

## Rules and Examples

### Rule: Should list every supported currency, sorted alphabetically by code

- **Example:** The one where the list reads AUD, BGN, BRL … USD, ZAR, each with its name (AUD — "Australian Dollar").

---

### Rule: Should reuse a currency list fetched within the configured list TTL (default 24 hours)

The age is measured from when we fetched the list.

| List fetched | Provider called? |
|---|---|
| 2 h ago | No |
| 23 h 59 m ago | No |
| 24 h ago | Yes — refreshed |

The 24 h row is the boundary where the cached list expires.

---

### Rule: Must keep serving the last loaded list when a refresh fails

- **Example:** The one where the list was fetched 25 h ago, the provider is down, and the old list is still served.
- **Counter-example:** The one where the provider is down on first start and no list was ever loaded — status 503 with "Exchange rates are temporarily unavailable. Please try again later."

---

## Resolved decisions (for implementation)

- **List TTL:** 24 hours, configurable; documented in the README.
- **Resilience:** currency-list calls go through the same timeout and circuit breaker as rate calls (see `provider-resilience.specs.md`).
- **Out of scope:** rendering the "from" and "to" dropdowns (frontend only).
