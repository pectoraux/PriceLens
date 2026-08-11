# 05 — Data Model & API

> Contracts first. These schemas are frozen before implementation begins so the Android agent and
> the backend can be built in parallel without integration surprises.

## Core concepts

| Concept | Definition |
|---|---|
| **Taxonomy item** | A food identity (e.g. "Roma tomato"). Hierarchical. Carries text embeddings and per-locale names |
| **Locality** | A geohash-6 cell plus human metadata (name, currency, unit system, market type) |
| **Observation** | One user-submitted fact: this item, this price, this unit, this locality, this time |
| **Cell** | The aggregate: `(locality, item, unit, week)` → price band |
| **Prototype** | A centroid embedding representing one visual mode of a taxonomy item |

## Server schema (Postgres 16 + PostGIS + TimescaleDB)

### `taxonomy_item`

```sql
CREATE TABLE taxonomy_item (
  id                BIGSERIAL PRIMARY KEY,
  slug              TEXT UNIQUE NOT NULL,          -- 'tomato.roma'
  parent_id         BIGINT REFERENCES taxonomy_item(id),
  canonical_name    TEXT NOT NULL,                 -- English
  scientific_name   TEXT,
  category          TEXT NOT NULL,                 -- produce|grain|dairy|meat|packaged|...
  default_unit      TEXT NOT NULL,                 -- kg|L|piece|bunch
  density_kg_per_l  NUMERIC(6,3),                  -- for portion → mass
  shape_model       TEXT,                          -- spheroid|cylinder|pile|flat|packaged
  seasonality       JSONB,                         -- {region: [month weights]}
  is_perishable     BOOLEAN NOT NULL DEFAULT TRUE,
  status            TEXT NOT NULL DEFAULT 'active',-- active|proposed|merged|retired
  merged_into_id    BIGINT REFERENCES taxonomy_item(id),
  version           INT NOT NULL DEFAULT 1,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE taxonomy_item_name (
  item_id     BIGINT NOT NULL REFERENCES taxonomy_item(id),
  locale      TEXT NOT NULL,                       -- 'sw-KE'
  name        TEXT NOT NULL,
  is_primary  BOOLEAN NOT NULL DEFAULT FALSE,
  is_vernacular BOOLEAN NOT NULL DEFAULT FALSE,    -- what it's actually called at the stall
  PRIMARY KEY (item_id, locale, name)
);

CREATE TABLE taxonomy_item_embedding (
  item_id        BIGINT PRIMARY KEY REFERENCES taxonomy_item(id),
  text_embedding BYTEA NOT NULL,                   -- 512 × fp16, prompt-ensembled
  model_version  TEXT NOT NULL
);
```

`merged_into_id` matters more than it looks: the taxonomy will accumulate duplicates as users
propose items ("courgette" and "zucchini"), and merging must preserve historical observations
rather than orphan them.

### `locality`

```sql
CREATE TABLE locality (
  id             BIGSERIAL PRIMARY KEY,
  geohash6       CHAR(6) UNIQUE NOT NULL,
  centroid       GEOGRAPHY(POINT, 4326) NOT NULL,
  admin_name     TEXT,
  country_code   CHAR(2) NOT NULL,
  currency_code  CHAR(3) NOT NULL,                 -- ISO 4217
  unit_system    TEXT NOT NULL DEFAULT 'metric',
  market_type    TEXT,                             -- informal|market|supermarket|mixed
  maturity       TEXT NOT NULL DEFAULT 'seeding',  -- seeding|learning|mature
  parent_region_id BIGINT REFERENCES region(id),   -- for hierarchical pooling
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON locality USING GIST (centroid);

CREATE TABLE locality_unit_conversion (             -- 'a tin' is not universal
  locality_id     BIGINT NOT NULL REFERENCES locality(id),
  item_category   TEXT NOT NULL,
  vernacular_unit TEXT NOT NULL,
  canonical_unit  TEXT NOT NULL,
  factor          NUMERIC(10,4) NOT NULL,
  confidence      TEXT NOT NULL,                   -- measured|estimated|assumed
  PRIMARY KEY (locality_id, item_category, vernacular_unit)
);
```

