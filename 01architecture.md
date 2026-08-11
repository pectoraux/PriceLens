# 01 — Architecture

> **Extension note.** This document describes the food v1.0 architecture. Three additions extend it
> without restructuring it: the category-profile abstraction and the Stage 2.5 archetype router
> ([doc 09](09categoryarchitecture.md)), the query-time price resolution ladder that replaces the
> bare Stage 7 lookup ([doc 10](10priceresolution.md)), and the civic-points ledger and partner
> platform ([doc 11](11civicpointsandrevenue.md)). New modules and services are folded into the
> tables below and marked *(doc 09/10/11)*.

## Principles

1. **Normalize before you infer.** No model ever sees a raw camera frame. Every frame passes
   through the device profile's geometric, photometric, and scale normalization first. This is
   the single highest-leverage decision in the system and it is why Phase 1 precedes Phase 2.
2. **Evidence fusion, not a monolith.** Label and price each come from several weak signals
   combined with calibrated weights. Any single signal can fail without the answer collapsing.
3. **Abstain loudly.** Every predictor returns a calibrated confidence and every consumer of a
   prediction has an abstention threshold. "I don't know" is a first-class output.
4. **Corrections are evidence, not training data.** A user correction enters a curation
   pipeline. It never directly mutates a model or a published price.
5. **Offline-first.** Capture, inference, and queued submission all work with no network.
6. **Contracts before code.** Module APIs and the OpenAPI schema are written and reviewed
   before implementation. This is what makes an AI coding agent productive instead of chaotic.

## Module graph

```
                                    ┌───────────────┐
                                    │      :app     │
                                    └───────┬───────┘
                    ┌───────────────────────┼───────────────────────┐
                    ▼                       ▼                       ▼
          ┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
          │ :feature:capture │   │ :feature:review  │   │:feature:contribute│
          └────────┬─────────┘   └────────┬─────────┘   └─────────┬────────┘
                   │                      │                        │
                   │            ┌─────────┴────────┐               │
                   │            │ :feature:explore │               │
                   │            └─────────┬────────┘               │
                   └──────────────────────┼────────────────────────┘
                                          ▼
        ┌─────────────────────────── :domain ────────────────────────────┐
        │  use cases · policy · abstention rules · unit normalization    │
        └───┬────────────┬─────────────┬─────────────┬──────────────┬────┘
            ▼            ▼             ▼             ▼              ▼
    ┌───────────┐ ┌────────────┐ ┌──────────┐ ┌────────────┐ ┌───────────┐
    │ :ml:      │ │ :ml:       │ │ :ml:     │ │ :ml:price  │ │ :core:    │
    │ pipeline  │ │ deviceprof │ │ portion  │ │            │ │ trust     │
    └─────┬─────┘ └─────┬──────┘ └────┬─────┘ └─────┬──────┘ └─────┬─────┘
          │             │             │             │              │
    ┌─────┴──────────────────────────────┐          │        ┌─────┴──────┐
    │ :ml:detect :ml:embed :ml:retrieval │          │        │ :core:attest│
    │ :ml:ocr    :ml:fusion              │          │        │ :core:geo   │
    └─────────────────┬──────────────────┘          │        └─────┬──────┘
                      ▼                             ▼              ▼
              ┌──────────────┐            ┌──────────────────────────────┐
              │ :ml:runtime  │            │ :core:data (Room · DataStore │
              │ LiteRT · NNAPI│           │  · Ktor client · WorkManager)│
              │ GPU · XNNPACK │           └──────────────┬───────────────┘
              └──────────────┘                           ▼
                                              ┌──────────────────────┐
                                              │  PriceLens Backend   │
                                              │ FastAPI · Postgres   │
                                              │ PostGIS · Redis · S3 │
                                              └──────────────────────┘
```

### Module responsibilities

