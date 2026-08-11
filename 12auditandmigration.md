# 12 — Repository Audit & Migration Plan

> What exists today, what the three new use cases require, and the ordered path between them.

## Part 1 — Audit

### What is genuinely strong

The design pack (docs 00–08) is unusually good and most of it survives the expansion untouched. In
particular:

- **Canonical camera space** (doc 02). Device normalization is category-independent — a fridge
  photographed on a cheap wide-angle sensor benefits exactly as much as a tomato. This is the most
  expensive part of the project and none of it is wasted.
- **Open-vocabulary retrieval over a learned index** (doc 03). The decision that *the index learns,
  not the backbone* is what makes 10⁵ SKUs tractable at all. A fixed-class classifier would have
  made the multi-category ask impossible.
- **The nine trust layers** (doc 04). Attestation, provenance, geo-integrity, reputation, robust
  aggregation, consensus, collusion detection, curation gate. All of it generalizes, and the
  influence-tracing requirement turns out to be a prerequisite for the payout ledger — which is a
  fortunate accident, since it cannot be retrofitted.
- **Abstention as a first-class output**, bands rather than point estimates, and storing
  `predicted_*` next to the user's answer. These are the decisions that make the learning loop
  measurable, and they were made correctly.
- **Module boundaries with CI-enforced dependency rules.** The `:domain` module is pure Kotlin and
  the architecture test exists. New work lands cleanly.

### Implementation status

The repository is **a design pack plus a skeleton**, not a working system. This matters for
sequencing, because the multi-category work is far cheaper now than it will be after the food
system is built out.

| Layer | State |
|---|---|
| Gradle modules, conventions, DI, navigation | Real, wired |
| `:domain` policy (abstention, verdict, reputation, consensus, aggregation) | Real, unit-tested, faithful to the docs |
| `:ml:*` device profiling, HNSW, fusion, portion, OCR | Substantially implemented, LiteRT interpreters present |
| `:core:trust`, `:core:attest`, `:core:geo` | Present, sealing/attestation partly stubbed |
| `:feature:*` capture / review / contribute / explore | Screens and ViewModels present |
| Backend API routes | **Mocks.** `POST /v1/price/estimate` returns a hardcoded band |
| Backend services (aggregation, reputation, consensus, curator, collusion) | Real algorithms, not wired to the DB or to each other |
| Backend persistence | SQLAlchemy models only. **No migrations directory, no Alembic** |
| Backend ↔ doc-05 schema parity | **Drifted.** `Observation` is missing `attestation_tier`, `provenance_tier`, `geo_confidence`, `trust_weight`, `evidence_key`, `capture_seal`, `embedding` — i.e. every trust column doc 05 specifies |
| `api/openapi.yaml` | 6 paths. Missing `/observations/mine`, `/price/history`, `/device-profiles`, `/taxonomy/proposals` — all documented in doc 05 |

Two conclusions follow. First, **the schema is the contract and it has already drifted from the
document that freezes it** — a migration tool and a spec-parity check in CI are needed before more
schema is added, or the drift compounds. Second, because the backend is still mocks, the
multi-category schema changes below are nearly free today; after Phase 3 they are a data migration
over live price history.

### Gap analysis against the three use cases

#### Use case 1 — an index across all consumer categories

| Gap | Where | Severity |
|---|---|---|
| Taxonomy is produce-shaped: `density_kg_per_l`, `shape_model`, `is_perishable`, `seasonality`, `scientific_name` as first-class columns | `05datamodelandapi.md`, `backend/app/models/taxonomy.py` | High — blocks all non-food |
| No SKU/model identity distinct from class | schema-wide | High — cannot price a specific TV |
| Cell grain hardcoded to `(locality, item, unit, week)` | `price_cell` | High — wrong for durables and materials |
| Consensus rule (≥3 obs / 14 d / same geohash-6) unreachable for sparse categories | `ConsensusPolicy`, `consensus.py` | High — durable cells never publish |
| One global `CONSENSUS_PRICE_LOG_TOLERANCE = 0.25f` | `domain/.../Thresholds.kt` | High — meaningless across archetypes |
| Units limited to `kg/L/piece/bunch/bag/crate` | `domain/.../Units.kt` | Medium |
| Portion estimation unconditional | `:ml:portion`, pipeline Stage 6 | Medium — actively harmful for packaged goods |
| Detection gated on "is this food" | `03recognitionandpriceml.md` §Stage 2 | Medium |
| `MAX_PLAUSIBLE_KG`, `MAX_PLAUSIBLE_PIECES` as global constants | `Thresholds.kt` | Low, but symptomatic |

