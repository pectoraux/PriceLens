# Tickets — Epics L, M, N (first tranche)

Epics L (Category Generalization), M (Price Resolution), N (Civic Points & Revenue).
Design: docs [09](09categoryarchitecture.md), [10](10priceresolution.md),
[11](11civicpointsandrevenue.md). Order and rationale: [doc 12](12auditandmigration.md).

**Definition of Done, applies to every ticket:** acceptance criteria have automated coverage;
public APIs have KDoc; the architecture rules in [`GEMINI.md`](GEMINI.md) are not violated; CI is
green; the commit message is `[{id}] {title}`.

This file specifies the **unblocked tranche only** — the tickets that can start today. The rest of
L, M and N are listed in doc 12 Part 3 and get written up once these land, because several of them
depend on what these turn up.

> **Agent note.** Every value in a `category_profile` row is a product judgment and is supplied by
> a human. These tickets build the mechanism that reads them. Where a ticket says "seed with the
> values in doc 09", that means copy them exactly — do not tune, round, or "improve" them.

---

# EPIC L — Category Generalization

### L-00 — Alembic + schema/OpenAPI parity check in CI
**Size** M · **Depends on** A-09 · **Spec** docs 05, 12

The backend has SQLAlchemy models and no migration tool, and the models have already drifted from
doc 05 — `Observation` is missing every trust column the trust layer depends on. Fix the drift and
make it impossible to reintroduce silently.

**Acceptance criteria**
- [ ] Alembic installed and configured; revision `0001_baseline` reproduces the doc-05 schema
      exactly on an empty database.
- [ ] `Observation` gains `attestation_tier`, `provenance_tier`, `geo_confidence`,
      `synthetic_score`, `trust_weight`, `evidence_key`, `evidence_phash`, `embedding`,
      `capture_seal`, `price_per_canonical`, `canonical_unit`, `predicted_p50_minor` — types and
      nullability per doc 05.
- [ ] `alembic upgrade head` then `alembic downgrade base` runs clean on a scratch database.
- [ ] CI job fails when a SQLAlchemy model has no corresponding migration
      (`alembic check` or an autogenerate-produces-empty-diff assertion).
- [ ] CI job fails when a FastAPI route exists that `api/openapi.yaml` does not declare, or vice
      versa.
- [ ] Both checks have a fixture proving they fire — add a deliberate divergence, watch it fail,
      revert it.

**Notes:** do this before any new schema lands or the drift compounds. The parity fixtures matter
more than the checks: a CI rule that has never fired is not known to work.

---

### L-01 — `category_profile` table, seeding, signed bundle distribution
**Size** M · **Depends on** L-00, F-05 · **Spec** doc 09 §"The core abstraction"

Create the record that holds every category-varying parameter, and ship it to clients the same way
taxonomy and prototypes already ship.

**Acceptance criteria**
- [ ] `category_profile` table matches the DDL in doc 09 exactly, via an Alembic migration.
- [ ] Seed script inserts exactly one row, `fungible_loose`, whose values reproduce today's
      constants from `domain/.../policy/Thresholds.kt` **verbatim** — `log_tolerance 0.25`,
      `consensus_min_obs 3`, `consensus_min_contributors 3`, `consensus_window_days 14`,
      `cell_scope_type geohash6`, `cell_period_type week`, `portion_estimation REQUIRED`.
- [ ] Profiles are included in the catalog bundle, signed with the existing key, and the client
      verifies the signature before use — reusing the F-05 path, not a new one.
- [ ] Client rejects and logs a bundle whose profile signature fails; falls back to the last good
      profile set rather than to defaults.
