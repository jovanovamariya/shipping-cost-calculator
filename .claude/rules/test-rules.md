---
paths:
  - "src/test/java/**"
---

You are editing test code. Tests are executable specifications.

- Prove each rule at the lowest tier that can: service / value-object tests
  (plain JUnit 6 + AssertJ, no Spring) for pure logic; `@SpringBootTest` + MockMvc
  acceptance tests only for the HTTP contract. Don't repeat the same assertion
  across both tiers. (Per-tier compiled examples live in the tdd skill's
  `test-patterns.md`.)
- Placement: acceptance tests in `src/test/java/.../acceptance/`; service/unit
  tests beside the code they prove.
- Name methods after the business rule (`sameCurrencyConvertsAtRateOne`),
  never `testCalculate3`. Use `@DisplayName` with plain-language, Example-Mapping
  descriptions: "The one where 100 EUR converts to 108.50 USD".
- Money assertions: compare with `compareTo()` (AssertJ `isEqualByComparingTo`),
  never `equals()` — `BigDecimal` is scale-sensitive. Assert exact values; no
  floating-point tolerance.
- Never modify an existing test to make production code pass — fix the production
  code instead.