→ Addressed by **doc 09**.

#### Use case 2 — camera detection, correction, and the right local price

Mostly present, with three real gaps:

| Gap | Severity |
|---|---|
| No query-time resolution procedure. Doc 03 gives the model; nothing specifies what to do when the local cell is empty — which will be the *majority* case outside food | High |
| No spatial extrapolation. "Prices from users a little further away" has no mechanism: no kernel, no length scale, no locality-similarity metric | High |
| Price answers carry no basis. A client cannot distinguish local consensus from a regional guess | High |
| Client-side price-input validation (order-of-magnitude, total-vs-each, unit mismatch) unspecified | Medium — total-vs-each is the classic silent corruption |
| Taxonomy proposal flow is one endpoint and a sentence (F-09); no state machine, no attribute elicitation | Medium — this *is* decentralized labelling |
| Self-confirmation / anchoring is named in doc 04 and deferred to J-07 | Medium, becomes High once points exist |

→ Addressed by **doc 10** (+ the labelling state machine in doc 09).

#### Use case 3 — civic points and revenue sharing

Absent entirely. No points, ledger, payout, entitlement, metering, or attribution anywhere in the
repository. `analytics.py` returns hardcoded values; `K-04 Partner/analyst API` is a single M-sized
ticket with no design behind it.

Additionally, the requirement **contradicts doc 00's stated anti-features** ("no gamified
leaderboard", "motivated by usefulness and local pride, not points") and doc 04's "reputation is
never displayed". That conflict is real and is resolved explicitly in doc 11 rather than ignored:
two separate ledgers, no leaderboard, coverage as the social object.

The new threat it introduces — **T9, the yield farmer** — is not in doc 04's threat model, and every
naive reward scheme loses to it immediately. Doc 11's answer is to pay for *information gain* rather
than volume or agreement, with escrow and vesting behind consensus.

→ Addressed by **doc 11**.

### Cross-cutting risks worth naming

| Risk | Note |
|---|---|
| **Scope explosion.** Five archetypes at once is a different project from food-only | Land the abstraction now, enable archetypes one at a time behind a flag. `FUNGIBLE_LOOSE` and `PACKAGED_SKU` first — the second is the cheapest, since barcodes do the work |
| **Paying for data before integrity is proven** | Points must not go live until doc 04's Phase 4 gates pass and the red-team suite covers T9. Reversing a payout is far harder than reversing a price |
| **Legal: licence to resell** | The contributor agreement must grant it *before* the first observation. Unfixable retroactively |
| **Reconstruction attacks via the metered API** | New surface created by the revenue product; addressed by the privacy budget in doc 11 |
| **Schema drift already present** | Add Alembic and a CI parity check before adding the category schema |

## Part 2 — Migration

Ordered so that nothing later requires re-doing anything earlier.

### M0 — Stop the drift (before anything else)

1. Add Alembic; baseline the current doc-05 schema as revision 0001.
2. Bring `backend/app/models/*` back to parity with doc 05 — the missing trust columns on
   `observation` above all, since every downstream weight depends on them.
3. CI check: OpenAPI spec ↔ FastAPI routes ↔ doc-05 DDL parity fails the build on divergence.

### M1 — Profile indirection, before any new categories

Purely mechanical, and the whole plan depends on it landing first.

1. Create `category_profile`; seed one row, `fungible_loose`, with today's constants verbatim.
2. `taxonomy_item.category_profile_id`, non-null, all existing rows → `fungible_loose`.
3. Replace `Thresholds` constants with a `ProfileThresholds(profile)` resolver. **Behaviour must be
   bit-identical afterwards** — the existing `ConsensusPolicyTest`, `PriceVerdictPolicyTest` and
   `AggregationTest` are the proof, and must pass unchanged.
4. Ship profiles in the signed catalog bundle; client verifies as it does taxonomy and prototypes.

### M2 — Identity split

