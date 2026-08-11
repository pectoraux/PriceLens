# Ticket Index

107 tickets across 11 epics (A–K) for the food v1.0, plus 39 tickets across 3 epics (L, M, N) for
the multi-category index and the civic-points/revenue layer. Sizes: **S** ≤ 2 days · **M** 3–5 days ·
**L** 1–2 weeks · **XL** > 2 weeks (should be split before starting).

Full ticket detail:
- [Phase 0–1 tickets](01phase01tickets.md) — Epics A, B, C, D
- [Phase 2–3 tickets](02phase23tickets.md) — Epics E, F, G, I
- [Phase 4–6 tickets](03phase46tickets.md) — Epics H, J, K
- [Audit & migration](12auditandmigration.md) — Epics L, M, N, with the migration order

Epics L–N extend the system beyond food. They depend on the category-profile indirection (L-01,
L-02), which is cheap to land while the backend is still mocks and expensive afterwards — see
[doc 12](12auditandmigration.md) for why L-00…L-02 and N-01 should start immediately.

---

## EPIC A — Foundations & Platform (Phase 0)

| ID | Title | Size | Depends on |
|---|---|---|---|
| A-01 | Gradle multi-module skeleton | M | — |
| A-02 | Hilt DI, app shell, navigation host | S | A-01 |
| A-03 | CI pipeline: build, unit test, Test Lab, lint | M | A-01 |
| A-04 | Architecture rule enforcement | M | A-01 |
| A-05 | OpenAPI contract + client/server codegen | L | — |
| A-06 | Design system + uncertainty display primitives | M | A-01 |
| A-07 | `:core:common` — Result, dispatchers, logging, fixtures | S | A-01 |
| A-08 | `:core:data` — Room, DataStore, Ktor scaffolding | M | A-01, A-05 |
| A-09 | Backend skeleton: FastAPI + Postgres migrations | L | A-05 |
| A-10 | Build flavors, signing, Play Feature Delivery scaffolding | M | A-01 |

## EPIC B — ML Runtime & Model Delivery (Phase 1)

| ID | Title | Size | Depends on |
|---|---|---|---|
| B-01 | LiteRT integration + interpreter lifecycle | M | A-01 |
| B-02 | Delegate abstraction layer | M | B-01 |
| B-03 | Model registry client + signature verification | M | A-05, A-08 |
| B-04 | On-demand model download | M | B-03, A-10 |
| B-05 | Golden-tensor delegate parity harness | L | B-02, B-03 |
| B-06 | Model export toolchain | L | — |
| B-07 | Thermal & power policy | M | B-02 |
| B-08 | Inference telemetry | S | B-01 |

## EPIC C — Camera & Capture (Phase 1)

| ID | Title | Size | Depends on |
|---|---|---|---|
| C-01 | CameraX session + Camera2 interop bridge | M | A-01 |
| C-02 | Physical camera selection & pinning | M | C-01 |
| C-03 | ISP control: AWB/AE lock, linear tonemap, NR/edge off | M | C-02 |
| C-04 | Frame quality gate | M | C-01, C-06 |
| C-05 | Capture coaching UI | M | C-04, A-06 |
| C-06 | Sensor suite manager | S | A-01 |
| C-07 | Evidence capture + storage with redaction hook | M | C-01 |
| C-08 | Camera permissions + graceful degradation | S | C-01 |

## EPIC D — Device Profiling & Calibration (Phase 1) ★ critical path

| ID | Title | Size | Depends on |
|---|---|---|---|
| D-01 | CameraCharacteristics enumeration + derived intrinsics | L | C-01, C-02 |
| D-02 | Brown–Conrady distortion model | M | D-01 |
| D-03 | **Canonical camera space reprojection** ★ | L | D-01, D-02 |
| D-04 | Passive photometric profile extraction | M | D-01 |
| D-05 | Photometric normalization pipeline | L | D-04, D-03 |
| D-06 | Guided white-sheet calibration UX + solver | L | D-05, A-06 |
| D-07 | Statistical color constancy fallback | M | D-05 |
| D-08 | Noise & MTF profiling | M | C-01, D-01 |
| D-09 | Device fleet test kit + cross-device spread harness | L | D-03, D-05 |
| D-10 | DeviceProfile persistence, invalidation, upload | M | D-01…D-08, A-08 |