- [ ] `GET /v1/catalog/manifest` reports a `categoryProfileVersion`, and `api/openapi.yaml` is
      updated in the same commit (L-00's parity check enforces this).
- [ ] Room cache + DAO for profiles, with a test proving a profile survives process death.

---

### L-02 — `ProfileThresholds` resolver replacing `Thresholds` constants ★
**Size** M · **Depends on** L-01 · **Spec** doc 09

Replace the global constants object with a resolver over the active category profile. **This is a
pure refactor: behaviour must be bit-identical afterwards.**

**Acceptance criteria**
- [ ] `ProfileThresholds(profile)` in `:domain` exposes every category-varying threshold currently
      in `Thresholds`: `logTolerance`, `consensusMinObs`, `consensusMinContributors`,
      `consensusMinCombinedWeight`, `consensusWindowDays`, `maxVerdictAgeDays`,
      `minObservationsForVerdict`, `verdictWellAboveThreshold`, `maxRelativeErrorForMass`, and
      quantity bounds.
- [ ] **Every one of those reads from the profile.** A getter returning a literal fails this
      ticket: it makes the resolver *look* category-driven while it is not, which is worse than
      leaving the constant where it was.
- [ ] Genuinely global values **stay** as constants and are not moved: `TAU_LABEL`, `MARGIN_MIN`,
      `LOW_DEVICE_RECOGNITION_THRESHOLD`, the anti-rephotography thresholds, the geo-integrity
      speed limits. Moving these would be wrong — they are properties of the model and the device,
      not of the category.
- [ ] **No `ProfileThresholds` parameter anywhere has a default value.** Omitting the profile must
      be a compile error, not a silent fall back to produce thresholds. This is the criterion the
      whole ticket exists for: once L-06 lands, a defaulted parameter means a durable is judged on
      a ±25% agreement band and no test fails.
- [ ] `ConsensusPolicyTest`, `PriceVerdictPolicyTest`, `AggregationTest`, `ReputationTest` and
      `MassEstimatorTest` keep **every assertion and every expected value unchanged**. The only
      permitted edit is passing `ProfileThresholds(CategoryProfile.FUNGIBLE_LOOSE)` explicitly at
      the call site. A changed expected value means the refactor altered behaviour; reject it.
- [ ] A test asserts each resolver output for `fungible_loose` against a **literal** — `0.25f`,
      `3`, `14`, `50.0`, and so on. Asserting against `Thresholds.X` is tautological when `X` is
      itself defined from the profile, and can never fail.
- [ ] No call site outside `:domain` reads a threshold directly; the A-04 architecture check is
      extended to fail the build if any file outside `/domain/` imports
      `com.pricelens.domain.policy.Thresholds`, with a fixture proving the rule fires.
- [ ] `MAX_PLAUSIBLE_KG` / `MAX_PLAUSIBLE_PIECES` are resolved from `profile.quantity_bounds`.

**Notes:** ★ the ideal agent ticket — an exact oracle, no judgment required.

Review it by checking that no *expected value* moved and that no `ProfileThresholds` parameter is
defaulted. An earlier version of this ticket said "reject any diff that edits a test file", which
was the wrong shape: it makes a defaulted parameter the cheapest way to pass, and a defaulted
parameter is precisely the failure this ticket must prevent. Updating call sites is expected;
updating expectations is not.

---

### L-03 — `product_sku` identity + observation linkage
**Size** L · **Depends on** L-01 · **Spec** doc 09 §"Identity: items are not SKUs"

Add the SKU/model identity level beneath the class, without making the class optional.

**Acceptance criteria**
- [ ] `product_sku` table per doc 09 DDL, with the GIN index on `attributes`.
- [ ] `observation.sku_id` nullable FK; `observation.item_id` stays **non-null**.
- [ ] A DB-level constraint or trigger rejects an observation whose `sku_id` resolves to a
      `product_sku` whose `item_id` differs from the observation's `item_id`. Test both directions.
- [ ] `merged_into_id` resolution: querying a merged SKU transparently returns the survivor, and
      historical observations are not orphaned. Test with a 3-deep merge chain.
- [ ] Aggregation can key a cell on either identity level; class-level cells are computed from all
      observations including those with a SKU.
- [ ] Backfill script is idempotent — running it twice changes nothing.

---

### L-04 — Produce-only columns → profile-scoped attributes
**Size** M · **Depends on** L-03 · **Spec** doc 09

Move `density_kg_per_l`, `shape_model`, `is_perishable`, `seasonality` and `scientific_name` off
`taxonomy_item` into a nullable `attributes JSONB` owned by the `fungible_loose` profile.

**Acceptance criteria**
- [ ] Migration moves the data, does not drop it, and is reversible.
- [ ] `:ml:portion` reads density and shape from attributes; `MassEstimatorTest` passes unchanged.
- [ ] A round-trip test proves every pre-migration value is retrievable post-migration.
- [ ] Client Room cache and the catalog bundle carry attributes as an opaque map, not typed
      columns — adding an attribute must not require a client release.

---

### L-05 — Generalized unit families + locality conversion rework
**Size** M · **Depends on** L-02 · **Spec** doc 09 §"Units, generalized"

Extend `CanonicalUnit` to the seven families in doc 09 and key vernacular conversions on the
category profile rather than a food category string.

**Acceptance criteria**
- [ ] Unit families implemented per doc 09's table; each unit declares its family, and conversion
      across families is a compile-time or type-level impossibility.
- [ ] `profile.canonical_units` constrains which units an observation of that category may use;
      violation is rejected at entry with a specific error, not silently coerced.
- [ ] `locality_unit_conversion` keys on `category_profile` rather than `item_category`, migrated.
- [ ] Where no conversion exists, the vernacular unit is retained as canonical for that locality —
      no fabricated conversion. Test: a "truckload" with no conversion stays a truckload and does
      **not** become a mass.
- [ ] Derived per-unit price for packaged goods is computed at query time from
      `product_sku.net_content`, never stored — so a later net-content correction retroactively
      fixes every derived price. Test that it does.
- [ ] Existing `MoneyTest` and unit-conversion tests pass unchanged.

---

# EPIC M — Price Resolution

### M-01 — `PriceAnswer` contract with `basis`, `n_effective`, `verdict_allowed`
**Size** M · **Depends on** A-05, G-06 · **Spec** doc 10 §"Principle: the basis is part of the answer"

The contract change that every later M ticket depends on. Land it before the ladder exists, so
clients never learn to ignore the field.

**Acceptance criteria**
- [ ] `PriceAnswer` in `api/openapi.yaml` and the generated Kotlin client, with all fields from
      doc 10: `basis`, `n_effective`, `n_contributors`, `freshness_days`, `spatial_reach_km`,
      `confidence`, `verdict_allowed`, `source_mix`.
- [ ] `basis` is a closed enum; an unknown value from the server is handled as `INSUFFICIENT`
      rather than crashing or defaulting to a confident basis.
- [ ] `n_effective` computed as Kish `(Σw)²/Σw²` over surviving weights. Unit-tested against three
      hand-computed cases including one where a single contributor holds 60% of the weight.
- [ ] **The client never computes `verdict_allowed`.** `PriceVerdictPolicy` takes it as an input;
      the A-04 check fails the build if a feature module derives it. Test that a `verdict_allowed
      = false` answer shows no verdict even when the price sits far outside the band.
- [ ] Existing `PriceVerdictPolicyTest` extended, not rewritten.
- [ ] `:feature:review` renders a visibly distinct treatment per basis; `@Preview` for each.

---

### M-07 — Client-side price input validation suite
**Size** M · **Depends on** I-05, L-05 · **Spec** doc 10 §"Price input: validation before submission"

Pure `:domain` logic, no network, covering the six checks in doc 10. Catches honest mistakes before
they become permanent bad data.

**Acceptance criteria**
- [ ] Each check implemented and independently unit-tested: currency minor-unit confusion,
      order-of-magnitude vs the prior band, digit transposition, unit mismatch, quantity bounds
      from `profile.quantity_bounds`, total-vs-each.
- [ ] **Total-vs-each is an explicit user toggle and is never inferred.** Test that no code path
      guesses it. This is the most common silent corruption in every crowdsourced price dataset.
- [ ] Order-of-magnitude check offers both candidate values and requires a choice; it never
      auto-corrects.
- [ ] No check ever *rejects* a submission. Every one returns a prompt; the user can always
      proceed. Test that an input failing all six checks is still submittable.
- [ ] Runs with no network and no cached band available (all checks that need a prior are skipped,
      not failed).
- [ ] Fully unit-tested in `:domain` with zero Android dependencies.

---

# EPIC N — Civic Points & Revenue

### N-02 — `civic_ledger_entry` + balances, append-only, rebuildable
**Size** M · **Depends on** H-10 · **Spec** doc 11 §"The ledger"

The ledger schema and its invariants. No issuance logic yet — that is N-03.

**Acceptance criteria**
- [ ] Tables per doc 11 DDL, including the partial unique index on
      `(observation_id, entry_type)`.
- [ ] Append-only enforced at the database level: `UPDATE` and `DELETE` on `civic_ledger_entry`
      are rejected by a trigger or revoked grant. Test that both fail.
- [ ] Corrections are new rows with `reversal_of` set, never edits. Test a full
      accrue → vest → clawback chain and assert the row count only grows.
- [ ] `points` is `BIGINT`. A `Float`/`Numeric` points column fails review — the A-04 money rule
      extends to points.
- [ ] `contributor_balance` is derivable: a rebuild from `civic_ledger_entry` alone reproduces
      every balance exactly. Property test over randomized entry sequences.
- [ ] The `computation` JSONB records every input to the award (`ig_nats`, `q`, multiplier, `K`)
      so an award can be independently recomputed. Test that it round-trips.
- [ ] **No foreign key, join, or code path connects this table to `contributor_reputation`.** The
      two ledgers are separate by design; add an architecture test asserting it.

---

## Handover order

One ticket per Gemini session, in this order. L-00 and N-01 (legal, not an agent ticket) start in
parallel today.

```
L-00 ──► L-01 ──► L-02 ★ ──┬──► L-03 ──► L-04
                            └──► L-05
M-01 ──► M-07                      (independent of the L chain after L-05)
N-02                               (independent; needs H-10 landed)
```

**Start with L-00.** The schema drift is already present and everything else adds to it.
**L-02 is the one to hand over first if you want to calibrate the agent** — it has an exact oracle,
so a bad result is obvious in seconds rather than in review.