1. `product_sku` table; `observation.sku_id` nullable.
2. Move produce-only columns from `taxonomy_item` into profile-scoped `attributes JSONB`.
3. Backfill from Open Food Facts for the launch country (F-07 already exists — widen its scope from
   food to all GTINs).
4. Client: `:core:catalog` SKU cache + FTS over brand/model.

### M3 — Cell generalization

1. Rewrite `price_cell` to the generalized key
   `(scope_type, scope_id, item_ref_type, item_ref_id, unit, condition, channel, period_type,
   period_start)`.
2. Migrate existing rows as `(geohash6, taxonomy_item, new, any, week)`. **Verify published bands
   are unchanged before and after** — a silent shift here would be invisible and would corrupt the
   history the product's credibility rests on.
3. Aggregation and consensus read scope/period/tolerance from the profile.

### M4 — Resolution ladder

1. Implement L0–L2 first (local, pooled, regional). Ship with `basis` in the response from day one,
   even while only three rungs exist — clients must never learn to ignore the field.
2. Fit `pooling_length_km` per profile from mature cells; do not ship a guessed constant.
3. L3–L5 (SKU anchor, substitute, public reference) once `product_sku` has real coverage.
4. Spatial holdout backtest — hold out whole localities, not only later weeks.

### M5 — Router and archetype-conditional pipeline

1. `:ml:route`, soft posterior over archetypes.
2. Stage 6 portion becomes conditional on `profile.portion_estimation`.
3. Per-archetype recognition eval sets, tracked separately in CI.

### M6 — Labelling loop

Proposal state machine, duplicate detection (embedding + name + GTIN), attribute elicitation UI,
merge tooling, proposer-set retention.

### M7 — Points ledger (shadow mode)

Ledger, IG computation, escrow and vesting — **computed and stored but invisible to users**, running
alongside the red-team suite until the numbers are trusted. Gini and reversal-rate metrics live
here before anyone is paid.

### M8 — Partner platform

Entitlements, metering, k-anonymity in the query layer, privacy budget, `api_usage_cell` attribution
log, webhooks.

### M9 — Payouts live

Only after: Phase 4 integrity gates pass, T9 is in the red-team suite, the contributor agreement
grants resale rights, per-country payout legal review is signed, and M7 has run in shadow for a full
quarter.

## Part 3 — New epics

Following the existing convention (**S** ≤ 2 d · **M** 3–5 d · **L** 1–2 wk · **XL** split before
starting). Epics A–K are unchanged.

### EPIC L — Category Generalization

| ID | Title | Size | Depends on |
|---|---|---|---|
| L-00 | Alembic + schema/OpenAPI parity check in CI | M | A-09 |
| L-01 | `category_profile` table, seeding, signed bundle distribution | M | L-00, F-05 |
| L-02 | `ProfileThresholds` resolver replacing `Thresholds` constants | M | L-01 |
| L-03 | `product_sku` identity + observation linkage | L | L-01 |
| L-04 | Produce columns → profile-scoped attributes migration | M | L-03 |
| L-05 | Generalized unit families + locality conversion rework | M | L-02 |
| L-06 | Generalized `price_cell` key + aggregation/consensus rework | L | L-04, L-05 |
| L-07 | `:ml:route` archetype router | L | E-02, L-01 |
| L-08 | Conditional portion estimation | S | L-07, G-04 |
| L-09 | `PACKAGED_SKU` path: GTIN-first resolution, net-content units | L | L-03, E-08 |
| L-10 | `DURABLE_MODEL` path: model-code OCR + attribute axes | L | L-07, E-08 |
| L-11 | `GRADED_MATERIAL` path: grade extraction + trade units | M | L-07, L-05 |
| L-12 | `SECOND_HAND` condition axis | M | L-06 |
| L-13 | Per-archetype eval sets + CI gates | L | L-07, E-12 |
| L-14 | GTIN/catalogue ingestion widened beyond food | M | F-07, L-03 |

### EPIC M — Price Resolution & Extrapolation