## EPIC E — Recognition (Phase 2)

| ID | Title | Size | Depends on |
|---|---|---|---|
| E-01 | `:ml:pipeline` orchestration + stage contracts | M | D-03, B-01 |
| E-02 | Detector selection, export, integration | L | B-06, E-01 |
| E-03 | Embedding model selection, export, integration | L | B-06, E-01, B-05 |
| E-04 | Person/face detection + redaction | M | E-02, C-07 |
| E-05 | Dominant region selection | S | E-02, D-08 |
| E-06 | On-device HNSW prototype index | L | E-03, F-05 |
| E-07 | Zero-shot text similarity path | M | E-03, F-05 |
| E-08 | OCR, barcode, GTIN resolution | M | E-01, F-07 |
| E-09 | Fusion + confidence calibration | L | E-06, E-07, E-08 |
| E-10 | Abstention policy in `:domain` | S | E-09 |
| E-11 | Golden set v1 collection & annotation ops | XL | F-03 |
| E-12 | Recognition eval harness in CI | L | E-09, E-11 |

## EPIC F — Data Foundations (Phases 0–2)

| ID | Title | Size | Depends on |
|---|---|---|---|
| F-01 | Public price source connector framework | M | A-09 |
| F-02 | WFP VAM + FAO FPMA connectors | L | F-01, F-03 |
| F-03 | **Taxonomy v1: 1,200 items, 5 locales, vernacular names** | XL | — |
| F-04 | Locality model + geohash resolution | M | A-09 |
| F-05 | Catalog bundle build, signing, delta distribution | L | F-03, A-09 |
| F-06 | Client catalog sync + Room cache + FTS | M | F-05, A-08 |
| F-07 | Open Food Facts / GTIN ingestion | M | F-03 |
| F-08 | Launch-region market API connector | M | F-01, F-04 |
| F-09 | Taxonomy proposal intake + merge tooling | M | F-03 |

## EPIC G — Price Estimation (Phase 3)

| ID | Title | Size | Depends on |
|---|---|---|---|
| G-01 | Distance estimation (depth / AF / reference object) | L | C-06, D-01 |
| G-02 | Food density & shape-factor LUT | M | F-03 |
| G-03 | Segmentation mask → physical dimensions | M | E-02, D-03, G-01 |
| G-04 | Mass estimation with uncertainty propagation | L | G-02, G-03 |
| G-05 | Unit normalization + locality vernacular units | M | F-03, F-04 |
| G-06 | Price serving API + cell lookup | M | A-09, F-04 |
| G-07 | Hierarchical Bayesian price model + nightly fit | XL | G-06, F-02 |
| G-08 | Cold-start partial pooling validation | M | G-07 |
| G-09 | Offline cached bands + staleness handling | M | G-06, A-08 |
| G-10 | Fair-price verdict logic + maturity gate | M | G-06 |
| G-11 | Price backtest harness | L | G-07 |

## EPIC H — Trust & Integrity (Phase 4)

| ID | Title | Size | Depends on |
|---|---|---|---|
| H-01 | Hardware key attestation + StrongBox install key | L | A-01 |
| H-02 | Play Integrity + build-time stub assertion | M | H-01, A-10 |
| H-03 | Server-issued nonce flow | S | A-09, H-01 |
| H-04 | Capture sealing + signing | L | H-01, H-03, C-07, D-10 |
| H-05 | Anti-rephotography detectors | L | D-08, C-06 |
| H-06 | Geo-integrity | L | C-06, F-04 |
| H-07 | Trust weight scorer (server) | M | H-02, H-04, H-06 |
| H-08 | Reputation system | M | H-07 |
| H-09 | Robust aggregation + influence caps | L | H-07, H-08, G-06 |
| H-10 | Consensus state machine + arbitration queue | L | H-09 |
| H-11 | Red-team suite in CI | L | H-09, H-10 |
| H-12 | Collusion + change-point detection | XL | H-10, H-11 |

