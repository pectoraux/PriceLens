# Tickets — Phase 4, 5 & 6

Epics H (Trust & Integrity), J (Learning Loop), K (Scale, Launch & Ops).

---

# EPIC H — Trust & Integrity

### H-01 — Hardware key attestation + StrongBox install key
**Size** L · **Depends on** A-01 · **Spec** doc 04 §L2

**Acceptance criteria**
- [ ] EC P-256 install key generated in StrongBox, falling back to TEE, then software — each with
      its tier recorded.
- [ ] Key is non-exportable, requires no user auth, and is bound to the app signature.
- [ ] Attestation certificate chain retrieved and validated server-side against Google's root.
- [ ] Verified boot state and security patch level extracted from the attestation extension.
- [ ] Key survives app updates; it is regenerated (with a re-enrolment flow) on device restore.
- [ ] Tested on devices without StrongBox — degrades to `STANDARD`, never fails hard.

---

### H-02 — Play Integrity + build-time stub assertion
**Size** M · **Depends on** H-01, A-10 · **Spec** doc 04 §L2

**Acceptance criteria**
- [ ] Integrity token requested with a **request hash binding the verdict to the specific
      submission payload**.
- [ ] Server decodes and validates the verdict; `deviceIntegrity`, `appIntegrity`, and
      `accountDetails` map to the doc 04 attestation tiers.
- [ ] `appIntegrity != PLAY_RECOGNIZED` → rejected outright (repackaged APK).
- [ ] Token caching within its validity window; graceful handling of Play Services absence
      (tier `UNVERIFIED`, not a crash).
- [ ] **A `prod` release build fails to compile if the attestation stub implementation is on the
      classpath.** Verified by a CI job that attempts it and expects failure.
- [ ] The UI shows the same confirmation regardless of tier — down-weighting is silent (doc 04).

---

### H-03 — Server-issued nonce flow
**Size** S · **Depends on** A-09, H-01

**Acceptance criteria**
- [ ] `POST /v1/capture/nonce` issues single-use nonces with a 10-minute TTL.
- [ ] Nonces stored in Redis with atomic consume-on-use; replay returns HTTP 409.
- [ ] Client pre-fetches a small nonce pool so capture never waits on the network.
- [ ] Expired-nonce submissions from the offline queue follow the doc 05 deferred path — re-signed
      with a fresh nonce and flagged `offline_deferred`, not discarded.

---

### H-04 — Capture sealing + signing
**Size** L · **Depends on** H-01, H-03, C-07, D-10 · **Spec** doc 04 §L3

**Acceptance criteria**
- [ ] `CaptureSeal` built with every field in doc 04: nonce, canonical frame hash, evidence hash,
      profile IDs, sensor timestamp, camera metadata, the 500 ms IMU window, geohash-6, versions.
- [ ] Signed with the attested install key; signature verified server-side.
- [ ] Sealing happens **inside the capture pipeline** — there is no API that lets a caller
      construct a seal for an arbitrary image.
- [ ] Gallery imports are structurally incapable of producing a `LIVE_CAPTURE` seal.
- [ ] Seal stored with the observation for audit and retained after image expiry.
- [ ] Tampering with any sealed field invalidates the signature — tested field by field.

---

### H-05 — Anti-rephotography detectors
**Size** L · **Depends on** D-08, C-06 · **Spec** doc 04 §L3

**Acceptance criteria**
- [ ] Moiré detection via FFT peak analysis of the luminance channel.
- [ ] PWM banding detection via row-mean periodicity.
- [ ] Depth flatness check where ARCore depth is available.
- [ ] Specular-signature and IMU micro-tremor checks (absence of handheld tremor is suspicious).
- [ ] Combined into a `synthetic_evidence_score`; **tuned for high precision** — detections
      down-weight the provenance tier, they never hard-reject.
- [ ] Evaluated on a purpose-built set of screen re-photographs vs genuine captures:
      ≥ 85% detection at ≤ 1% false positive.
- [ ] ≤ 30 ms, runs off the capture critical path.

---

