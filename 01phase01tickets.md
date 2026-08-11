# Tickets — Phase 0 & 1

Epics A (Foundations), B (ML Runtime), C (Camera), D (Device Profiling).

**Definition of Done, applies to every ticket:** acceptance criteria have automated coverage;
public APIs have KDoc; the architecture rules in `GEMINI.md` are not violated; CI is green; the
commit message is `[{id}] {title}`.

---

# EPIC A — Foundations & Platform

### A-01 — Gradle multi-module skeleton
**Size** M · **Depends on** — · **Spec** doc 01

Create the module graph from doc 01 with version catalogs, convention plugins, and Kotlin 2.x /
JDK 17 / minSdk 26 configured once and shared.

**Acceptance criteria**
- [ ] All modules from doc 01 exist and build from a clean clone.
- [ ] `libs.versions.toml` is the single source of dependency versions; no inline versions.
- [ ] Convention plugins (`pricelens.android.library`, `pricelens.android.feature`,
      `pricelens.jvm.library`, `pricelens.android.test`) applied consistently.
- [ ] `:domain` and `:core:common` compile as pure JVM modules with no Android dependency.
- [ ] Clean build ≤ 8 min; incremental ≤ 45 s.
- [ ] Configuration cache and build cache enabled.

**Notes:** get this right once. Module churn later is expensive and confuses coding agents badly.

---

### A-02 — Hilt DI, app shell, navigation host
**Size** S · **Depends on** A-01

Application class, Hilt setup, single-activity Compose host, type-safe navigation graph with
routes for capture, review, contribute, explore, and settings.

**Acceptance criteria**
- [ ] `@HiltAndroidApp` app builds and launches to an empty capture route.
- [ ] Navigation is type-safe (`kotlinx.serialization` routes), no string literals.
- [ ] Each feature module exposes its own nav graph; `:app` composes them without knowing internals.
- [ ] Deep links defined for `pricelens://item/{slug}` and `pricelens://locality/{geohash6}`.
- [ ] Process-death restoration test passes for each route.

---

### A-03 — CI pipeline
**Size** M · **Depends on** A-01

GitHub Actions (or equivalent): assemble, unit tests, lint, detekt, instrumented tests on
Firebase Test Lab across 3 device configurations, and artifact publication.

**Acceptance criteria**
- [ ] PR pipeline: build + unit test + lint + detekt, ≤ 12 min.
- [ ] Nightly: instrumented tests on flagship / mid-tier / API-26 configurations.
- [ ] Test results and coverage published as PR comments.
- [ ] Gradle build scan or equivalent on every run.
- [ ] A deliberately failing test blocks merge (verify by opening a throwaway PR).

---

### A-04 — Architecture rule enforcement
**Size** M · **Depends on** A-01 · **Spec** docs 01, 08

Automated enforcement of the rules an agent will otherwise erode. Konsist for structural rules,
custom detekt rules for the rest.

**Acceptance criteria**
- [ ] Build fails if `:domain` imports anything from `android.*`, `androidx.*`, Room, or Ktor.
- [ ] Build fails if a `:feature:*` module depends on another `:feature:*`.
- [ ] Build fails if any `:ml:*` module imports `androidx.camera.*` — enforces
      "no model sees a raw frame".
- [ ] Build fails on `Double`/`Float`-typed fields whose name matches `.*[Pp]rice.*`.
- [ ] Build fails on `.average()` or `.sum()/size` in `:ml:price` or the backend pricing package.
- [ ] Build fails on `!!` outside test sources.
- [ ] Each rule has a fixture proving it fires — a rule that has never fired is not known to work.

---

### A-05 — OpenAPI contract + codegen
**Size** L · **Depends on** — · **Spec** doc 05

Author `api/openapi.yaml` covering every endpoint in doc 05. Generate the Kotlin Ktor client and
the FastAPI/Pydantic server models from it.

**Acceptance criteria**
- [ ] Spec validates against OpenAPI 3.1 and is linted (spectral).
- [ ] Kotlin client generated into `:core:data`; hand-written HTTP client code is prohibited by
      review.
- [ ] Pydantic models generated for the backend.
- [ ] Money is `integer` minor units + ISO-4217 code everywhere; no float money in the spec.
- [ ] Error responses are typed and deliberately non-specific for integrity failures (doc 05).
- [ ] A CI check fails if generated sources drift from the spec.

---

### A-06 — Design system + uncertainty primitives
**Size** M · **Depends on** A-01 · **Spec** doc 00