## EPIC I — Validation & Contribution UX (Phase 3)

| ID | Title | Size | Depends on |
|---|---|---|---|
| I-01 | Capture screen | M | C-05, E-01 |
| I-02 | Label validation UI | L | E-10, A-06 |
| I-03 | Price validation UI + band visualization | L | G-10, A-06 |
| I-04 | Manual item picker | M | F-06 |
| I-05 | Unit & quantity entry | M | G-05 |
| I-06 | Contribute-only flow | M | I-03, I-04 |
| I-07 | Receipt / price-tag capture flow | M | E-08, C-07 |
| I-08 | Submission queue + sync status UI | M | A-08 |
| I-09 | Explore: locality prices + history | M | G-06 |
| I-10 | Onboarding + accessibility pass | L | D-06, I-01 |

## EPIC J — Learning Loop (Phase 5)

| ID | Title | Size | Depends on |
|---|---|---|---|
| J-01 | Observation ingestion → embedding retention | M | H-04, A-09 |
| J-02 | Curation pipeline | L | J-01, H-10 |
| J-03 | Prototype centroid update (geometric median) | L | J-02, E-06 |
| J-04 | Golden-set regression gate automation | M | J-03, E-12 |
| J-05 | Canary rollout + automatic rollback | L | J-04, B-03 |
| J-06 | Shadow mode | L | B-01, J-01 |
| J-07 | Truthful elicitation experiment | M | I-03, H-08 |
| J-08 | Embedding adapter training pipeline | XL | J-02, E-03 |
| J-09 | Influence tracing, reversal, periodic rebuild | L | J-03 |

## EPIC K — Scale, Launch & Ops (Phase 6)

| ID | Title | Size | Depends on |
|---|---|---|---|
| K-01 | Locality onboarding playbook + seeding automation | M | F-02, F-08 |
| K-02 | Localization completion + vernacular review | L | F-03, I-10 |
| K-03 | Performance & battery optimization + baseline profiles | L | I-01, B-07 |
| K-04 | Partner/analyst API + rate limiting | M | G-06 |
| K-05 | Privacy, legal & data-rights implementation | L | A-09, H-04 |
| K-06 | Play Store release + staged rollout | M | K-03, K-05 |
| K-07 | Observability dashboards + alerting | M | B-08, H-07 |
| K-08 | On-call runbooks + transparency report | M | K-07 |

## EPICS L, M, N — Multi-category, price resolution, civic points

Detailed in [doc 12, Part 3](12auditandmigration.md#part-3--new-epics).

| Epic | Theme | Tickets | Design doc |
|---|---|---|---|
| **L** | Category generalization: profiles, SKU identity, cell grain, archetype router | L-00…L-14 | [09](09categoryarchitecture.md) |
| **M** | Price resolution ladder, spatial extrapolation, input validation | M-01…M-11 | [10](10priceresolution.md) |
| **N** | Civic points ledger, revenue attribution, partner platform | N-01…N-14 | [11](11civicpointsandrevenue.md) |

---

## Recommended execution order (first 20 tickets)

Start these three in **week 1, in parallel**, regardless of team size — they are the longest-lead
items and everything else waits on them:

1. **F-03** taxonomy construction (XL, data work, ~8 weeks elapsed)
2. **A-01** module skeleton (unblocks all Android work)
3. **A-05** OpenAPI contract (unblocks client and backend in parallel)

Then:

```
A-07 → A-02 → A-06 → A-03 → A-04 → A-08 → A-10        (foundations)
A-09 → F-01 → F-04 → F-02                             (backend + data, parallel track)
C-01 → C-02 → C-06 → C-08 → C-03                      (camera)
B-01 → B-02 → B-06                                    (runtime, parallel)
D-01 → D-02 → D-03 ★ → D-04 → D-05                    (critical path)
E-11 starts as soon as F-03 has 400 items             (annotation is slow — start early)
```

★ **D-03 is the project bottleneck.** Assign your strongest engineer, review it hardest, and do
not let it start before D-01 is genuinely finished — derived intrinsics done wrong there
propagate silently into every downstream number in the system.
