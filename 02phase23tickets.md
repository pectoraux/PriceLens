# Tickets — Phase 2 & 3

Epics E (Recognition), F (Data Foundations), G (Price Estimation), I (Validation UX).

---

# EPIC E — Recognition

### E-01 — `:ml:pipeline` orchestration + stage contracts
**Size** M · **Depends on** D-03, B-01 · **Spec** doc 01 §"The inference pipeline"

Define the stage interfaces and the orchestrator. Written and reviewed *before* any stage is
implemented — this is the contract every other E ticket builds against.

**Acceptance criteria**
- [ ] Typed contracts: `QualityGate`, `FrameNormalizer`, `Detector`, `Embedder`, `TextMatcher`,
      `Retriever`, `OcrReader`, `Fuser`, `PortionEstimator`.
- [ ] Orchestrator runs stages with the doc 01 parallelism (3a ∥ 3b) and enforces the per-stage
      latency budget, cancelling on overrun rather than blocking the UI.
- [ ] Every stage emits a timing and a status; a stage failure degrades the result instead of
      failing the pipeline.
- [ ] A fake implementation of every stage exists so features can be built and tested without
      models.
- [ ] The pipeline accepts only `CanonicalFrame` — it is structurally impossible to pass a raw
      `ImageProxy` (enforced by types, backed by the A-04 lint rule).

---

### E-02 — Detector selection, export, integration
**Size** L · **Depends on** B-06, E-01 · **Spec** doc 03 §Stage 2

**Acceptance criteria**
- [ ] All three candidate approaches from doc 03 evaluated on the golden set for mAP, latency on
      the reference device, and size.
- [ ] **Licence review documented and signed off** — an AGPL backbone is a release blocker, and
      finding that out in Phase 6 is a catastrophe.
- [ ] Selected detector exported via B-06 with goldens.
- [ ] ≤ 90 ms p95 on the reference mid-tier device.
- [ ] A low-end variant is selectable by the compute profile.
- [ ] Comparison report committed with the decision and its rationale.

---

### E-03 — Embedding model selection, export, integration
**Size** L · **Depends on** B-06, E-01, B-05 · **Spec** doc 03 §Stage 3a

**Acceptance criteria**
- [ ] MobileCLIP-S2, SigLIP variants, and EfficientViT-CLIP evaluated for zero-shot top-1 on the
      golden set, latency, size, and licence.
- [ ] Output 512-d, L2-normalized; image tower only on-device (text tower is server-side).
- [ ] INT8 quantization drift ≤ 1e-3 mean cosine vs fp32.
- [ ] ≤ 110 ms p95 on the reference device.
- [ ] Passes the B-05 parity harness on ≥ 90% of fleet device/delegate combinations.
- [ ] Licence permits commercial use; recorded in the manifest.

---

### E-04 — Person/face detection + redaction
**Size** M · **Depends on** E-02, C-07 · **Spec** doc 00 §"Privacy posture"

**Acceptance criteria**
- [ ] Face detection runs on every frame destined for storage, before the file is written.
- [ ] Detected faces are irreversibly blurred in the stored evidence copy (blur applied to pixels,
      not as an overlay).
- [ ] Runs within the existing detection pass; adds ≤ 15 ms.
- [ ] Test with a face dataset: **zero** unredacted faces written to disk.
- [ ] Redaction failure blocks the write. It never falls open.

---

### E-05 — Dominant region selection
**Size** S · **Depends on** E-02, D-08 · **Spec** doc 03

**Acceptance criteria**
- [ ] Scoring implements `area^0.5 × centrality × objectness × focus_sharpness`, with sharpness
      normalized by the device noise profile.
- [ ] Agrees with human "what is the subject?" annotation ≥ 90% on a 500-image sample.
- [ ] Handles the no-detection case by falling back to a centre crop with a lowered confidence.

---

### E-06 — On-device HNSW prototype index
**Size** L · **Depends on** E-03, F-05 · **Spec** doc 03 §Stage 4

**Acceptance criteria**
- [ ] HNSW (`M=16, efConstruction=200, efSearch=64`) over int8-quantized 512-d centroids.
- [ ] Loads a ~20 MB index in ≤ 400 ms; queries in ≤ 15 ms.
- [ ] Distance-weighted kNN vote over the top 32 neighbours.
- [ ] Delta updates applied without a full rebuild; full rebuild on adapter change.
- [ ] Index signature verified before load (B-03).
- [ ] Recall@10 ≥ 0.98 versus brute-force on a held-out query set.
- [ ] Memory-mapped, not fully heap-resident.