Material 3 theme (light/dark/dynamic), typography, spacing, and the components this product needs
that Material does not provide.

**Acceptance criteria**
- [ ] `PriceBandBar`: renders P10/P50/P90 with the asked price marked, and legibly conveys band
      width — a wide band must *look* uncertain at a glance.
- [ ] `ConfidenceChip`: HIGH/MEDIUM/LOW with text labels, never colour alone (colour-blind users).
- [ ] `AbstainPrompt`: the "is it one of these?" three-option component.
- [ ] All components have `@Preview` for every state including error and empty.
- [ ] Contrast ratios ≥ 4.5:1 verified; touch targets ≥ 44 dp.
- [ ] Every component exposes a content description; verified with an accessibility test.

**Notes:** the uncertainty components are product-critical, not decoration. If a wide band reads
as confident, the app misleads people. Design review required, not just code review.

---

### A-07 — `:core:common`
**Size** S · **Depends on** A-01

`Result<T>` with typed errors, a `DispatcherProvider`, structured logging with PII redaction, and
shared test fixtures.

**Acceptance criteria**
- [ ] `Result<T>` with `Success`/`Failure(AppError)`; `AppError` is a sealed hierarchy.
- [ ] `DispatcherProvider` injected everywhere; a detekt rule bans hardcoded `Dispatchers.*`
      outside it.
- [ ] Logger redacts anything matching coordinate, phone-number, or token patterns — with tests.
- [ ] `TestDispatcherProvider` and a fixture factory available to all modules.

---

### A-08 — `:core:data`
**Size** M · **Depends on** A-01, A-05 · **Spec** doc 05

Room database, Proto DataStore, Ktor client configuration, WorkManager setup.

**Acceptance criteria**
- [ ] Room entities from doc 05 with migrations and exported schemas committed.
- [ ] FTS4 table over taxonomy display + vernacular names.
- [ ] Proto DataStore for `DeviceProfile` and user settings.
- [ ] Ktor client: auth interceptor, retry with jittered backoff, certificate pinning, timeouts.
- [ ] WorkManager with a Hilt worker factory.
- [ ] Migration tests from each released schema version.

---

### A-09 — Backend skeleton
**Size** L · **Depends on** A-05 · **Spec** doc 05

FastAPI service, Postgres 16 + PostGIS + TimescaleDB, Alembic migrations for the full doc 05
schema, Redis + Celery, S3-compatible storage, local docker-compose.

**Acceptance criteria**
- [ ] `docker compose up` gives a working stack with seeded data in one command.
- [ ] All doc 05 tables created via migrations; hypertable on `observation`.
- [ ] Health, readiness, and metrics endpoints.
- [ ] Structured JSON logging with request IDs.
- [ ] Auth middleware skeleton (bearer, anonymous read).
- [ ] Integration test suite runs against the composed stack in CI.

---

### A-10 — Build flavors, signing, feature delivery
**Size** M · **Depends on** A-01 · **Spec** doc 01

`dev`/`staging`/`prod` flavors, signing config, R8, ABI splits, and the Play Feature Delivery
module for on-demand ML assets.

**Acceptance criteria**
- [ ] Three flavors with distinct application IDs and backend URLs.
- [ ] Base APK ≤ 25 MB with models excluded.
- [ ] On-demand feature module for model assets installs and loads at runtime.
- [ ] R8 with a checked-in mapping upload step; no reflection breakage (verified by running the
      instrumented suite on the release build).
- [ ] Release build fails if `BuildConfig.DEBUG` code paths or stub implementations are reachable.

---

# EPIC B — ML Runtime & Model Delivery

### B-01 — LiteRT integration + interpreter lifecycle
**Size** M · **Depends on** A-01 · **Spec** doc 01

Wrap LiteRT with correct lifecycle handling: creation, warm-up, thread confinement, and release.

**Acceptance criteria**
- [ ] `InferenceEngine` interface with a LiteRT implementation and a fake for tests.
- [ ] Interpreters are pooled and reused; never created per frame.
- [ ] Explicit warm-up (5 dummy inferences) before first real use.
- [ ] Interpreters released on lifecycle stop; a leak test proves no native memory growth over
      1,000 inferences.
- [ ] Concurrent inference on one interpreter is impossible (enforced by the API, not convention).

---

### B-02 — Delegate abstraction layer
**Size** M · **Depends on** B-01 · **Spec** doc 02

