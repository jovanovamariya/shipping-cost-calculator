# Currency Conversion

**As a user, I want to convert an amount from one currency to another so that I know how much it's worth in the other currency.**

## Rules and Examples

### Rule: Must convert an amount at the current rate and report the result, the rate used and the rate's publication date

The rate date is the provider's publication date — never the time we fetched the rates.

- **Example:** The one where 100 EUR → USD at 1.0850 (published 2026-11-11) gives 108.50 USD, rate 1.0850, rate date 2026-11-11, `stale: false`.
- **Counter-example:** The one where on Monday 2026-11-09 a successful fetch returns Friday 2026-11-06 rates — rate date 2026-11-06 and `stale: false`, because the provider answered and a weekend gap is normal.

---

### Rule: Must round the converted amount to 2 decimals using HALF_UP

Supersedes AC1.2 of the original story (HALF_EVEN) to follow the project-wide HALF_UP rule. The rate itself is not rounded.

| Amount | Rate | Raw result | Result |
|---|---|---|---|
| 100 | 1.0850 | 108.500 | 108.50 |
| 100 | 1.08125 | 108.125 | 108.13 |
| 100 | 1.08124 | 108.124 | 108.12 |
| 100.555 | 1 (EUR → EUR) | 100.555 | 100.56 |

The 108.125 row is the boundary: an exact half rounds up. The last row shows the amount itself may carry any number of decimals.

---

### Rule: Must treat a same-currency conversion as rate 1 without asking the provider

- **Example:** The one where 100 EUR → EUR gives 100.00, rate 1, rate date = today, and no provider call is made.
- **Counter-example:** The one where XYZ → XYZ is still rejected with "Unsupported currency: XYZ", because the code is checked against the supported list first.

---

### Rule: Must accept currency codes in any letter case

- **Example:** The one where `eur` → `usd` gives the same result as EUR → USD, and the response shows the codes in upper case.
- **Counter-example:** The one where `euro` is not a currency code and is rejected as unsupported.

---

### Rule: Must reject an unsupported currency

- **Example:** The one where 100 XYZ → USD is rejected with status 400 and "Unsupported currency: XYZ".

---

### Rule: Must reject a missing, zero or negative amount

| Amount | Outcome |
|---|---|
| 100 | Converted |
| 0.01 | Converted |
| 0 | 400 |
| -5 | 400 |
| missing | 400 |

0.01 is the smallest accepted amount; 0 is the boundary that flips to rejection.

---

### Rule: Must report every error in one consistent shape without internal details

- **Example:** The one where an unsupported currency returns `timestamp`, `status: 400`, `error: "Bad Request"`, `message: "Unsupported currency: XYZ"`, `path: "/api/convert"` — and no stack trace.
- **Counter-example:** The one where an unexpected internal error returns the same shape with status 500 and a generic message, with no exception details.

---

## Resolved decisions (for implementation)

- **Rounding:** HALF_UP, scale 2, `BigDecimal` throughout (supersedes the story's HALF_EVEN).
- **Rate date:** provider publication date; the fetch time is internal and only drives caching (see `rate-freshness-and-fallback.specs.md`).
- **Same currency:** validated against the supported list (see `supported-currencies.specs.md`), then answered locally with rate 1 and today's date.
- **Amount validation:** enforced by the API as well as the form; extra decimals accepted, only the result is rounded.
- **Provider abstraction:** the conversion logic obtains rates through a `RatesProvider` interface, never the Frankfurter client directly, so tests can use a fake provider (US9).
- **Out of scope:** UI behaviour — disabled Convert button for empty/zero/negative amounts and friendly display of API errors (frontend only).
