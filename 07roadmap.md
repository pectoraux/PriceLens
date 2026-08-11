# 07 — Roadmap

## Shape of the plan

Seven phases over ~32 weeks to a public v1.0 in a first launch region. Each phase has a hard
**exit gate** — a measurable condition, not a date. Phases overlap where dependencies allow.

The order is not negotiable in one respect: **Phase 1 (device profiling and calibration) precedes
Phase 2 (recognition)**. Every accuracy number in this pack assumes frames arrive already
normalized. Building recognition first and retrofitting calibration means re-tuning every
threshold, refitting every calibration, and rebuilding every prototype index — roughly six weeks
of rework. This is the most common way this project fails.

```
Week   1   4   8   12  16  20  24  28  32
P0 ████████
P1     ██████████████
P2           ████████████████
P3                 ██████████████
P4                       ████████████
P5                             ██████████
P6                                  ████████████
```

| Phase | Weeks | Theme | Epics |
|---|---|---|---|
| **P0** | 1–3 | Foundations | A, F (start) |
| **P1** | 3–9 | Device profile & canonical camera space | B, C, D |
| **P2** | 7–14 | Recognition | E, F |
| **P3** | 12–19 | Price & validation UX | G, I |
| **P4** | 16–24 | Trust & integrity | H |
| **P5** | 21–28 | Learning loop | J |
| **P6** | 25–32 | Scale, hardening, launch | K |

---

## Phase 0 — Foundations (weeks 1–3)

**Goal:** a repository an agent can work in productively, and the contracts everything else is
built against.

Deliverables: module skeleton with the enforced dependency graph, CI (build, unit test,
instrumented test on Test Lab, lint, dependency-rule check), Hilt wiring, design system with
uncertainty-display primitives, the frozen OpenAPI contract, the backend skeleton with schema
migrations, and the taxonomy v0 seed.

**Exit gate:**
- [ ] `./gradlew build` green from a clean clone in < 8 minutes.
- [ ] CI runs unit + instrumented tests on 3 device configurations.
- [ ] Dependency-rule check fails the build on a deliberate violation (test it).
- [ ] OpenAPI spec generates both the Kotlin client and the FastAPI server stubs.
- [ ] Taxonomy v0 loaded: ≥ 400 items with names in 5 locales.

---

## Phase 1 — Device profile & canonical camera space (weeks 3–9)

**Goal:** any two devices photographing the same object produce near-identical tensors.

Deliverables: full `CameraCharacteristics` enumeration and derived intrinsics; distortion
correction; canonical reprojection; photometric pipeline with passive matrices, the optional
white-sheet calibration, and statistical fallback; noise/MTF profiling; the delegate parity
harness and compute profile; thermal policy; profile persistence, invalidation, and anonymized
upload.

**Exit gate — this is the most important gate in the plan:**
- [ ] Cross-device embedding distance on the fleet set **< 0.08** (from a 0.20–0.35 baseline).
- [ ] Profile builds on all 8 fleet devices including the LEGACY-level one, with the correct
      `degradations` recorded on each.
- [ ] Delegate parity harness correctly rejects a deliberately-broken delegate.
- [ ] Normalization adds ≤ 25 ms p95 on the reference mid-tier device.
- [ ] Guided calibration completes in ≤ 20 s and is fully skippable.

**Why this gate is worth six weeks:** the difference between 0.30 and 0.08 cross-device embedding
distance is roughly the difference between 78% and 92% top-1 on budget hardware. No amount of
downstream model work recovers it.

---

## Phase 2 — Recognition (weeks 7–14)

**Goal:** the label prediction hits its accuracy and calibration bars.

Deliverables: detector selection and export; embedding model selection and export; on-device
HNSW retrieval; zero-shot text path; prototype path; OCR and barcode; GTIN resolution; fusion
with learned weights; calibration; the abstention policy; the golden set v1 (3,000 images); the
eval harness in CI.