---

### E-07 — Zero-shot text similarity path
**Size** M · **Depends on** E-03, F-05 · **Spec** doc 03 §Stage 4(i)

**Acceptance criteria**
- [ ] Cosine similarity against precomputed, prompt-ensembled text embeddings from the catalog
      bundle.
- [ ] Prompt ensemble includes the locale's **vernacular** name — measurably better than the
      English-only ensemble, and that delta is reported.
- [ ] Covers items with zero prototypes (the cold-start case this path exists for).
- [ ] ≤ 8 ms for 1,200 items.

---

### E-08 — OCR, barcode, GTIN resolution
**Size** M · **Depends on** E-01, F-07 · **Spec** doc 03 §Stage 3b

**Acceptance criteria**
- [ ] ML Kit barcode scanning (EAN-8/13, UPC-A/E, Code-128) and text recognition.
- [ ] GTIN → SKU lookup against the local catalog, then server; ≥ 90% correct resolution on the
      test GTIN set.
- [ ] Net-weight extraction from on-pack text (`500 g`, `1 kg`, `2 × 250 ml`) — when found,
      portion estimation is skipped entirely.
- [ ] Price-tag OCR produces a **separate, higher-provenance price observation**, distinct from
      the user's typed answer (doc 04).
- [ ] A confident GTIN match overrides the vision path.
- [ ] ≤ 120 ms p95, running in parallel with embedding.

---

### E-09 — Fusion + confidence calibration
**Size** L · **Depends on** E-06, E-07, E-08 · **Spec** doc 03 §Stage 5

**Acceptance criteria**
- [ ] Late fusion of all six evidence sources per doc 03.
- [ ] Fusion weights **learned** on the calibration set, not hand-tuned; the fitting script is
      committed and reproducible.
- [ ] `w_knn` is conditioned on prototype count for the top candidates — three prototypes must
      not outvote zero-shot.
- [ ] Temperature is a learned function of the quality metrics (blur, exposure, ISO, motion,
      degradations, NARROW_FOV).
- [ ] Temperature scaling + Dirichlet calibration; ECE ≤ 0.05 globally **and ≤ 0.07 per device
      tier**.
- [ ] Reliability diagrams generated per model version and per device tier, committed to the
      eval report.
- [ ] ≤ 5 ms.

---

### E-10 — Abstention policy in `:domain`
**Size** S · **Depends on** E-09 · **Spec** doc 03 §"Abstention policy"

**Acceptance criteria**
- [ ] The policy from doc 03 implemented as pure functions in `:domain`, with every threshold in
      one `Thresholds.kt`.
- [ ] No threshold literal exists anywhere else in the codebase (verified by the A-04 rule).
- [ ] Thresholds are tunable via remote config with a signed, validated payload and safe defaults.
- [ ] Exhaustive unit tests over the decision table, including the LOW-device-confidence branch.
- [ ] A tuning script reports the precision/abstention trade-off curve so the threshold choice is
      made from data.

---

### E-11 — Golden set v1 collection & annotation ops
**Size** XL · **Depends on** F-03 · **Spec** doc 06 §"Golden set"

**Start in week 4. It is the longest-lead item in Phase 2 and cannot be compressed later.**

**Acceptance criteria**
- [ ] ≥ 3,000 images, ≥ 8 per item for items with ≥ 5 confirmed observations.
- [ ] Stratified across device tier, lighting, background, and presentation per doc 06.
- [ ] Double-annotated with third-annotator adjudication; inter-annotator agreement tracked.
- [ ] Classes with < 85% inter-annotator agreement raised as **taxonomy** problems for merge
      review, not annotator problems.
- [ ] Content-hash storage, version controlled, with a documented change process.
- [ ] **Never** auto-labelled from model output. Provenance recorded per image.
- [ ] Annotator guide includes reference photographs, not just names.

---

### E-12 — Recognition eval harness in CI
**Size** L · **Depends on** E-09, E-11 · **Spec** doc 06

**Acceptance criteria**
- [ ] One command evaluates a model version against the golden set and emits every doc 06
      recognition metric.
- [ ] Per-class breakdown with the **per-class worst** metric surfaced prominently — a 93% mean
      hiding a 20% class is a broken model and the report must say so.