### `observation`

```sql
CREATE TABLE observation (
  id                  UUID PRIMARY KEY,
  contributor_id      UUID NOT NULL REFERENCES contributor(id),
  item_id             BIGINT NOT NULL REFERENCES taxonomy_item(id),
  locality_id         BIGINT NOT NULL REFERENCES locality(id),

  price_minor         BIGINT NOT NULL,             -- integer minor units. never float
  currency_code       CHAR(3) NOT NULL,
  quantity            NUMERIC(10,3) NOT NULL,
  unit                TEXT NOT NULL,
  price_per_canonical NUMERIC(14,4),               -- normalized, NULL if unconvertible
  canonical_unit      TEXT,

  observed_at         TIMESTAMPTZ NOT NULL,        -- SERVER time. never client
  submitted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- what the model said, before the user answered. the disagreement is the signal
  predicted_item_id   BIGINT REFERENCES taxonomy_item(id),
  predicted_confidence REAL,
  predicted_p50_minor BIGINT,
  label_was_corrected BOOLEAN NOT NULL,
  price_was_corrected BOOLEAN NOT NULL,
  abstained           BOOLEAN NOT NULL DEFAULT FALSE,

  -- trust
  attestation_tier    TEXT NOT NULL,               -- STRONG|STANDARD|BASIC|UNVERIFIED
  provenance_tier     TEXT NOT NULL,               -- LIVE_CAPTURE|RECEIPT_OCR|IMPORTED|NO_IMAGE
  geo_confidence      REAL NOT NULL,
  synthetic_score     REAL,
  device_class_id     TEXT NOT NULL,
  trust_weight        REAL,                        -- computed by scorer, NULL until scored

  status              TEXT NOT NULL DEFAULT 'pending', -- pending|confirmed|disputed|outlier|rejected
  evidence_key        TEXT,                        -- S3 key, NULL after retention expiry
  evidence_phash      BIGINT,                      -- dedupe
  embedding           BYTEA,                       -- 512 × int8, for prototype building
  capture_seal        JSONB NOT NULL,              -- full signed seal, for audit
  model_versions      JSONB NOT NULL
);

SELECT create_hypertable('observation', 'observed_at');
CREATE INDEX ON observation (locality_id, item_id, observed_at DESC);
CREATE INDEX ON observation (contributor_id, submitted_at DESC);
CREATE INDEX ON observation (status) WHERE status = 'pending';
```

Storing `predicted_*` alongside the user's answer is what makes the whole learning loop
measurable. Without it there is no way to compute real-world accuracy — only offline eval, which
always flatters.

### `price_cell`

```sql
CREATE TABLE price_cell (
  locality_id     BIGINT NOT NULL REFERENCES locality(id),
  item_id         BIGINT NOT NULL REFERENCES taxonomy_item(id),
  unit            TEXT NOT NULL,
  week_start      DATE NOT NULL,

  p10_minor       BIGINT NOT NULL,
  p50_minor       BIGINT NOT NULL,
  p90_minor       BIGINT NOT NULL,
  currency_code   CHAR(3) NOT NULL,

  n_observations  INT NOT NULL,
  n_contributors  INT NOT NULL,
  total_weight    REAL NOT NULL,
  source_mix      JSONB NOT NULL,                  -- {user_confirmed, public, receipt_ocr}
  confidence      TEXT NOT NULL,                   -- HIGH|MEDIUM|LOW|INSUFFICIENT
  is_published    BOOLEAN NOT NULL DEFAULT FALSE,  -- maturity gate
  regime_shift_at TIMESTAMPTZ,                     -- last accepted change point
  computed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

  PRIMARY KEY (locality_id, item_id, unit, week_start)
);
```

### `contributor`, `prototype`, `public_price`

