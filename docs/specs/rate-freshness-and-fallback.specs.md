# Rate Freshness and Fallback

**As a user, I want fast conversions that keep working when the rates service has problems so that the app stays useful.**

## Rules and Examples

Two times are tracked for every set of rates and must never be mixed up:

- **Publication date** — when the provider published the rates. Shown to users, reported as the rate date, used in the stale warning and to measure stale age.
- **Fetched-at** — when we fetched them. Internal only; drives the cache TTL.

### Rule: Should reuse rates for a base currency fetched less than 1 hour ago

| Base requested | Cached rates | Provider called? |
|---|---|---|
| EUR | EUR, fetched 10 min ago | No |
| EUR | EUR, fetched 59 m 59 s ago | No |
| EUR | EUR, fetched 60 min ago | Yes — refreshed |
| USD | EUR only | Yes — the cache is per base currency |

The 60 min row is the expiry boundary; the USD row shows the cache does not cross base currencies.

- **Counter-example:** The one where EUR rates published last Friday were fetched 10 minutes ago — they are reused, because cache age is measured from the fetch, not the publication date.

---

### Rule: Must make only one provider call when simultaneous requests find no cached rates for a base

- **Example:** The one where 20 EUR conversions arrive at the same moment with an empty cache, and the provider is called exactly once.

---

### Rule: Must fall back to the last known rates for that base, marked stale, when the provider fails or the circuit is open

`stale: true` means "served from fallback" and nothing else.

- **Example:** The one where EUR rates published 2026-11-11 were fetched at 09:00, the provider fails at 11:00 on 2026-11-12, and 100 EUR → USD returns a result with `stale: true` and rate date 2026-11-11.
- **Counter-example:** The one where the provider answers normally — `stale: false`, whatever the publication date.

---

### Rule: Must not serve fallback rates whose publication date is older than the configured maximum stale age

Max stale age is in days and unset by default (no limit). Age = today − publication date; age equal to the limit is still served.

| Max stale age | Today | Last rates published | Outcome |
|---|---|---|---|
| not set | Thu 2026-11-12 | Mon 2026-11-02 | Served, `stale: true` |
| 3 days | Mon 2026-11-09 | Fri 2026-11-06 (3 days) | Served, `stale: true` |
| 3 days | Tue 2026-11-10 | Fri 2026-11-06 (4 days) | 503 — temporarily unavailable |
| 3 days | Thu 2026-11-12 | Fri 2026-11-06, fetched 5 min ago | 503 — a recent fetch does not make old data fresh |

The 3-day row is the boundary (age = limit is allowed); the last row guards against judging staleness by fetch time.

---

### Rule: Must answer "temporarily unavailable" when the provider is down and no rates were ever loaded for that base

- **Example:** The one where the provider is down and no EUR rates exist — status 503 with "Exchange rates are temporarily unavailable. Please try again later."

---

## Resolved decisions (for implementation)

- **Cache TTL:** 1 hour per base currency, measured from fetched-at.
- **Max stale age:** configurable in days, default no limit; documented in the README with the advice to set it to at least 4 days, so normal weekend/holiday gaps (ECB does not publish then) don't cause 503s during an outage.
- **Stale flag:** true only for fallback results.
- **Out of scope:** the stale warning in the app — "Rates may be outdated (last updated at 2026-11-11)" — is frontend only and uses the publication date.