- [ ] Confusion-pair report flags any pair confused > 15%.
- [ ] Risk-coverage curve and reliability diagrams generated.
- [ ] Runs nightly; results published to a dashboard; regressions block promotion.
- [ ] Runs against a fixed random seed and is byte-reproducible.

---

# EPIC F — Data Foundations

### F-01 — Public price source connector framework
**Size** M · **Depends on** A-09 · **Spec** doc 03 §"Public source ingestion"

**Acceptance criteria**
- [ ] `PriceSourceConnector` interface: fetch, normalize, map to taxonomy, map to locality, load.
- [ ] Scheduled runs via Celery beat with per-source cadence.
- [ ] Idempotent upsert keyed on `(source, source_ref, item, locality, period)`.
- [ ] Unmapped items and localities are quarantined for review, never silently dropped.
- [ ] All public data lands at trust tier `PUBLIC` — a prior, never a veto.
- [ ] Per-source licence and attribution recorded and surfaced in the app's about screen.

---

### F-02 — WFP VAM + FAO FPMA connectors
**Size** L · **Depends on** F-01, F-03

**Acceptance criteria**
- [ ] Both sources ingested for the launch region with market-level granularity where available.
- [ ] Unit and currency normalization to the canonical schema, with FX rate snapshots.
- [ ] ≥ 80% of source items mapped to the taxonomy; the remainder queued for review.
- [ ] Historical backfill of ≥ 24 months to support the seasonal term `δ_t`.
- [ ] Source outage or schema change raises an alert rather than silently ingesting nothing.

---

### F-03 — Taxonomy v1
**Size** XL · **Depends on** — · **Spec** doc 05

**Start week 1. Everything downstream waits on this and it cannot be parallelized late.**

**Acceptance criteria**
- [ ] ≥ 1,200 items with hierarchy, category, default unit, and shape model.
- [ ] Names in all 5 launch locales, **including vernacular market names** — reviewed by native
      speakers, not machine-translated.
- [ ] Density and shape factors populated for produce items (feeds G-02).
- [ ] Seasonality weights per region where known.
- [ ] Text embeddings generated with the prompt ensemble and stored.
- [ ] Merge/retire lifecycle supported; historical observations survive a merge.
- [ ] Coverage validated against the launch region's actual market inventory by a local reviewer
      walking a real market with the list.

---

### F-04 — Locality model + geohash resolution
**Size** M · **Depends on** A-09 · **Spec** doc 05

**Acceptance criteria**
- [ ] Geohash-6 → locality resolution with PostGIS; auto-creation of new localities on first
      observation.
- [ ] Region hierarchy for the price model's partial pooling.
- [ ] Currency, unit system, and market type per locality.
- [ ] Maturity state machine: `seeding → learning → mature`, driven by the doc 00 thresholds.
- [ ] Client-side geohash-6 coarsening, with a test proving finer coordinates never leave the
      device.

---

### F-05 — Catalog bundle build, signing, delta distribution
**Size** L · **Depends on** F-03, A-09

**Acceptance criteria**
- [ ] Bundle build per locality cluster: taxonomy subset, text embeddings, prototype centroids,
      calibration params.
- [ ] Ed25519 signed; content-addressed; CDN-distributed.
- [ ] Delta bundles keyed on version; full refresh on major version change.
- [ ] Bundle ≤ 25 MB for a typical locality cluster.
- [ ] Build is reproducible and versioned alongside the model registry.

---

### F-06 — Client catalog sync + Room cache + FTS
**Size** M · **Depends on** F-05, A-08

**Acceptance criteria**
- [ ] WorkManager sync on unmetered network, with delta support.
- [ ] Signature verified before the cache is updated; failure keeps the previous catalog.
- [ ] FTS4 index over display and vernacular names in all installed locales.
- [ ] Search returns "zucchini" for a query of "courgette" — cross-locale vernacular matching.
- [ ] Atomic swap: the app is never left with a half-updated catalog.

---

### F-07 — Open Food Facts / GTIN ingestion
**Size** M · **Depends on** F-03

**Acceptance criteria**
- [ ] GTIN → product name, brand, net weight, category ingested and mapped to the taxonomy.
- [ ] **ODbL obligations reviewed and documented** — share-alike terms apply to redistributed
      derived databases and must be understood before launch, not after.
- [ ] Regional subset shipped in the catalog bundle for offline barcode resolution.
- [ ] Server fallback for GTINs absent from the local bundle.

---

### F-08 — Launch-region market API connector
**Size** M · **Depends on** F-01, F-04