Uniform interface over NNAPI (per accelerator where enumerable), GPU, XNNPACK, and CPU, with
construction, capability query, and safe teardown.

**Acceptance criteria**
- [ ] `Delegate` sealed hierarchy with availability probing that never crashes on unsupported
      hardware.
- [ ] Delegate construction failure falls back down the chain without user-visible error.
- [ ] Supported delegates enumerable at runtime with their properties (fp16, thread count).
- [ ] Tested on API 26 and current API in Test Lab.

---

### B-03 — Model registry client + signature verification
**Size** M · **Depends on** A-05, A-08 · **Spec** docs 05, 06

Fetch the model manifest, verify Ed25519 signatures against a pinned key, and manage local
versions.

**Acceptance criteria**
- [ ] Manifest fetch with `deviceClassId` and ABI.
- [ ] Every artifact's SHA-256 and signature verified before it is loaded. **A verification
      failure deletes the artifact and falls back to the previous version.**
- [ ] Pinned public key in the binary; key rotation supported via a signed key-set.
- [ ] Version pinning and rollback by manifest change without an app update.
- [ ] Test: a tampered model byte is rejected and the previous version stays active.

---

### B-04 — On-demand model download
**Size** M · **Depends on** B-03, A-10

Download models through Play Feature Delivery / asset packs, orchestrated by WorkManager.

**Acceptance criteria**
- [ ] Download on first launch over unmetered network; user-initiated download over metered.
- [ ] Progress surfaced in the UI; app is usable (manual-entry mode) while downloading.
- [ ] Resume after interruption; integrity verified post-download.
- [ ] Storage-pressure eviction of stale versions, keeping N-1 for rollback.

---

### B-05 — Golden-tensor delegate parity harness
**Size** L · **Depends on** B-02, B-03 · **Spec** doc 02 §"Compute profile"

The parity check that keeps a lossy vendor delegate from silently breaking retrieval.

**Acceptance criteria**
- [ ] 32 golden input tensors and CPU-float reference outputs ship with each model.
- [ ] Every available delegate is evaluated: mean cosine distance and max per-sample distance vs
      reference.
- [ ] Delegates are **rejected** at mean > 1e-3 or any sample > 5e-3, or on NaN/Inf.
- [ ] Latency benchmarked (p50/p95, 20 runs after 5 warm-ups) for parity-passing delegates only.
- [ ] The fastest parity-passing delegate is selected; the full audit trail including rejections
      is stored in `ComputeProfile`.
- [ ] Test: an injected delegate that perturbs outputs by 1e-2 is rejected.
- [ ] Whole evaluation completes in ≤ 8 s and runs off the UI thread at first launch.
- [ ] A server-supplied delegate blocklist skips known-bad vendor drivers without local testing.

---

### B-06 — Model export toolchain
**Size** L · **Depends on** — · **Spec** doc 03

A reproducible `tools/model-export/` pipeline: PyTorch/JAX → ONNX → LiteRT, quantization,
golden-tensor generation, signing.

**Acceptance criteria**
- [ ] One command exports a model with its goldens and manifest entry.
- [ ] INT8 post-training quantization with a representative dataset; fp16 variant also produced.
- [ ] Quantization drift reported (mean cosine vs fp32); **export fails above 1e-3**.
- [ ] Output is byte-reproducible for a given input checkpoint and config.
- [ ] Licence and provenance of every model recorded in the manifest — required, not optional.

---

### B-07 — Thermal & power policy
**Size** M · **Depends on** B-02 · **Spec** doc 02

**Acceptance criteria**
- [ ] Thermal listener registered; state exposed to the pipeline.
- [ ] MODERATE → analysis frame rate reduced; SEVERE → next-fastest delegate + smaller model
      variant; CRITICAL → continuous analysis suspended, tap-to-capture only.
- [ ] Battery-saver mode reduces frame rate.
- [ ] Recovery on cooldown without an app restart.
- [ ] Instrumented test: sustained 5-minute capture never reaches CRITICAL on the reference device.

---

### B-08 — Inference telemetry
**Size** S · **Depends on** B-01 · **Spec** doc 06

**Acceptance criteria**
- [ ] Per-inference: stage latencies, delegate used, thermal state, peak memory, model versions.
- [ ] Aggregated locally and uploaded in batches; no per-inference network calls.
- [ ] Opt-out honored; no image data, no location in telemetry.
- [ ] Debug overlay in `dev` builds showing live stage timings.

---

# EPIC C — Camera & Capture

