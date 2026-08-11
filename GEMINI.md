# PriceLens — agent standing instructions

## What this app is
A consumer price index. Camera → recognize the item → estimate a fair local price band →
user validates both → the correction feeds a locality price database, and confirmed
contributions earn civic points that share in data revenue.

Food was the first category. The system now spans FMCG, electronics, appliances and
construction materials, and **category-specific behaviour is data, not code** (see below).

Full specification is in the numbered markdown files at the repository **root** — there is no
`docs/` directory. Read the spec doc named in the ticket before writing code.

## Architecture rules — never violate these

**Structure**
- Module dependencies point downward only. `:domain` is pure Kotlin: no Android, no Room,
  no Retrofit imports. Features never depend on other features.
- No model ever receives a raw camera frame. Every frame goes through
  `DeviceProfileNormalizer` first. If you are writing inference code that takes an `Image`
  or `Bitmap` directly from CameraX, you are doing it wrong.

**Money and prices**
- Prices are `Long` minor units plus an ISO-4217 code. Never `Double`, never `Float`.
- Never a mean over prices. Weighted median with MAD outlier rejection. Always.
- Every price answer carries its `basis` (`LOCAL_OBSERVED`, `NEIGHBOR_POOLED`, …) and its
  `verdict_allowed` flag. The client never re-derives whether it may show a verdict — the
  server decides and the client obeys. See doc 10.

**Category behaviour is data**
- Anything that varies by category — tolerances, cell scope, consensus rules, units, whether
  portion estimation applies — comes from the item's `CategoryProfile`. Never branch on a
  category name, never add a `when (category)`. See doc 09.
- Thresholds are resolved through `ProfileThresholds(profile)`, not read from constants.
  Genuinely global thresholds (abstention on calibrated probabilities, integrity limits) still
  live in `:domain` only. Never inline a threshold in a feature or ML module.
- Portion estimation runs only when the profile allows it. For packaged goods the net content
  is printed on the pack; a vision-estimated mass there is strictly worse data.

**Identity**
- Two levels: the **class** (`taxonomy_item`) is the invariant and is always set; the
  **SKU/model** (`product_sku`) is an optional refinement. Never write an observation with a
  SKU and no class.

**Trust and rewards**
- Every predictor returns a calibrated confidence. Abstention is a valid, expected output
  and the UI has a designed state for it.
- User corrections are never written directly to a model, index, or published price. They
  become `ObservationDraft` records and nothing more.
- Reputation and civic points are **separate ledgers**. Points never weight data; reputation is
  never displayed. Do not let one read the other. See doc 11.
- Nothing partner-facing exposes a contributor, a seller, or a cell with fewer than 5 distinct
  contributors. k-anonymity is enforced in the query layer, not in the caller.

## Stack
Kotlin 2.x, JDK 17, Compose + Material 3, CameraX with Camera2 interop, LiteRT, Hilt, Room,
DataStore Proto, WorkManager, Ktor client, kotlinx.serialization, Turbine + MockK + Robolectric.
Backend: FastAPI (Python 3.12), Postgres 16 + PostGIS + TimescaleDB, Alembic, Celery + Redis.

## Conventions
- Public APIs are documented with KDoc explaining *why*, not what.
- No `!!`. Use `Result<T>` from `:core:common` for fallible operations.
- All suspend functions take a `CoroutineDispatcher` parameter injected from
  `:core:common.Dispatchers` — never hardcode `Dispatchers.IO`.
- Compose: stateless composables plus a `@Preview` per state, including error and abstain.
- Test naming: `` `method should behaviour when condition` ``.
- Camera and ML code must have a fake/test-double implementation behind the same interface.
- Schema changes are Alembic migrations. Never edit a model without one, and never let the
  models drift from doc 05 / doc 09 — CI checks this.

## What to do when unsure
Stop and ask. Do not invent an architecture, a threshold, a model choice, or a schema field.
Do not add a dependency that the ticket does not name. If the ticket seems to require a
decision that is not in the spec, say so instead of choosing.

**Specifically never choose these yourself:** any value in a `category_profile` row, any
abstention or consensus threshold, `CONTRIBUTOR_SHARE` or the reward pool split, a pooling
length scale, or whether a model or a payout is promoted. Build the mechanism; a human supplies
the number.