**Acceptance criteria**
- [ ] The launch region's national/subnational market price API connected via F-01.
- [ ] ToS reviewed; only permitted programmatic access is used. **Scraping in violation of ToS
      is out of scope** and must not be implemented.
- [ ] Market-level geographies mapped to localities.
- [ ] Failure and rate-limit handling with alerting.

---

### F-09 — Taxonomy proposal intake + merge tooling
**Size** M · **Depends on** F-03 · **Spec** doc 05

**Acceptance criteria**
- [ ] `POST /v1/taxonomy/proposals` implemented with rate limiting.
- [ ] A proposal requires ≥ 3 independent submissions before reaching human review — otherwise
      the taxonomy is a spam vector.
- [ ] Reviewer tooling: approve, merge into an existing item, or reject with a reason.
- [ ] Merging preserves historical observations via `merged_into_id`.
- [ ] Approved items appear in the next catalog bundle with generated text embeddings.

---

# EPIC G — Price Estimation

### G-01 — Distance estimation
**Size** L · **Depends on** C-06, D-01 · **Spec** doc 03 §Stage 6

**Acceptance criteria**
- [ ] Preference chain implemented: ARCore Depth → `LENS_FOCUS_DISTANCE` (only when calibration
      is `APPROXIMATE` or better) → reference object → none.
- [ ] Reference-object detection for a bank card (85.60 × 53.98 mm) and common coins.
- [ ] Every estimate carries an uncertainty `σ_d`.
- [ ] **"None" is a valid, well-handled outcome** that routes to explicit user unit entry.
- [ ] Accuracy on the reference rig: ≤ 8% error with depth, ≤ 15% with a reference object.

---

### G-02 — Food density & shape-factor LUT
**Size** M · **Depends on** F-03

**Acceptance criteria**
- [ ] Density (kg/L) and shape model for every produce item in the taxonomy, sourced from
      published food-composition references with citations.
- [ ] Shape models: spheroid, cylinder, flat, pile, packaged — with the projected-area-to-volume
      factor for each.
- [ ] The **pile** model includes an occlusion correction, since heaps are the common market case
      and the hardest to estimate.
- [ ] Per-item uncertainty `σ_density` recorded, not assumed constant.
- [ ] Validated against physical weighing of ≥ 50 real items: median error ≤ 20%.

---

### G-03 — Segmentation mask → physical dimensions
**Size** M · **Depends on** E-02, D-03, G-01

**Acceptance criteria**
- [ ] `mm_per_px = (2·d·tan(hFOV_canonical/2)) / canonical_width_px` — the device-independent
      formula the entire Phase 1 work exists to enable.
- [ ] Oblique-angle correction using the gravity vector from the rotation sensor.
- [ ] Physical width, height, and projected area with propagated uncertainty.
- [ ] Verified against the ruler card on the reference rig: ≤ 5% dimensional error with depth.

---

### G-04 — Mass estimation with uncertainty propagation
**Size** L · **Depends on** G-02, G-03 · **Spec** doc 03 §Stage 6

**Acceptance criteria**
- [ ] `mass = projected_area^1.5 × shape_factor × density`, per the item's shape model.
- [ ] Uncertainty propagated: `σ_mass/mass ≈ sqrt((2σ_d/d)² + σ_shape² + σ_density²)`.
- [ ] **Hard rule: relative error > 25% → no mass is asserted.** The app asks for the unit and
      quantity instead. Tested explicitly.
- [ ] For piles and bunches, the natural retail unit is preferred and the mass conversion is
      recorded separately at lower confidence.
- [ ] Validated against physical weighing: within 20% on ≥ 70% of the 50-item validation set.

**Notes:** the failure mode this ticket exists to prevent is a plausible-looking wrong per-kg
price. Silent guessing here corrupts the database and directly causes the harm the product is
meant to prevent. When in doubt, ask the user.

---

### G-05 — Unit normalization + locality vernacular units
**Size** M · **Depends on** F-03, F-04 · **Spec** doc 03 §"Unit normalization"

**Acceptance criteria**
- [ ] Canonical units: kg, L, piece, bunch, bag, crate. Pure functions in `:domain`.
- [ ] Locality-specific vernacular units (gorogoro, debe, mudu, tin, heap) via the versioned
      `locality_unit_conversion` table.