| Module | Owns | Must not |
|---|---|---|
| `:app` | DI wiring, navigation host, app lifecycle | Contain business logic |
| `:feature:capture` | Camera UI, capture session, quality coaching | Call ML modules directly (goes via `:domain`) |
| `:feature:review` | Label + price validation UI, correction entry | Decide what "confident" means |
| `:feature:contribute` | Standalone price submission, receipt capture | Bypass trust checks |
| `:feature:explore` | Locality price browsing, history charts | Write data |
| `:domain` | Use cases, abstention policy, unit normalization, models (pure Kotlin) | Depend on Android, Room, or Retrofit |
| `:ml:deviceprofile` | Camera characteristics enumeration, calibration routines, compute benchmarking | Persist to network |
| `:ml:pipeline` | Frame normalization → detect → embed → retrieve → OCR → fuse orchestration | Own model files |
| `:ml:detect` | Open-vocabulary / class-agnostic proposal generation | Classify |
| `:ml:embed` | Image and text embedding | Know about food |
| `:ml:retrieval` | ANN index over taxonomy prototypes, kNN | Fuse |
| `:ml:ocr` | ML Kit text + barcode, GTIN resolution | Interpret prices |
| `:ml:fusion` | Calibrated late fusion, confidence, abstention | Run models |
| `:ml:portion` | Scale estimation, size → mass via density LUT | Guess when unconfident |
| `:ml:price` | Price band inference (server-first, on-device fallback) | Display anything |
| `:ml:runtime` | Delegate selection, numerical parity checks, model registry, thermal policy | Contain task logic |
| `:core:trust` | Provenance sealing, submission signing, integrity verdict cache | Make policy decisions alone |
| `:core:attest` | Play Integrity, hardware key attestation, StrongBox key management | Be bypassable in release builds |
| `:core:geo` | Locality resolution, geohash coarsening, mock-location detection | Persist fine coordinates |
| `:core:data` | Room DB, DataStore, Ktor client, WorkManager sync | Contain policy |
| `:core:designsystem` | Compose theme, components, uncertainty visualization primitives | Depend on features |
| `:core:common` | Result types, dispatchers, logging, test fixtures | Depend on anything |
| `:ml:route` *(doc 09)* | Archetype posterior from cheap frame signals, before the evidence stages | Resolve identity itself |
| `:domain:category` *(doc 09)* | Category profile resolution, unit families, per-archetype threshold lookup | Depend on Android |
| `:core:catalog` *(doc 09)* | SKU cache, attribute schemas, proposal drafts, profile bundle verification | Contain policy |

**Dependency rule, enforced in CI:** dependencies point downward only. `:domain` is pure Kotlin
with zero Android dependencies. Features never depend on each other. Ticket A-04 adds the
Konsist/Lint check that fails the build on violation.

## The inference pipeline

```
 CameraX ImageAnalysis (YUV_420_888)
        │
        ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ STAGE 0 · QUALITY GATE                        :ml:pipeline   │
 │ Laplacian variance (blur) · exposure histogram · motion from │
 │ gyro · AF lock state. Below threshold → coach, don't infer.  │
 └────────┬─────────────────────────────────────────────────────┘
          ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ STAGE 1 · CANONICAL NORMALIZATION          :ml:deviceprofile │
 │ a. Undistort with LENS_DISTORTION (Brown–Conrady)            │
 │ b. Reproject to canonical intrinsics (fixed 55° hFOV, fixed  │
 │    angular resolution) — a tele phone and an ultrawide now   │
 │    present the same object at the same apparent scale        │
 │ c. Photometric: raw/YUV → linear → device CCM → sRGB-D65     │
 │    canonical, illuminant-corrected                           │
 │ d. Noise-aware denoise strength from the per-ISO SNR curve   │
 │ OUTPUT: CanonicalFrame + FrameQualityMetrics                 │
 └────────┬─────────────────────────────────────────────────────┘
          ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ STAGE 2 · DETECTION                              :ml:detect  │
 │ Class-agnostic + open-vocab proposals. Pick dominant region  │
 │ by area × centrality × objectness. Person/face detector runs │
 │ here too; frames with people are redacted before any upload. │
 └────────┬─────────────────────────────────────────────────────┘
          ▼
 ┌────────────────┬────────────────────┬────────────────────────┐
 │ STAGE 3a       │ STAGE 3b           │ STAGE 3c               │
 │ EMBED          │ OCR + BARCODE      │ CONTEXT PRIORS         │
 │ :ml:embed      │ :ml:ocr            │ :domain                │
 │ 512-d L2-norm  │ ML Kit text +      │ locality catalogue     │
 │ image vector   │ GTIN → SKU lookup  │ seasonality · user     │
 │                │ on-pack net weight │ history · time of day  │
 └────────┬───────┴─────────┬──────────┴──────────┬─────────────┘
          ▼                 │                     │
 ┌────────────────────┐     │                     │
 │ STAGE 4 · RETRIEVE │     │                     │
 │ :ml:retrieval      │     │                     │
 │ (i) zero-shot: cos │     │                     │
 │     vs taxonomy    │     │                     │
 │     text embeds    │     │                     │
 │ (ii) few-shot: kNN │     │                     │
 │     vs validated   │     │                     │
 │     prototype      │     │                     │
 │     centroids      │     │                     │
 └────────┬───────────┘     │                     │
          ▼                 ▼                     ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ STAGE 5 · FUSION                                 :ml:fusion  │
 │ Calibrated late fusion of all evidence → posterior over      │
 │ taxonomy. Temperature + Dirichlet calibrated. Quality metrics │
 │ from Stage 0/1 widen the posterior in bad conditions.        │
 │ OUTPUT: top-k labels with calibrated p, or ABSTAIN           │
 └────────┬─────────────────────────────────────────────────────┘
          ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ STAGE 6 · PORTION                               :ml:portion  │
 │ mm-per-pixel from device intrinsics + distance (ARCore Depth,│
 │ AF distance, or reference object). Volume → mass via per-class│
 │ density LUT. Low confidence → ask the user for the unit.     │
 └────────┬─────────────────────────────────────────────────────┘
          ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ STAGE 7 · PRICE                                   :ml:price  │
 │ (label, unit, quantity, locality, timestamp) → server        │
 │ hierarchical model → {p10, p50, p90, n_obs, freshness}       │
 │ Offline → cached locality band with a staleness penalty.     │
 └────────┬─────────────────────────────────────────────────────┘
          ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ STAGE 8 · VALIDATION UX                    :feature:review   │
 │ Show label + band + honest uncertainty. User confirms or     │
 │ corrects either. Correction → sealed observation → sync queue│
 └──────────────────────────────────────────────────────────────┘
```