### H-06 — Geo-integrity
**Size** L · **Depends on** C-06, F-04 · **Spec** doc 04 §L4

**Acceptance criteria**
- [ ] `isMock` / `isFromMockProvider` checked; mock-location apps enumerated.
- [ ] GNSS raw measurement plausibility: C/N₀ distribution sanity (spoofed fixes are
      implausibly uniform).
- [ ] Fused location cross-checked against cell/Wi-Fi-derived coarse location; large disagreement
      is disqualifying.
- [ ] Travel plausibility per account: > 900 km/h impossible, > 200 km/h suspicious.
- [ ] Produces a continuous `geo_confidence`, not a boolean.
- [ ] **Precise coordinates are used in memory for these checks and never persisted.** Only
      geohash-6 is stored. Tested.
- [ ] Time is server-authoritative; client clocks recorded but never trusted.

---

### H-07 — Trust weight scorer
**Size** M · **Depends on** H-02, H-04, H-06 · **Spec** doc 04 §L6

**Acceptance criteria**
- [ ] `w = reputation × attestation_tier × provenance_tier × geo_confidence ×
      device_reliability × freshness_decay`, computed on intake.
- [ ] `device_reliability` floored at 0.6 (doc 02 fairness constraint) — cheap phones must not be
      excluded from the record.
- [ ] Every factor logged for auditability so a weight can be explained after the fact.
- [ ] Weight is recomputed when reputation changes, not frozen at intake.
- [ ] Property tests: weight is monotonic in each factor and bounded to [0, 1.2].

---

### H-08 — Reputation system
**Size** M · **Depends on** H-07 · **Spec** doc 04 §L5

**Acceptance criteria**
- [ ] Beta-Bernoulli per contributor **per item category**.
- [ ] The **lower confidence bound** (z = 1.28) is what is used, not the mean — 2/2 must not
      outrank 90/100.
- [ ] Disagreements weighted 2.5× agreements; 180-day decay toward the prior.
- [ ] New accounts start in probation with near-zero weight until agreement history accumulates.
- [ ] Reputation is never exposed via any API or UI.
- [ ] Simulation tests: an account that behaves well for 90 days then attacks cannot move a band.

---

### H-09 — Robust aggregation + influence caps
**Size** L · **Depends on** H-07, H-08, G-06 · **Spec** doc 04 §L6

**Acceptance criteria**
- [ ] Weighted median and weighted quantiles on **log-price**. No mean anywhere (A-04 rule
      enforces this in code).
- [ ] MAD-based outlier rejection at 3.5×; MAD, not σ.
- [ ] **Contributor influence cap: no contributor exceeds 15% of a cell's total weight**,
      regardless of submission volume. This is the single most effective defence against flooding.
- [ ] Shrinkage toward the hierarchical prior proportional to 1/n.
- [ ] Recomputation is incremental and idempotent; a full recompute reproduces the incremental
      result exactly.
- [ ] Simulation: 50 fabricated observations from 3 accounts move the published band by < 3%.

---

### H-10 — Consensus state machine + arbitration queue
**Size** L · **Depends on** H-09 · **Spec** doc 04 §L7

**Acceptance criteria**
- [ ] `SUBMITTED → PENDING → {CONFIRMED | DISPUTED | OUTLIER}` implemented with the exact doc 04
      confirmation conditions (≥ 3 independent contributors, 14-day window, ±25% log tolerance,
      combined weight ≥ 2.0, ≥ 1 at STANDARD+).
- [ ] Collusion-cluster members do not count as independent.
- [ ] DISPUTED observations enter an arbitration queue resolved by photographic and receipt
      evidence, not by majority vote.
- [ ] A disputed cell shows **no verdict** in the app until resolved.
- [ ] Median time to confirmation in mature localities ≤ 72 h, measured.
- [ ] State transitions are event-sourced and auditable.

---

### H-11 — Red-team suite in CI
**Size** L · **Depends on** H-09, H-10 · **Spec** doc 04, doc 06

**The Phase 4 exit gate.**

**Acceptance criteria**
- [ ] Simulated attacks covering T1–T7 from doc 04: trolls, interested sellers, Sybil farms,
      collusion rings, label poisoning, replay/forgery, geo-spoofing.