**Exit gate:**
- [ ] Top-1 (non-abstained) ≥ 92% on the golden set, abstention ≤ 15%.
- [ ] Top-5 ≥ 98%.
- [ ] ECE ≤ 0.05 globally and ≤ 0.07 per device tier.
- [ ] Cross-device top-1 spread ≤ 3 pts.
- [ ] No class with ≥ 20 golden images below 70%.
- [ ] Capture → label p95 ≤ 450 ms on the reference device.
- [ ] Barcode path resolves ≥ 90% of test GTINs to the correct SKU.

---

## Phase 3 — Price & validation UX (weeks 12–19)

**Goal:** the app answers "is this a fair price?" and captures the user's answer.

Deliverables: portion and scale estimation with uncertainty propagation; the density LUT; unit
normalization including locality vernacular units; the hierarchical price model and nightly fit;
the price serving path with cold-start pooling; offline cached bands; the validation UI for both
label and price; correction flows; the manual item picker with FTS; the contribute-only flow;
receipt/price-tag capture.

**Exit gate:**
- [ ] Band coverage ≥ 88% on the backtest; MdAPE ≤ 12% on mature cells, ≤ 25% cold-start.
- [ ] Portion estimation refuses to assert mass when relative error > 25% (verified on the
      reference kit).
- [ ] Validation completion rate ≥ 55% in internal dogfood.
- [ ] Full offline flow: capture → label → cached band → queued submission → sync on reconnect.
- [ ] TalkBack passes on capture and validation; both prediction and confidence are announced.

---

## Phase 4 — Trust & integrity (weeks 16–24)

**Goal:** the database survives contact with people who want to corrupt it.

Deliverables: Play Integrity and hardware key attestation; capture sealing and nonce flow;
anti-rephotography detectors; geo-integrity; reputation; robust aggregation with influence caps;
consensus state machine; collusion detection; change-point detection; the arbitration queue; the
red-team suite in CI; person redaction; the appeals path.

**Exit gate:**
- [ ] Red-team suite: **zero** poisoned labels promoted to production.
- [ ] ≥ 95% of injected coordinated attacks detected before affecting a published band.
- [ ] ≤ 5% of genuine regime shifts misclassified as attacks.
- [ ] False-positive integrity blocks ≤ 0.5% on dogfood traffic.
- [ ] Release build fails to compile with attestation stubs on the classpath (verified).
- [ ] Person redaction verified: no frame containing a detectable face is uploaded unredacted.

---

## Phase 5 — Learning loop (weeks 21–28)

**Goal:** corrections measurably improve the app, safely.

Deliverables: the curation pipeline; robust geometric-median prototype updates; influence tracing
and reversal; near-duplicate detection; the golden-set regression gate; canary rollout with
automatic rollback; shadow mode; the embedding adapter and its training pipeline; taxonomy
proposal handling; the truthful-elicitation experiment.

**Exit gate:**
- [ ] Demonstrated: 500 confirmed corrections on a held-out weak class raise its top-1 by ≥ 8 pts
      with no regression elsewhere.
- [ ] Influence reversal verified: removing a contributor's contributions restores the prior
      index state.
- [ ] Shadow mode running with ≥ 50k captures compared.
- [ ] Automatic rollback fires correctly in a simulated regression.
- [ ] Periodic full rebuild produces an index within tolerance of the incremental one.

---

## Phase 6 — Scale, hardening & launch (weeks 25–32)

**Goal:** ship it, to real users, in real localities.

Deliverables: locality onboarding playbook; public-source seeding for launch regions;
localization completion; accessibility audit; performance and battery optimization; baseline
profiles; the partner/analyst API; privacy and legal review (GDPR/local); the transparency
report; Play Store listing, staged rollout, on-call runbooks, and dashboards.