**Latency budget (mid-tier reference device, p95):**

| Stage | Budget |
|---|---|
| 0 Quality gate | 8 ms/frame (runs continuously) |
| 1 Normalization | 25 ms |
| 2 Detection | 90 ms |
| 3a Embedding | 110 ms |
| 3b OCR/barcode | 120 ms (parallel with 3a) |
| 4 Retrieval | 15 ms |
| 5 Fusion | 5 ms |
| 6 Portion | 40 ms |
| 7 Price (cached) | 20 ms |
| **Total on-device** | **≤ 450 ms** |
| 7 Price (network) | +250 ms budget, non-blocking — band streams in after the label |

The label is shown as soon as Stage 5 completes. The price band arrives after. Never make the
user wait on the network to learn what they are looking at.

Two amendments from the multi-category work: **Stage 2.5**, the archetype router, sits between
detection and the evidence stages and selects which Stage-3 branches run (a resolved barcode
short-circuits embedding and retrieval, freeing ~125 ms), and **Stage 6 becomes conditional** —
portion estimation is skipped entirely for archetypes whose net content is printed on the pack.
**Stage 7 is no longer a cell lookup** but the resolution ladder of [doc 10](10priceresolution.md),
which returns the `basis` of its answer alongside the band. See [doc 09](09categoryarchitecture.md).

## Technology choices

| Layer | Choice | Rationale |
|---|---|---|
| Language | Kotlin 2.x, JDK 17 | Non-negotiable for modern Android tooling |
| UI | Jetpack Compose + Material 3 | Agent-friendly, declarative, testable |
| Camera | CameraX for session management, Camera2 interop for characteristics | CameraX handles device quirks; Camera2 is the only way to reach `LENS_INTRINSIC_CALIBRATION` and friends |
| On-device inference | **LiteRT** (formerly TF Lite) + Play Services delegates | Broadest device coverage; NNAPI/GPU/XNNPACK fallback chain is the point |
| Model delivery | Play Feature Delivery (on-demand) + signed model registry | Models are 40–90 MB; do not ship in base APK |
| Text/barcode | ML Kit (bundled or Play Services) | Best-in-class, free, on-device |
| Depth | ARCore Depth API where available, AF distance fallback | Optional signal; must degrade cleanly |
| Vector search | On-device HNSW over quantized prototypes | Retrieval must work offline |
| Local DB | Room + SQLite FTS + a BLOB vector table | Standard, agent-legible |
| Prefs/state | DataStore Proto | Typed device profile persistence |
| Background | WorkManager | Sync, model download, calibration refresh |
| Networking | Ktor client + kotlinx.serialization | Multiplatform-ready if iOS follows |
| DI | Hilt | Best agent support of the Android DI options |
| Backend | FastAPI (Python 3.12) | ML ecosystem proximity for the price model |
| DB | Postgres 16 + PostGIS + TimescaleDB | Geo + time-series price data, exactly the shape of the problem |
| Cache/queue | Redis + Celery | Consensus recomputation is async |
| Blob | S3-compatible with lifecycle expiry | Evidence photos, expiring by retention policy |
| Attestation | Play Integrity API + Android Key Attestation | The only credible device-integrity primitives on Android |