### C-01 — CameraX session + Camera2 interop bridge
**Size** M · **Depends on** A-01 · **Spec** doc 01

CameraX for lifecycle and use cases; Camera2 interop to reach characteristics and capture
request keys CameraX does not expose.

**Acceptance criteria**
- [ ] Preview + ImageAnalysis (YUV_420_888) + ImageCapture bound to lifecycle.
- [ ] `Camera2CameraInfo` / `Camera2Interop` bridge exposes raw `CameraCharacteristics` and
      per-frame `CaptureResult`.
- [ ] Analysis runs `STRATEGY_KEEP_ONLY_LATEST`; back-pressure never blocks preview.
- [ ] Correct handling of rotation, aspect ratio, and orientation on all fleet devices.
- [ ] Graceful behaviour when the camera is unavailable (in use by another app).

---

### C-02 — Physical camera selection & pinning
**Size** M · **Depends on** C-01 · **Spec** doc 02 §"Camera selection"

**Acceptance criteria**
- [ ] Selection rule from doc 02 implemented: largest rear sensor within 0.8–1.4× standard focal,
      preferring one reporting `LENS_INTRINSIC_CALIBRATION`.
- [ ] The chosen **physical** camera ID is pinned so the logical camera cannot hand off to the
      ultra-wide at close focus.
- [ ] Verified on a multi-camera device: focusing at 10 cm does not switch physical cameras.
- [ ] Falls back sensibly on single-camera and LEGACY devices.
- [ ] Selected camera ID recorded in the `DeviceProfile`.

**Notes:** the silent switch to an ultra-wide at close focus is a real and commonly missed source
of scale error. Write an explicit test for it.

---

### C-03 — ISP control
**Size** M · **Depends on** C-02 · **Spec** doc 02

Suppress vendor image "enhancement" so frames are reproducible.

**Acceptance criteria**
- [ ] Where supported: `AWB_MODE_OFF` with profile-derived gains, `NOISE_REDUCTION_MODE_MINIMAL`,
      `EDGE_MODE_OFF`, `TONEMAP_MODE_CONTRAST_CURVE` with a linear curve.
- [ ] Each unsupported control recorded as a `degradation`, never a hard failure.
- [ ] Applied controls and their actual `CaptureResult` values are logged — requested is not
      always granted, and the difference matters.
- [ ] Verified on the fleet; LEGACY-level device degrades cleanly.

---

### C-04 — Frame quality gate
**Size** M · **Depends on** C-01, C-06 · **Spec** doc 01 §Stage 0

**Acceptance criteria**
- [ ] Blur via Laplacian variance, **normalized by the device noise profile** (D-08) — a raw
      threshold is meaningless across sensors.
- [ ] Exposure assessment from the luminance histogram (clipping at both ends).
- [ ] Motion from the gyro window; rejects frames during fast pans.
- [ ] AF lock state required before a capture is accepted.
- [ ] Runs in ≤ 8 ms per frame.
- [ ] Emits a typed `FrameQuality` with per-dimension scores, consumed by fusion (E-09) to widen
      the posterior.

---

### C-05 — Capture coaching UI
**Size** M · **Depends on** C-04, A-06

**Acceptance criteria**
- [ ] One actionable hint at a time, prioritized by severity ("move closer", "hold still",
      "too dark", "tap to focus").
- [ ] Hints debounced ≥ 800 ms; no flicker.
- [ ] Hints are announced to TalkBack.
- [ ] Capture button disabled with a clear reason when quality is failing — but a long-press
      override exists, because the user may know better than the gate.

---

### C-06 — Sensor suite manager
**Size** S · **Depends on** A-01 · **Spec** doc 02 §"Sensor suite"

**Acceptance criteria**
- [ ] Gyro, accel, rotation vector, ambient light registered with lifecycle-aware batching.
- [ ] A rolling 500 ms IMU buffer available at capture time (needed by H-04 sealing).
- [ ] ARCore availability probed without requiring the ARCore dependency at runtime on devices
      that lack it.
- [ ] Missing sensors degrade cleanly and are recorded in the profile.
- [ ] Sensor listeners unregistered on stop; verified by a leak test.

---

### C-07 — Evidence capture + storage with redaction hook
**Size** M · **Depends on** C-01 · **Spec** docs 00, 04

**Acceptance criteria**
- [ ] Full-resolution evidence JPEG stored in app-internal storage, never in shared media.
- [ ] A redaction hook runs **before** the file is written (E-04 plugs in here).
- [ ] Perceptual hash computed and stored at capture time.
- [ ] Storage quota with LRU eviction of synced evidence.
- [ ] Deleting a draft deletes its evidence file — verified.