**Exit gate:**
- [ ] 25 localities meet the maturity threshold.
- [ ] Crash-free sessions ≥ 99.5% over 2 weeks of open beta.
- [ ] p50 app-open → prediction ≤ 2.0 s, p95 ≤ 4.0 s on the fleet.
- [ ] All five launch locales complete, reviewed by native speakers, vernacular names included.
- [ ] Privacy review signed off; data deletion and export verified end-to-end.
- [ ] Runbooks exist for: model rollback, attack response, locality quarantine, backend incident.

---

## Critical path

```
A-01 repo/modules
  └─ A-05 OpenAPI contract ──────────────────────┐
  └─ C-01 CameraX + Camera2 interop              │
       └─ D-01 characteristics enumeration       │
            └─ D-03 canonical reprojection ◀── the true bottleneck
                 └─ D-05 photometric pipeline    │
                      └─ B-05 delegate parity    │
                           └─ E-03 embedding ────┤
                                └─ E-06 retrieval│
                                     └─ E-09 fusion + calibration
                                          └─ I-02 validation UI
                                               └─ G-06 price serving ◀── needs A-05 too
                                                    └─ H-04 capture sealing
                                                         └─ J-02 curation pipeline
                                                              └─ K-06 launch
```

**The bottleneck is D-03 → E-03.** Everything downstream of the canonical frame is blocked on it,
and it is the ticket with the most subtle failure modes (a silently wrong principal point
produces plausible-looking output and quietly costs accuracy for months). Give it the most
review attention and the strongest test coverage in the project.

## Parallelizable work

These have no dependency on the critical path and should be started early to keep everyone busy
while D-03 is in flight:

| Work | Can start | Why it's independent |
|---|---|---|
| F-01/F-02 public source connectors | Week 1 | Pure backend, no client dependency |
| F-03 taxonomy construction | Week 1 | Data work; the single longest-lead item |
| E-11 golden set collection | Week 4 | Annotation is slow; start before the models exist |
| H-01/H-02 attestation | Week 6 | Independent of ML entirely |
| G-02 density LUT | Week 6 | Reference data compilation |
| K-02 localization | Week 10 | Needs the taxonomy, not the models |

**Start F-03 and E-11 in week 1 regardless of everything else.** Taxonomy construction and
golden-set annotation are the longest-lead, least-compressible items in the plan. Every week they
start late is a week the Phase 2 gate slips, and no amount of engineering throughput recovers it.

## Staffing

| Role | FTE | Phases |
|---|---|---|
| Android engineer (senior, camera/ML) | 2 | P0–P6 |
| ML engineer (CV) | 1.5 | P1–P5 |
| ML engineer (pricing/statistics) | 1 | P3–P5 |
| Backend engineer | 1.5 | P0–P6 |
| Data/annotation ops | 0.5 | P2–P6 |
| Product/design | 1 | P0–P6 |
| Trust & safety (part-time from P4) | 0.5 | P4–P6 |

With Gemini agent assistance the *implementation* time compresses meaningfully — but the gates
do not. Model evaluation, annotation, device-fleet testing, and consensus tuning are bounded by
data collection and physics, not by typing speed. Expect the agent to compress P0 and the UI
portions of P3 substantially, and P1's calibration validation barely at all.

## What to cut if you must

In priority order, the things to drop while keeping a coherent product:

1. **Federated learning (J-08).** Already recommended as deferred. Zero cost to cut.
2. **Truthful elicitation (J-07).** Valuable but the consensus layer works without it.
3. **ARCore depth (G-01 partial).** Fall back to reference-object and explicit-unit paths.
4. **Price history / explore feature (I-09).** Nice, not core to the fair-price question.
5. **Adapter fine-tuning (J-03).** Prototype retrieval alone carries most of the learning benefit.
6. **Locality vernacular unit conversion (G-05).** Record vernacular units natively instead.

**What you cannot cut:** anything in Phase 1, the abstention policy, the price *band* (as
opposed to a point estimate), consensus confirmation before publication, or the golden-set
regression gate. Each of those is load-bearing for the promise that the numbers are trustworthy,
and that promise is the entire product.