**Why LiteRT over MediaPipe or ONNX Runtime:** MediaPipe's graph abstraction fights the custom
normalization stage; ONNX Runtime Mobile has weaker NNAPI coverage on mid-tier chipsets. LiteRT
gives direct control over the delegate chain, which Phase 1 depends on. Revisit at [E-02](../tickets/02-phase-2-3-tickets.md).

## Data flow: an observation's life

```
1. CAPTURE   User captures. Frame sealed on-device: canonical frame hash, device
             profile ID, capture nonce, sensor timestamp, gyro/accel window,
             coarse geohash-6, AF/AE/AWB state. Signed with the StrongBox-backed
             install key. → local Room, status=DRAFT

2. PREDICT   Pipeline produces label posterior + price band. Shown to user.

3. VALIDATE  User confirms or corrects. Both the prediction and the user's answer
             are recorded — the disagreement is the signal.
             → status=PENDING_SYNC

4. SEAL      :core:trust attaches the Play Integrity verdict token, the key
             attestation chain, and the geo-integrity assessment.

5. SYNC      WorkManager uploads on unmetered network (or immediately if the user
             taps "send now"). Server verifies signature, attestation, nonce
             freshness, and geo plausibility. → status=SUBMITTED or REJECTED

6. SCORE     Server computes a trust weight: reputation × attestation tier ×
             provenance tier × geo confidence × device-profile reliability.

7. CONSENSUS Async job re-aggregates the (locality, item, unit, week) cell with
             reputation-weighted robust statistics. Change-point detector decides
             whether a shift is a regime change or an attack.
             → observation becomes CONFIRMED, DISPUTED, or OUTLIER

8. PUBLISH   Cell's band is republished only if it clears the maturity threshold.
             Clients pull deltas.

9. CURATE    Label corrections that reach CONFIRMED enter the model curation
             queue: dedupe, influence-cap per contributor, robust centroid
             update, golden-set regression, canary rollout. See doc 04.
```

## Backend service decomposition

| Service | Responsibility |
|---|---|
| `api` | FastAPI. Auth, submission intake, price queries, catalog sync |
| `verifier` | Attestation, signature, nonce, and geo-plausibility verification. Synchronous, on the intake path |
| `consensus` | Celery workers. Robust re-aggregation, outlier and collusion detection, change-point detection |
| `ingest` | Scheduled public-source connectors (see doc 04). Normalizes external price series into the same cell schema at a lower trust tier |
| `curator` | Model-update candidate pipeline. Builds prototype deltas, runs golden-set regression, manages canary |
| `registry` | Signed model + taxonomy + calibration artifact distribution to clients |
| `resolver` *(doc 10)* | Query-time price resolution ladder: local → pooled → regional → SKU anchor → substitute → public. Owns `basis` and `verdict_allowed` |
| `ledger` *(doc 11)* | Civic points: information-gain scoring, escrow, vesting, clawback. Append-only, rebuildable from observations |
| `partner` *(doc 11)* | Entitlements, metering, k-anonymity and privacy budget enforcement, usage attribution log |

## Build and release configuration

- **Flavors:** `dev` (local backend, attestation stubbed, debug overlays), `staging` (staging
  backend, real attestation, shadow-mode ML), `prod`.
- **Attestation must not be stubbable in `prod`.** Ticket H-02 adds a build-time assertion that
  fails the release build if the stub implementation is on the `prod` classpath.
- **ABI splits + Play Feature Delivery** for models. Base APK target ≤ 25 MB.
- **Baseline Profiles** generated in CI for the capture→predict path.
- **Minimum SDK 26** (Camera2 full characteristics, StrongBox from 28 with graceful fallback).
  Target the current SDK.