---

### C-08 — Camera permissions + graceful degradation
**Size** S · **Depends on** C-01

**Acceptance criteria**
- [ ] Rationale shown before the request; permanent-denial path leads to settings.
- [ ] Without camera permission the app still works in manual-entry mode (search item, see price
      band) — the price lookup is valuable on its own.
- [ ] Location permission is separately optional; denial falls back to manual locality selection.
- [ ] No permission is requested at first launch before the user has seen why.

---

# EPIC D — Device Profiling & Calibration ★

### D-01 — CameraCharacteristics enumeration + derived intrinsics
**Size** L · **Depends on** C-01, C-02 · **Spec** doc 02 §"Optical profile"

**Acceptance criteria**
- [ ] Every characteristic in the doc 02 table read, with null-safety on every one (mid-tier
      devices omit many, and a missing key must never crash).
- [ ] `LENS_INTRINSIC_CALIBRATION` used directly when present; `optical.source = REPORTED`.
- [ ] Otherwise intrinsics derived by the doc 02 formula; `source = DERIVED` and
      `overall_confidence` dropped one tier.
- [ ] Intrinsics correctly rescaled from the active array to the analysis stream resolution,
      including crop-region and zoom-ratio effects.
- [ ] Quad-Bayer / non-standard CFA sensors detected and recorded.
- [ ] Unit tests over recorded characteristic fixtures from all 8 fleet devices.
- [ ] Builds a valid profile on the LEGACY-level device with the right degradations.

**Notes:** the scale-to-analysis-stream step is where mistakes hide. A profile that is correct at
full resolution and wrong at analysis resolution produces a consistent, plausible, wrong answer.

---

### D-02 — Brown–Conrady distortion model
**Size** M · **Depends on** D-01

**Acceptance criteria**
- [ ] Forward and inverse distortion with radial (k1,k2,k3) and tangential (p1,p2) terms.
- [ ] Inverse solved iteratively to ≤ 0.1 px residual.
- [ ] Round-trip distort→undistort recovers a synthetic grid within 0.5 px.
- [ ] Absent coefficients → identity transform + `NO_DISTORTION_COEFFS` degradation.
- [ ] Precomputed remap LUT so per-frame cost is a table lookup, not a solve.

---

### D-03 — Canonical camera space reprojection ★
**Size** L · **Depends on** D-01, D-02 · **Spec** doc 02 §"Canonical camera space"

**The project bottleneck.** See the worked agent prompt in doc 08.

**Acceptance criteria**
- [ ] Projects to 55° hFOV, 448×448, 0.1228 °/px.
- [ ] Undistortion applied when coefficients exist.
- [ ] **Cross-device scale invariance:** synthetic 13 mm ultra-wide and 24 mm main intrinsics
      viewing the same simulated 6 cm object at 30 cm yield canonical pixel extents within 2%.
- [ ] Principal-point offset honored: a synthetic `c_x` offset of 50 px produces a correctly
      centred canonical frame.
- [ ] hFOV < 55° → letterbox, `NARROW_FOV` degradation, **never upscale**.
- [ ] Bilinear sampling; bicubic when the compute profile affords it.
- [ ] ≤ 25 ms p95 for 1920×1080 input on the reference device (instrumented benchmark).
- [ ] No deprecated APIs (no RenderScript).

---

### D-04 — Passive photometric profile extraction
**Size** M · **Depends on** D-01 · **Spec** doc 02 §"Photometric profile"

**Acceptance criteria**
- [ ] `SENSOR_COLOR_TRANSFORM1/2`, `SENSOR_CALIBRATION_TRANSFORM1/2`,
      `SENSOR_FORWARD_MATRIX1/2`, and reference illuminants extracted as rationals → floats.
- [ ] Interpolation between the two reference illuminants by correlated colour temperature.
- [ ] Absent matrices → a documented sRGB fallback + degradation.
- [ ] Matrices validated: a neutral input maps to a near-neutral output within ΔE 3 on fixture
      data.

---

### D-05 — Photometric normalization pipeline
**Size** L · **Depends on** D-04, D-03 · **Spec** doc 02

The full YUV → linear → vignetting → CCM → sRGB-D65 chain.

**Acceptance criteria**
- [ ] Correct YUV→RGB matrix per the frame's actual colour standard (BT.601 vs 709 — getting
      this wrong is a subtle, systematic hue shift).