```sql
CREATE TABLE contributor (
  id                  UUID PRIMARY KEY,
  phone_hash          BYTEA UNIQUE NOT NULL,       -- salted hash. never the number
  attested_key_pub    BYTEA NOT NULL,
  key_attestation_chain BYTEA NOT NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  probation_until     TIMESTAMPTZ NOT NULL,
  status              TEXT NOT NULL DEFAULT 'active', -- active|limited|blocked
  collusion_cluster_id BIGINT
);

CREATE TABLE contributor_reputation (
  contributor_id  UUID NOT NULL REFERENCES contributor(id),
  item_category   TEXT NOT NULL,
  alpha           REAL NOT NULL DEFAULT 0,
  beta            REAL NOT NULL DEFAULT 0,
  score_lcb       REAL NOT NULL DEFAULT 0.1,       -- lower confidence bound. what's actually used
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (contributor_id, item_category)
);

CREATE TABLE prototype (
  id                BIGSERIAL PRIMARY KEY,
  item_id           BIGINT NOT NULL REFERENCES taxonomy_item(id),
  centroid          BYTEA NOT NULL,                -- 512 × int8
  n_members         INT NOT NULL,
  contributor_ids   UUID[] NOT NULL,               -- influence tracing. required for reversibility
  region_id         BIGINT REFERENCES region(id),  -- regional visual variation is real
  model_version     TEXT NOT NULL,
  built_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE public_price (
  id            BIGSERIAL PRIMARY KEY,
  source        TEXT NOT NULL,                     -- wfp_vam|fao_fpma|agmarknet|...
  source_ref    TEXT NOT NULL,
  item_id       BIGINT REFERENCES taxonomy_item(id),
  locality_id   BIGINT REFERENCES locality(id),
  price_minor   BIGINT NOT NULL,
  currency_code CHAR(3) NOT NULL,
  unit          TEXT NOT NULL,
  period_start  DATE NOT NULL,
  ingested_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (source, source_ref, item_id, locality_id, period_start)
);
```

### `device_profile_stat` (anonymized fleet telemetry)

```sql
CREATE TABLE device_profile_stat (
  device_class_id     TEXT PRIMARY KEY,
  n_devices           INT NOT NULL,
  agreement_rate      REAL,                        -- vs consensus
  mean_abstention     REAL,
  reliability_weight  REAL NOT NULL DEFAULT 1.0,   -- floored at 0.6. see doc 02
  degradation_mix     JSONB,
  correction_delta    BYTEA,                       -- pushed back to fix the class, not punish it
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## On-device schema (Room)

```kotlin
@Entity(tableName = "observation_draft")
data class ObservationDraftEntity(
    @PrimaryKey val id: String,
    val itemSlug: String?, val predictedItemSlug: String?, val predictedConfidence: Float?,
    val priceMinor: Long?, val currencyCode: String, val quantity: Double, val unit: String,
    val geohash6: String, val capturedAtMs: Long,
    val labelWasCorrected: Boolean, val priceWasCorrected: Boolean, val abstained: Boolean,
    val evidencePath: String?, val embedding: ByteArray?,
    val captureSealJson: String, val signature: ByteArray,
    val syncState: SyncState,        // DRAFT | PENDING | SYNCING | SYNCED | REJECTED
    val syncAttempts: Int, val lastError: String?
)

@Entity(tableName = "price_cell_cache")   // offline price answers
data class PriceCellCacheEntity(
    @PrimaryKey val key: String,          // "$geohash6|$itemSlug|$unit"
    val p10Minor: Long, val p50Minor: Long, val p90Minor: Long,
    val currencyCode: String, val nObservations: Int,
    val confidence: String, val computedAtMs: Long
)

@Entity(tableName = "taxonomy_item_cache")
data class TaxonomyItemCacheEntity(
    @PrimaryKey val slug: String,
    val displayName: String, val vernacularNames: String, val category: String,
    val defaultUnit: String, val densityKgPerL: Double?, val shapeModel: String?,
    val textEmbedding: ByteArray
)