- [ ] **Gate: zero poisoned labels reach a promoted index.**
- [ ] ≥ 95% of coordinated price attacks detected before affecting a published band.
- [ ] **Genuine regime shifts are injected alongside attacks**; ≤ 5% may be misclassified as
      attacks. A defence that rejects real inflation is a bug, and this is where it gets caught.
- [ ] False-positive integrity blocks ≤ 0.5% on simulated legitimate traffic.
- [ ] Runs on every change to consensus, aggregation, or curation code.
- [ ] New attack scenarios are added when real-world attacks are observed — the suite is living.

---

### H-12 — Collusion + change-point detection
**Size** XL · **Depends on** H-10, H-11 · **Spec** doc 04 §L8, §L6

**Acceptance criteria**
- [ ] Contributor co-occurrence graph built from shared items, localities, and time windows.
- [ ] Louvain community detection; per-cluster deviation from non-cluster consensus computed.
- [ ] Detected clusters have their **combined weight collapsed to that of a single contributor**
      — not banned. A ban tells the ring it was caught; collapsing weight makes the attack
      pointless while leaving them uninformed.
- [ ] Clusters larger than 10 require human review before action (legitimate groups exist).
- [ ] Bayesian online change-point detection on cell log-price series.
- [ ] The doc 04 regime-vs-attack discriminator implemented: sustained + many independent
      contributors + regionally correlated → widen the prior; abrupt + few contributors +
      locally isolated → quarantine.
- [ ] Regime-shift tracking lag ≤ 2 weeks on the backtest's real inflation episodes.

---

# EPIC J — Learning Loop

### J-01 — Observation ingestion → embedding retention
**Size** M · **Depends on** H-04, A-09

**Acceptance criteria**
- [ ] Submitted embeddings stored int8-quantized against the observation.
- [ ] `predicted_*` fields retained alongside the user's answer — this pairing is what makes
      real-world accuracy measurable at all.
- [ ] Perceptual hashes indexed for dedupe and retained **after** image expiry, so replay
      detection outlives the image.
- [ ] Model version provenance recorded per observation; embeddings from different backbone
      versions are never mixed.

---

### J-02 — Curation pipeline
**Size** L · **Depends on** J-01, H-10 · **Spec** doc 04 §L9

**Acceptance criteria**
- [ ] The doc 04 flow implemented end to end: integrity → consensus → influence cap → dedupe →
      candidate.
- [ ] Near-duplicate detection by perceptual hash **and** embedding distance — 40 photos of the
      same physical item count once.
- [ ] Per-contributor influence cap of 15% per class, enforced at candidate-build time.
- [ ] **No path exists by which a correction reaches a model without traversing this pipeline.**
      Asserted by an architectural test, not just by convention.
- [ ] Every candidate records the full contributor set that produced it (required by J-09).

---

### J-03 — Prototype centroid update
**Size** L · **Depends on** J-02, E-06 · **Spec** doc 04

**Acceptance criteria**
- [ ] Centroids computed as the **robust geometric median** (Weiszfeld), not the arithmetic mean
      — one poisoned embedding must not drag the centroid.
- [ ] Up to 32 centroids per item via k-means, capturing within-class variation.
- [ ] Regional centroid variants where visual variation warrants them.
- [ ] Candidate index built and diffed against the current one.
- [ ] **Class-level anomaly monitor:** a centroid moving more than the threshold in one cycle
      triggers human review regardless of how it passed the gates.

---

### J-04 — Golden-set regression gate automation
**Size** M · **Depends on** J-03, E-12 · **Spec** doc 06

**Acceptance criteria**
- [ ] Every candidate index is evaluated against the golden set automatically.
- [ ] **Any class with ≥ 20 golden images regressing more than 0.5 pts blocks the update
      entirely.** No override without documented sign-off.
- [ ] All doc 06 release gates checked, not just mean accuracy.
- [ ] The report is published and linked from the promotion decision.

---

### J-05 — Canary rollout + automatic rollback
**Size** L · **Depends on** J-04, B-03 · **Spec** doc 06 §"Rollout"