- [ ] Conversion confidence recorded (`measured`/`estimated`/`assumed`).
- [ ] **Where no reliable conversion exists, the vernacular unit is the canonical unit for that
      locality.** No forced conversion — a wrong "tin" factor silently corrupts every price in
      that market.
- [ ] Money is `Long` minor units + ISO-4217 throughout; property tests over conversion
      round-trips.

---

### G-06 — Price serving API + cell lookup
**Size** M · **Depends on** A-09, F-04 · **Spec** doc 05

**Acceptance criteria**
- [ ] `POST /v1/price/estimate`, `GET /v1/price/cells`, `GET /v1/price/history` implemented.
- [ ] Returns `confidence: INSUFFICIENT` (HTTP 200, not an error) for immature cells.
- [ ] Falls back through: exact cell → adjacent weeks → neighbouring localities in the same
      region → regional prior — with the fallback level reported in the response.
- [ ] p95 ≤ 150 ms with Redis caching.
- [ ] Rate limited per IP for anonymous reads.

---

### G-07 — Hierarchical Bayesian price model + nightly fit
**Size** XL · **Depends on** G-06, F-02 · **Spec** doc 03 §"The model"

**Acceptance criteria**
- [ ] The doc 03 model implemented in NumPyro or Stan: item, locality, interaction, temporal, and
      a **Student-t(ν=4) likelihood**.
- [ ] Nightly regional fits; parameters exported to a fast serving table (no MCMC on the request
      path).
- [ ] Convergence diagnostics (R̂ ≤ 1.01, ESS) checked automatically; a non-converged fit is
      **not** promoted.
- [ ] Posterior predictive quantiles produce the P10/P50/P90 band.
- [ ] Freshness decay applied at serving time.
- [ ] Partial pooling verified: a 3-observation cell borrows correctly from its region.

---

### G-08 — Cold-start partial pooling validation
**Size** M · **Depends on** G-07

**Acceptance criteria**
- [ ] MdAPE ≤ 25% on cells with n ∈ [1,5] in backtest.
- [ ] Bands on thin cells are demonstrably **wider** than on mature cells — uncertainty must be
      visible, not hidden by the prior.