@Entity(tableName = "prototype_cache")
data class PrototypeCacheEntity(
    @PrimaryKey val id: Long,
    val itemSlug: String, val centroid: ByteArray, val nMembers: Int
)
```

Plus an FTS4 table over `taxonomy_item_cache` display and vernacular names, because the manual
item picker must find "courgette" when the catalog says "zucchini" and the user is typing in
French.

## API contract (OpenAPI 3.1, `api/openapi.yaml`)

All endpoints under `/v1`. Auth: bearer token for contributor endpoints; anonymous read for
price queries with per-IP rate limiting.

### Prediction and price

```
POST /v1/price/estimate
  body: { itemSlug, unit, quantity, geohash6, currencyCode, observedAt? }
  200:  PriceBand { p10Minor, p50Minor, p90Minor, currencyCode, unit,
                    nObservations, nContributors, freshnessDays,
                    confidence: HIGH|MEDIUM|LOW|INSUFFICIENT,
                    sourceMix, verdictThresholds }
  Returns confidence=INSUFFICIENT (not an error) when the cell is immature.

GET  /v1/price/cells?geohash6=&itemSlugs=&since=
  Bulk fetch for offline cache warming. Delta-capable via `since`.

GET  /v1/price/history?geohash6=&itemSlug=&unit=&weeks=26
  Published cells only.
```

### Submission

```
POST /v1/capture/nonce
  200: { nonce, expiresAt }            single-use, 10-minute TTL

POST /v1/observations
  headers: X-Integrity-Token (Play Integrity), X-Signature (attested key)
  body: ObservationSubmission {
          clientId, nonce, itemSlug, priceMinor, currencyCode, quantity, unit,
          geohash6, geoConfidence, captureSeal, deviceClassId, modelVersions,
          predictedItemSlug, predictedConfidence, predictedP50Minor,
          labelWasCorrected, priceWasCorrected, abstained,
          evidenceUploadRequested: bool }
  202: { observationId, evidenceUploadUrl?, status: "accepted" }
  409: nonce replayed
  422: integrity verification failed  — response body deliberately non-specific

PUT  {evidenceUploadUrl}               presigned S3 PUT, hash-bound

GET  /v1/observations/mine?cursor=
  The user's own history and published status. Transparency requirement.
```

### Catalog and models

```
GET  /v1/catalog/manifest?locale=&geohash6=&since=
  200: { taxonomyVersion, prototypeIndexVersion, calibrationVersion,
         bundles: [{ kind, url, sha256, sizeBytes, signature }] }

GET  /v1/models/manifest?deviceClassId=&abi=
  200: { models: [{ name, version, url, sha256, signature,
                    goldenInputsUrl, expectedOutputsUrl,   ← delegate parity check
                    minRamMb, recommendedDelegate }] }

POST /v1/device-profiles
  body: anonymized DeviceProfile (no profile_id)
  204
```

**Every downloaded artifact is signed** and the client verifies the signature against a pinned
public key before use. A model or index swapped in transit is a total compromise of both label
and price outputs.

### Taxonomy proposals

```
POST /v1/taxonomy/proposals
  body: { proposedName, locale, parentSlugGuess, evidenceObservationIds }
  202: { proposalId, status: "queued" }
```
Users encountering an item outside the taxonomy can propose it. Proposals require multiple
independent submissions before reaching human review — otherwise the taxonomy becomes a spam
vector.

## Sync protocol

- **Push:** WorkManager, `NetworkType.CONNECTED`, exponential backoff, batches of ≤ 10.
  Idempotent on client-generated UUID. Nonces are fetched at capture time, not sync time, so an
  offline queue must handle expired nonces — expired-nonce submissions are re-signed with a
  fresh nonce and marked `provenance_tier` unchanged but `offline_deferred = true` (the seal's
  sensor timestamp and IMU window still prove the capture was real; the nonce only proves
  freshness, which we knowingly relax for offline users rather than discarding their data).
- **Pull:** delta sync on catalog, prototypes, and price cells, keyed by version. Full refresh
  when the major version changes.
- **Conflict:** server wins for catalog and price data; client wins for the user's own drafts.
- **Retention:** evidence images expire after 180 days (configurable per region for legal
  compliance); embeddings and observation records are retained. The dedupe `phash` survives image
  expiry so replay detection outlives the image itself.