- [ ] De-gamma to linear; per-device vignetting gain map applied; illuminant-interpolated CCM;
      Bradford chromatic adaptation to D65; re-encode.
- [ ] Verified against a physical ColorChecker: mean ΔE00 ≤ 5 across the 24 patches after
      normalization, from a pre-normalization baseline of ≥ 12.
- [ ] Works in fp16 internally; output 8-bit.
- [ ] ≤ 12 ms p95 on the reference device (part of the 25 ms Stage 1 budget shared with D-03).
- [ ] Falls back to D-07 when no calibration exists.

---

### D-06 — Guided white-sheet calibration UX + solver
**Size** L · **Depends on** D-05, A-06 · **Spec** doc 02

**Acceptance criteria**
- [ ] ≤ 20 s flow, fully skippable, with honest framing of the benefit (no dark patterns, no
      nagging).
- [ ] Detects a sufficiently large, uniform, unclipped neutral region; rejects and re-coaches
      otherwise.
- [ ] Solves the 3×3 correction matrix and the radial vignetting map.
- [ ] Estimates the scene illuminant CCT and stores it as the calibration's validity context.
- [ ] Sets `overall_confidence = HIGH` on success.
- [ ] Re-runnable from settings; multiple lighting environments can be captured and interpolated
      between.
- [ ] Fully accessible: works with TalkBack, does not depend on colour perception.

---

### D-07 — Statistical color constancy fallback
**Size** M · **Depends on** D-05

**Acceptance criteria**
- [ ] Shades-of-Grey (Minkowski p=6) with a Grey-Edge cross-check; disagreement between them
      widens the confidence rather than picking one arbitrarily.
- [ ] ≤ 4 ms per frame.
- [ ] Measured against the ColorChecker: better than no correction, and the gap versus D-06 is
      quantified and documented (expected ~2–4 pts of top-1 on colour-sensitive classes).
- [ ] Automatically used whenever user calibration is absent or stale.

---

### D-08 — Noise & MTF profiling
**Size** M · **Depends on** C-01, D-01 · **Spec** doc 02 §"Noise profile"

**Acceptance criteria**
- [ ] 5-frame static burst at 3 ISO levels; temporal σ per channel; `σ²(I) = a·I + b` fitted.
- [ ] MTF50 estimated from a slanted edge or a focus-sweep gradient.
- [ ] Rolling-shutter skew estimated from a gyro-correlated burst.
- [ ] Profile drives: denoise strength in D-05, blur threshold normalization in C-04, and the
      fusion temperature in E-09.
- [ ] Collected opportunistically over the first ~20 captures if not done at onboarding — never
      blocks first use.

---

### D-09 — Device fleet test kit + cross-device spread harness
**Size** L · **Depends on** D-03, D-05 · **Spec** docs 02, 06

**This is the Phase 1 exit gate.**

**Acceptance criteria**
- [ ] Physical kit defined and procured: colour target, ruler card, fixed-distance rig, 40 items.
- [ ] Fleet of ≥ 8 devices covering: flagship, mid, budget, LEGACY, no-intrinsics, quad-Bayer,
      aggressive-ISP, and one with no ARCore.
- [ ] Automated harness computes pairwise cosine distance between post-normalization embeddings
      of the same physical item across devices.
- [ ] **Gate: mean pairwise distance < 0.08**, against a documented pre-normalization baseline.
- [ ] Per-device-tier top-1 reported; spread ≤ 3 pts.
- [ ] Results published as a versioned report per model/profile version; regressions block release.

---

### D-10 — DeviceProfile persistence, invalidation, upload
**Size** M · **Depends on** D-01…D-08, A-08 · **Spec** doc 02 §"Profile lifecycle"

**Acceptance criteria**
- [ ] Proto DataStore persistence with schema versioning.
- [ ] Rebuild triggered by: schema bump, OS major version change, camera hardware change, model
      version change, or 180 days elapsed.
- [ ] Profile is usable immediately after passive enumeration (confidence MEDIUM) — the app never
      blocks on calibration.
- [ ] Opportunistic refinement over the first ~20 captures.
- [ ] Anonymized upload (no `profile_id`, `device_class_id` only) via WorkManager on unmetered
      network; opt-out honored.
- [ ] `degradations` list accurate and complete; a test asserts each degradation is produced under
      its documented condition.
- [ ] Three or more concurrent degradations → `overall_confidence = LOW` → explicit-confirmation
      mode (doc 02).