- [ ] Shrinkage strength validated against a held-out set; no over-shrinkage that makes every
      locality look like the regional average (which would destroy the product's entire premise).

---

### G-09 — Offline cached bands + staleness handling
**Size** M · **Depends on** G-06, A-08

**Acceptance criteria**
- [ ] Cells for the user's frequent localities pre-cached on unmetered network.
- [ ] Offline lookups served from cache with `freshnessDays` shown prominently.
- [ ] Cells older than 21 days show no verdict — only the data and its age.
- [ ] Cache eviction by locality recency; bounded size.

---

### G-10 — Fair-price verdict logic + maturity gate
**Size** M · **Depends on** G-06 · **Spec** doc 03

**Acceptance criteria**
- [ ] Verdict bands per doc 03 implemented as pure `:domain` functions.
- [ ] **No verdict is shown when `n_observations < 5` or `freshness_days > 21`** — only
      "not enough local data yet". Tested as a hard rule.
- [ ] Verdict copy is non-accusatory and does not name or imply a seller.
- [ ] Exhaustive unit tests across the boundary conditions.

---

### G-11 — Price backtest harness
**Size** L · **Depends on** G-07 · **Spec** doc 06

**Acceptance criteria**
- [ ] **Temporal splits only**; random splits are rejected by the harness itself, not just by
      convention.
- [ ] Rolling 26-week evaluation reporting coverage, MdAPE, pinball loss, and sharpness together.
- [ ] Includes the adversarial slice of genuine regime shifts; reports tracking lag.
- [ ] Cold-start cells reported separately.
- [ ] Runs nightly; regressions block model promotion.

---

# EPIC I — Validation & Contribution UX

### I-01 — Capture screen
**Size** M · **Depends on** C-05, E-01

**Acceptance criteria**
- [ ] Full-bleed preview, detection overlay on the dominant region, coaching hints, capture button.
- [ ] Live label appears as soon as Stage 5 completes; the price band streams in after —
      **never block the label on the network**.
- [ ] One-handed reachable; capture button ≥ 56 dp.
- [ ] Cold start to camera-ready p95 ≤ 1.8 s.
- [ ] Works with the camera permission denied (routes to manual entry).

---

### I-02 — Label validation UI
**Size** L · **Depends on** E-10, A-06 · **Spec** doc 00

**Acceptance criteria**
- [ ] Confident state: label + confidence, one-tap confirm, obvious "not right" affordance.
- [ ] Abstain state: the three-option `AbstainPrompt` plus "none of these" → manual picker.
- [ ] Correction is ≤ 2 taps for a top-3 item.
- [ ] Both the prediction and the user's answer are recorded, plus which was corrected.
- [ ] Confidence is conveyed in **text**, not colour alone.
- [ ] TalkBack announces label, confidence, and available actions.
- [ ] Correcting never feels like a failure — copy is neutral and thanks the user concretely.

---

### I-03 — Price validation UI + band visualization
**Size** L · **Depends on** G-10, A-06

**Acceptance criteria**
- [ ] `PriceBandBar` shows P10/P50/P90 with the asked price marked; band width visibly encodes
      uncertainty.
- [ ] The user enters what they were quoted and gets a verdict, or sees "not enough local data".
- [ ] Sample size and freshness always visible — never a bare number.
- [ ] Currency and unit from the locality, not the device locale.
- [ ] INSUFFICIENT state is a designed, non-apologetic state that still invites contribution.
- [ ] TalkBack announces the band, the verdict, and the sample size.

---

### I-04 — Manual item picker
**Size** M · **Depends on** F-06

**Acceptance criteria**
- [ ] FTS search over display and vernacular names in all installed locales.
- [ ] Photo grid for visual disambiguation — names alone cannot separate produce varieties.
- [ ] Recent and locality-common items surfaced first.
- [ ] "Not in the list" routes to a taxonomy proposal (F-09).
- [ ] Results in ≤ 100 ms for a 1,200-item catalog.

---

### I-05 — Unit & quantity entry
**Size** M · **Depends on** G-05

**Acceptance criteria**
- [ ] Unit options come from the locality's vernacular set plus canonical units.
- [ ] Pre-filled from the portion estimate **only when** G-04 asserted a mass; otherwise empty and
      required.
- [ ] Numeric input is locale-aware (decimal separators).
- [ ] Implausible quantities (a 40 kg tomato) are challenged before submission.

---

### I-06 — Contribute-only flow
**Size** M · **Depends on** I-03, I-04

**Acceptance criteria**
- [ ] A user can record a price without a prediction: pick item → unit → price → submit.
- [ ] Optional photo; submitting without one lands at `NO_IMAGE` provenance (doc 04).
- [ ] Reachable in ≤ 2 taps from the main screen — contribution is a first-class action, not an
      afterthought.
- [ ] Works fully offline and queues.

---

### I-07 — Receipt / price-tag capture flow
**Size** M · **Depends on** E-08, C-07

**Acceptance criteria**
- [ ] Dedicated capture mode with OCR of item lines and prices.
- [ ] Multi-item receipts produce multiple observations after user review of each line.
- [ ] Produces `RECEIPT_OCR` provenance (the highest tier — a photographed price is much harder to
      fabricate than a typed one).
- [ ] The user reviews every extracted line before submission; OCR is never trusted blind.

---

### I-08 — Submission queue + sync status UI
**Size** M · **Depends on** A-08 · **Spec** doc 04 §"Transparency"

**Acceptance criteria**
- [ ] The user sees their queued, syncing, submitted, and published observations.
- [ ] Failed submissions show a plain-language reason and a retry.
- [ ] "Send now" over metered network is available and explicit.
- [ ] Published status is visible — contribution must feel like it landed.
- [ ] Reputation, weights, and tiers are **never** displayed (doc 04).

---

### I-09 — Explore: locality prices + history
**Size** M · **Depends on** G-06

**Acceptance criteria**
- [ ] Browse published price cells for the current locality; search by item.
- [ ] 26-week history chart for an item, published cells only.
- [ ] Immature localities show a "learning this area" state with a contribution invite.
- [ ] Charts are accessible: data table alternative and TalkBack summaries.

---

### I-10 — Onboarding + accessibility pass
**Size** L · **Depends on** D-06, I-01

**Acceptance criteria**
- [ ] Onboarding covers: what the app does, the privacy posture (photos and location), and the
      optional calibration invite — honest framing, skippable, ≤ 4 screens.
- [ ] No permission requested before the user has seen why it is needed.
- [ ] Full accessibility audit across capture, validation, and contribute flows:
      TalkBack, 200% font scale, contrast, touch targets, no colour-only information.
- [ ] Accessibility scanner passes with zero critical issues.
- [ ] Verified with at least one screen-reader user session.