**Acceptance criteria**
- [ ] 2% → 10% → 50% → 100% ramp with the doc 06 dwell times.
- [ ] Cohort assignment is stable per install and independent of device class (otherwise the
      canary is biased).
- [ ] Automatic rollback on any doc 06 trigger, evaluated hourly.
- [ ] Rollback is a manifest change taking effect on the next client poll — **not** an app store
      release.
- [ ] A simulated regression is verified to trigger rollback end to end.

---

### J-06 — Shadow mode
**Size** L · **Depends on** B-01, J-01 · **Spec** doc 06

**Acceptance criteria**
- [ ] The candidate model runs alongside the incumbent on a sampled fraction of real captures;
      its output is logged and **never shown**.
- [ ] Battery and latency impact bounded; shadow inference is skipped under thermal pressure.
- [ ] Comparison over ≥ 50k captures: agreement rate, abstention delta, latency delta.
- [ ] **The user-corrected subset is reported separately** — which model matched the user's
      answer is the most valuable signal in the system, being the only measurement against real
      in-the-wild ground truth.
- [ ] A model that wins offline but loses on the corrected subset is blocked from promotion.

---

### J-07 — Truthful elicitation experiment
**Size** M · **Depends on** I-03, H-08 · **Spec** doc 04 §L7

**Acceptance criteria**
- [ ] Optional second question: "what do you think most people here pay?"
- [ ] BTS-lite information scoring implemented: surprisingly-common answers score higher than
      merely-popular ones.
- [ ] A/B tested against agreement-only scoring for its effect on band accuracy.
- [ ] **Explicitly measured:** whether agreement-only scoring causes the database to converge on
      its own displayed estimate rather than on reality. That anchoring failure is the reason
      this ticket exists.
- [ ] Adds ≤ 1 extra tap and is skippable.

---

### J-08 — Embedding adapter training pipeline
**Size** XL · **Depends on** J-02, E-03 · **Spec** doc 03

**Acceptance criteria**
- [ ] A 512×512 residual linear adapter trained contrastively on confirmed pairs — the backbone
      is **not** fine-tuned, so stored embeddings stay valid via re-projection.
- [ ] Gradient norm clipping applied (a poisoning bound, not just a stability trick).
- [ ] Adapter version triggers a full index re-projection, which is a matrix multiply, not a
      re-embed.
- [ ] **Demonstrated benefit: 500 confirmed corrections on a held-out weak class raise its top-1
      by ≥ 8 pts with no regression elsewhere.** This is the Phase 5 exit gate.
- [ ] Training is reproducible: data snapshot hash, commit, and hyperparameters recorded.

---

### J-09 — Influence tracing, reversal, periodic rebuild
**Size** L · **Depends on** J-03 · **Spec** doc 04

**Acceptance criteria**
- [ ] Every centroid retains its contributor set (from J-02).
- [ ] Given a contributor found abusive, their contributions are subtracted and affected
      centroids rebuilt. **Poisoning must be reversible** — and reversibility cannot be
      retrofitted, which is why provenance retention starts at J-01.
- [ ] Verified: removing a contributor restores the prior index state within tolerance.
- [ ] Full rebuild from the confirmed corpus every 90 days, shedding incremental drift.
- [ ] A rebuild that diverges from the incremental index beyond tolerance raises an alert — that
      divergence is itself evidence of slow poisoning.

---

# EPIC K — Scale, Launch & Ops

### K-01 — Locality onboarding playbook + seeding automation
**Size** M · **Depends on** F-02, F-08

**Acceptance criteria**
- [ ] A documented, repeatable process to bring a new locality from `seeding` to `learning`.
- [ ] Automated public-source seeding for a named geography.
- [ ] Taxonomy coverage validated against local market inventory before launch.
- [ ] Locality-level kill switch to quarantine an area under attack without a deploy.
- [ ] Rollout is locality-by-locality. **Never a global launch** — the cold-start risk is managed
      by sequencing, and a global launch forfeits that control.

---