| ID | Title | Size | Depends on |
|---|---|---|---|
| M-01 | `PriceAnswer` contract with `basis`, `n_effective`, `verdict_allowed` | M | A-05, G-06 |
| M-02 | Ladder L0–L2 with per-rung band widening | L | M-01, L-06 |
| M-03 | Locality similarity kernel + `α_locality` reuse | L | G-07, M-02 |
| M-04 | Semivariogram length-scale fitting per profile | M | M-03 |
| M-05 | Ladder L3: national SKU anchor + depreciation | M | L-03, M-02 |
| M-06 | Ladder L4–L5: substitutes and public reference | M | M-05, F-02 |
| M-07 | Client-side price input validation suite | M | I-05, L-05 |
| M-08 | Price-tag evidence escalation flow | M | M-07, I-07 |
| M-09 | Blind-entry sampling + anchoring drift monitor | M | I-03, M-02 |
| M-10 | Per-basis calibration feedback loop | M | M-02, G-11 |
| M-11 | Spatial holdout backtest harness | L | M-02, G-11 |

### EPIC N — Civic Points & Data Revenue

| ID | Title | Size | Depends on |
|---|---|---|---|
| N-01 | Contributor agreement: data licence + reward terms | M | K-05 |
| N-02 | `civic_ledger_entry` + balances, append-only, rebuildable | M | H-10 |
| N-03 | Information-gain (LOO) computation per confirmed observation | L | H-09, N-02 |
| N-04 | Escrow, vesting, clawback wired to influence tracing | L | N-03, J-09 |
| N-05 | Collusion-cluster point collapse | M | N-04, H-12 |
| N-06 | Shadow-mode issuance + Gini/reversal monitoring | M | N-04 |
| N-07 | T9 (yield farmer) red-team scenarios | L | N-06, H-11 |
| N-08 | Partner entitlements, tiers, metering | L | K-04 |
| N-09 | `api_usage_cell` attribution log (financial-grade) | M | N-08 |
| N-10 | k-anonymity + privacy budget in the query layer | L | N-08 |
| N-11 | Revenue attribution: usage pool + coverage pool split | L | N-09, N-03 |
| N-12 | Payout rails, thresholds, KYC-at-payout, non-cash option | XL | N-11, N-01 |
| N-13 | Contributor ledger UI + locality coverage map | M | N-06 |
| N-14 | Partner analytics products: series, index, coverage, alerts | L | N-08, M-02 |

### Recommended order

```
L-00 → L-01 → L-02          land the abstraction while the backend is still mocks
L-03 → L-04 → L-05 → L-06   identity and cell grain
        ├─ L-09 (PACKAGED_SKU — cheapest category, barcodes do the work)
        └─ M-01 → M-02 → M-03 → M-04   the ladder, which every category needs
L-07 → L-08 → L-10 → L-11   remaining archetypes, one at a time behind a flag
M-07 → M-08 → M-09          input validation and the anchoring guard
N-01 in parallel from week 1 — legal lead time is the binding constraint
N-02 → N-03 → N-04 → N-06 → N-07   points in shadow, never visible
N-08 → N-09 → N-10 → N-11 → N-12   revenue, then and only then payouts
```

**L-00, L-01 and L-02 should start immediately and in that order.** They are cheap now and
expensive after Phase 3; every ticket in epics L, M and N depends on the profile indirection
existing. **N-01 should also start in week 1** — the contributor agreement cannot be applied
retroactively, and every observation collected without it is unsellable.

### What to cut, extending doc 07's list

In priority order, ahead of the existing cut list:

1. **`SECOND_HAND` (L-12).** A whole market, but it needs condition grading the vision stack cannot
   do reliably. Defer.
2. **Ladder L4, substitute inference (part of M-06).** The least reliable rung; L5 public reference
   covers most of the same cases more defensibly.
3. **`GRADED_MATERIAL` (L-11).** Highest civic value per item, lowest observation frequency from
   consumer users — the buyers are trades, not shoppers. Strong candidate for a partner data feed
   rather than crowdsourcing.
4. **Coverage pool (part of N-11).** Ship usage-attributed payouts first, add the coverage pool once
   revenue is real.

**What cannot be cut:** the category profile indirection (everything else assumes it), `basis` on
every price answer, escrow-and-vesting behind consensus, k-anonymity in the query layer, and the
contributor data licence. Each is load-bearing for either correctness, integrity, or legality, and
none can be added afterwards without a migration over live data or a renegotiation with every
contributor.