### K-02 — Localization completion + vernacular review
**Size** L · **Depends on** F-03, I-10

**Acceptance criteria**
- [ ] All UI strings in English, French, Spanish, Hindi, Swahili — no hardcoded strings (lint
      enforced).
- [ ] Taxonomy vernacular names reviewed by native speakers **in the launch localities**, not by
      remote translators. A market name is local knowledge.
- [ ] RTL layout support verified even though no launch locale needs it (cheap now, expensive
      later).
- [ ] Currency, number, and date formatting driven by locality, not device locale.
- [ ] Pseudolocalization run to catch truncation.

---

### K-03 — Performance & battery optimization + baseline profiles
**Size** L · **Depends on** I-01, B-07 · **Spec** doc 06

**Acceptance criteria**
- [ ] All doc 06 latency and resource gates met on the reference mid-tier device.
- [ ] Baseline Profiles generated in CI for the capture → predict path.
- [ ] Macrobenchmark suite for startup, capture, and inference, tracked over time.
- [ ] Battery ≤ 4% per 100 captures.
- [ ] Peak memory ≤ 380 MB; no OOM on a 3 GB device.
- [ ] StrictMode clean; no jank frames above threshold in the capture flow.

---

### K-04 — Partner/analyst API + rate limiting
**Size** M · **Depends on** G-06

**Acceptance criteria**
- [ ] Read-only aggregate API over published cells, with API keys and quotas.
- [ ] **Only aggregate data is exposed.** No individual observations, no contributor data, no
      seller information — ever.
- [ ] Abuse-resistant rate limiting and bulk-extraction detection (threat T8).
- [ ] Documented with examples; terms of use and attribution requirements stated.

---

### K-05 — Privacy, legal & data-rights implementation
**Size** L · **Depends on** A-09, H-04 · **Spec** doc 00

**Acceptance criteria**
- [ ] Data export and deletion implemented and verified end to end.
- [ ] Deletion unlinks contributed observations from the account while retaining them in
      aggregate — and the privacy policy says exactly that, plainly.
- [ ] Evidence image retention (180 days, regionally configurable) enforced by an automated job.
- [ ] Play Data Safety declaration matches actual behaviour — verified against the code, not the
      intent.
- [ ] Legal review completed for: price data publication, public-source licence obligations
      (ODbL from F-07), and the launch region's data protection regime.
- [ ] **No seller identity exists anywhere in the schema** — asserted by a schema test.

---

### K-06 — Play Store release + staged rollout
**Size** M · **Depends on** K-03, K-05

**Acceptance criteria**
- [ ] Store listing, screenshots, and description that do not overclaim accuracy.
- [ ] Closed beta → open beta → staged production rollout (5% → 20% → 50% → 100%).
- [ ] Crash-free sessions ≥ 99.5% sustained over 2 weeks of open beta before production.
- [ ] Halt criteria defined and monitored at each stage.
- [ ] Pre-launch report issues triaged; in-app update flow implemented.

---

### K-07 — Observability dashboards + alerting
**Size** M · **Depends on** B-08, H-07 · **Spec** doc 06

**Acceptance criteria**
- [ ] Dashboards for every doc 06 continuous-monitoring signal.
- [ ] **Alerts fire on rate of change, not absolute level** — a locality that has always had 30%
      abstention is a known cold-start; one that went 8% → 25% in a week is a regression.
- [ ] Correction rate by device class alerts on OS-update-induced normalization regressions.
- [ ] Consensus disagreement rate alerts route to trust & safety, not to engineering.
- [ ] Every alert links to its runbook.

---

### K-08 — On-call runbooks + transparency report
**Size** M · **Depends on** K-07

**Acceptance criteria**
- [ ] Runbooks for: model rollback, coordinated attack response, locality quarantine, backend
      incident, public-source outage, and OS-update accuracy regression.
- [ ] Each runbook rehearsed at least once in a game day before launch.
- [ ] Escalation paths and on-call rotation defined.
- [ ] The doc 04 transparency report is generated and published: submissions received, share
      quarantined, appeals received and upheld.
- [ ] An appeals path exists with a human reviewer and a stated response time.
