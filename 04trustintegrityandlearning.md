# 04 — Trust, Integrity & Learning

> The product is a database of what things actually cost. Anyone with a commercial interest in
> that number — a seller who wants the recorded price high, a competitor who wants a rival's
> area to look expensive — has a motive to corrupt it. The learning loop that makes the app
> smarter is also the attack surface. This document is how both survive.

## Threat model

| # | Adversary | Goal | Capability |
|---|---|---|---|
| T1 | Casual troll | Amusement | One device, one account, low effort |
| T2 | Interested seller | Inflate recorded prices in their locality | Several accounts, real device, real location, patience |
| T3 | Sybil farmer | Bulk-submit fabricated observations | Emulators, rooted devices, automation, scripted image submission |
| T4 | Collusion ring | Coordinated agreement to shift a locality's band | Multiple real users, real devices, real locations — *the hardest case* |
| T5 | Model poisoner | Corrupt label→class associations | Repeated confident mislabelling of a target class |
| T6 | Replay/forgery | Submit synthetic or re-photographed evidence | Screen re-photography, generated images, replayed payloads |
| T7 | Geo-spoofer | Attribute observations to a locality they are not in | Mock location providers, VPN, rooted device |
| T8 | Scraper | Bulk-extract the price database | API abuse |

**T4 is the one that matters.** T1–T3 and T6–T7 are solvable with attestation and provenance.
A ring of real people with real phones standing in a real market and lying in unison cannot be
detected by any device signal — it can only be caught statistically, and that is why the
consensus layer matters more than the crypto layer.

## Defence layers

Each layer independently reduces attack value. None is trusted alone.

```
┌─ L1 IDENTITY COST ────────── phone verification, per-account rate limits
├─ L2 DEVICE INTEGRITY ─────── Play Integrity, hardware key attestation, StrongBox
├─ L3 PROVENANCE ──────────── in-app capture sealing, nonce, anti-rephotography
├─ L4 GEO/TIME INTEGRITY ──── mock detection, travel plausibility, server-authoritative time
├─ L5 REPUTATION ──────────── Beta-Bernoulli agreement scoring, slow to earn, fast to lose
├─ L6 ROBUST AGGREGATION ──── weighted median, MAD rejection, hierarchical shrinkage
├─ L7 CONSENSUS ──────────── independent peer confirmation before publication
├─ L8 COLLUSION DETECTION ─── graph clustering, correlated-behaviour analysis
└─ L9 CURATION GATE ───────── nothing reaches a model without passing all of the above + regression
```

---

### L1 — Identity cost

- Reading prices requires no account. Contributing does. That asymmetry is deliberate: it keeps
  the product frictionless for its primary use while putting a real cost on writing.
- Phone-number verification, one account per number, cooldown on re-registration.
- Rate limits: 25 observations/day/account, 8/hour, with per-locality caps. Legitimate heavy
  contributors get raised limits *after* establishing a reputation, not before.
- New accounts enter a **probation window**: their observations are recorded but carry near-zero
  consensus weight until they have accumulated agreement history. This alone removes most of the
  value of bulk account creation.

### L2 — Device integrity

**Play Integrity API** on every submission batch:

| Verdict field | Use |
|---|---|
| `deviceIntegrity` (`MEETS_STRONG_INTEGRITY` / `MEETS_DEVICE_INTEGRITY` / `MEETS_BASIC_INTEGRITY` / none) | Sets the attestation tier |
| `appIntegrity` (`PLAY_RECOGNIZED`) | Rejects modified/repackaged APKs outright |
| `accountDetails` (`LICENSED`) | Weak Sybil signal |
| Request hash | Binds the verdict to *this* submission payload |

**Hardware key attestation.** At install, generate an EC P-256 key in StrongBox (fall back to
TEE, then to software with a tier penalty), non-exportable, with attestation. Every submission
is signed with it. The attestation certificate chain proves the key lives in real secure
hardware on a device with a verified boot state.

Attestation tiers → weight multipliers:

| Tier | Condition | Weight |
|---|---|---|
| `STRONG` | StrongBox key + `MEETS_STRONG_INTEGRITY` + verified boot GREEN | 1.0 |
| `STANDARD` | TEE key + `MEETS_DEVICE_INTEGRITY` | 0.85 |
| `BASIC` | `MEETS_BASIC_INTEGRITY` only | 0.4 |
| `UNVERIFIED` | Software key, emulator, or no verdict | 0.05 |

`UNVERIFIED` submissions are accepted and stored — banning them outright pushes attackers to
harder-to-detect methods and excludes users on legitimately unusual devices — but at 0.05 weight
they cannot move a band. **Never tell the attacker their tier.** The UI shows the same
confirmation to everyone. Silent down-weighting is far more effective than a visible block,
which just tells the attacker to iterate.

### L3 — Provenance

**Capture sealing.** At capture time, `:core:trust` builds and signs:

```
CaptureSeal {
  nonce                 // server-issued, 10-minute TTL, single-use
  canonical_frame_hash  // SHA-256 of the normalized frame
  evidence_image_hash
  device_profile_id, device_class_id
  sensor_timestamp_ns   // SENSOR_TIMESTAMP from the capture result
  camera_metadata       // ISO, exposure, AF state, AWB gains, focus distance
  imu_window            // 500 ms of gyro/accel around capture
  geohash6, geo_confidence
  app_version, model_versions
}  → signed with the attested install key
```

The server-issued nonce is the anti-replay core: a payload cannot be prepared in advance or
resubmitted.

**Provenance tiers:**

| Tier | Condition | Weight |
|---|---|---|
| `LIVE_CAPTURE` | Sealed in-app capture, valid nonce, IMU consistent with handheld | 1.0 |
| `RECEIPT_OCR` | Sealed capture of a receipt/price tag, price read by OCR not typed | 1.15 — *higher than a typed price, because it is much harder to fabricate* |
| `IMPORTED` | Gallery image | 0.1 |
| `NO_IMAGE` | Price submitted without a photo | 0.25 |

**Anti-rephotography** (defeating "photograph a picture of a tomato on a screen"):
- **Moiré detection.** FFT of the luminance channel; screen re-photography leaves characteristic
  periodic peaks from the display's pixel grid beating against the sensor's CFA.
- **PWM banding.** Many displays dim by pulse-width modulation, producing horizontal banding at
  short exposures; detect via row-mean periodicity.
- **Depth flatness.** Where ARCore depth is available, a screen is a plane. A tomato is not.
- **Specular signature.** Screens have a distinctive uniform gloss response; matte produce does not.
- **IMU consistency.** A handheld capture has a characteristic micro-tremor spectrum. A phone in
  a rig has none. Absence of tremor is suspicious.

None of these is decisive alone; combined into a `synthetic_evidence_score`, they gate the
provenance tier. False positives are costly (a legitimate user blocked), so the threshold is set
for high precision and detections *down-weight* rather than reject.

### L4 — Geo and time integrity

- `Location.isMock` / `isFromMockProvider`, and the `ACCESS_MOCK_LOCATION` app enumeration.
- **GNSS raw measurements** (`GnssMeasurement`, API 24+): real satellite C/N₀ distributions are
  hard to synthesize. A spoofed fix usually has implausibly uniform signal strengths.
- **Cross-check** fused location against the cell tower and Wi-Fi-derived coarse location. A GPS
  fix in Nairobi with a Wi-Fi BSSID cluster last seen in Lagos is disqualifying.
- **Travel plausibility.** Consecutive submissions implying > 900 km/h are impossible; 200 km/h
  is suspicious. Maintained per account as a simple kinematic check.
- **Time is server-authoritative, always.** The client clock is recorded but never trusted. The
  nonce TTL enforces recency independently.
- **Only geohash-6 is persisted.** Precise coordinates are used for the integrity checks in
  memory on the server and then discarded — the integrity requirement and the privacy
  requirement are reconciled by not storing the intermediate.

### L5 — Reputation

Per contributor, per item-category (a user reliable on produce is not automatically reliable on
packaged goods):

```
Beta(α, β)   α += agreement_weight on confirmation
             β += disagreement_weight on contradiction
reputation = mean of Beta(α + 1, β + 1)  with a lower-confidence-bound adjustment:

   r = (α+1)/(α+β+2) − z·sqrt( var(Beta) )      z = 1.28   (P10 of the posterior)
```

Using the lower bound, not the mean, means a user with 2/2 agreements does not immediately
outrank one with 90/100. Reputation must be *slow to earn and fast to lose*: disagreements carry
2.5× the weight of agreements, and reputation decays toward the prior with a 180-day half-life so
a dormant-then-abusive account cannot bank credibility.

Reputation is never displayed. A visible score is a target to game and a status hierarchy we do
not want.

### L6 — Robust aggregation

**Never a mean. Ever.** For a `(locality, item, unit, week)` cell:

1. **Weight** each observation: `w = reputation × attestation_tier × provenance_tier ×
   geo_confidence × device_reliability × freshness_decay`.
2. **Reject outliers** by MAD: drop observations where `|x − median| > 3.5 × MAD`, computed on
   log-price. MAD, not standard deviation — an attacker can inflate σ to hide inside it, but
   cannot inflate the MAD without controlling the majority.
3. **Weighted median** and weighted quantiles for the band.
4. **Contributor influence cap.** No single contributor may account for more than 15% of a
   cell's total weight, regardless of volume. This alone defeats most of T2 and T3 — flooding
   stops working.
5. **Shrink** toward the hierarchical prior (doc 03) in proportion to `1/n`.

**Change-point detection, so real price movements are not treated as attacks.** Bayesian online
change-point detection on the cell's log-price series. A shift that is (a) sustained over
multiple periods, (b) present across *many independent contributors*, and (c) correlated with
neighbouring localities or the same item regionally, is a **regime change**: widen the prior and
accept the new level. A shift that is abrupt, concentrated in few contributors, and locally
isolated is an **attack**: quarantine.

Getting this distinction wrong in the conservative direction is worse than it sounds — a model
that rejects genuine inflation will report stale, too-low prices and tell users they are being
overcharged when they are not. Explicitly tested in ticket H-11 with injected real inflation
episodes alongside injected attacks.

### L7 — Consensus

An observation's lifecycle: `SUBMITTED → PENDING → {CONFIRMED | DISPUTED | OUTLIER}`

**CONFIRMED** requires:
- ≥ 3 independent observations of the same `(locality, item, unit)` within a 14-day window,
- from ≥ 3 distinct contributors with no collusion-cluster membership,
- agreeing within a tolerance band (± 25% on log-price, item-category-tuned),
- with combined weight ≥ 2.0,
- and at least one at attestation tier `STANDARD` or better.

**DISPUTED** observations do not vanish. They go to an arbitration queue where the tie-break is
photographic evidence and receipt OCR — the objective sources. A disputed cell shows no verdict
in the app until resolved.

**Truthful elicitation (Phase 5, ticket J-07).** Optionally, alongside "what did you pay?", ask
"what do you think most people here pay?". Scoring contributions by *information* (Bayesian Truth
Serum: a surprisingly-common answer is more likely honest) rather than by mere agreement removes
the incentive to simply echo the displayed band — which is the subtle failure mode of any
agreement-based reputation system, and would otherwise cause the database to slowly converge on
its own initial guess regardless of reality.

### L8 — Collusion detection

Build a contributor graph where edge weight reflects co-occurrence: contributors who repeatedly
submit the same items, in the same localities, within short time windows, agreeing more with
each other than with the population.

- **Community detection** (Louvain) over that graph.
- For each detected cluster, compute the **cluster's deviation** from the non-cluster consensus.
  A tight cluster that agrees internally and diverges externally on a commercially interesting
  item is the T4 signature.
- Response: collapse the cluster's combined weight to that of a *single* contributor. Not a ban
  — a ban tells them they were caught and they simply re-form. Collapsing weight makes the attack
  economically pointless while leaving them uninformed.
- Legitimate co-occurrence exists (a family, a market association, a neighbourhood group). The
  discriminator is *divergence from external consensus*, not co-occurrence alone. Requires human
  review before any cluster of size > 10 is collapsed.

### L9 — Curation gate: how corrections become learning

**No user correction ever directly updates a model, an index, or a published price.** The path:

```
CORRECTION
   │
   ├─ passes L2/L3/L4 (integrity, provenance, geo)?          no → store, weight 0, stop
   ├─ reaches CONFIRMED via L7 consensus?                    no → hold, revisit
   ├─ contributor influence cap applied (≤ 15% of any class) 
   ├─ near-duplicate detection (perceptual hash + embedding) — the same photo resubmitted,
   │  or 40 photos of the same physical item, count once
   │
   ├─ LABEL corrections → prototype centroid update
   │     robust geometric median of the class's confirmed embeddings, not the arithmetic
   │     mean — one poisoned embedding cannot drag a geometric median
   │     └─ candidate index built
   │          └─ GOLDEN SET REGRESSION: frozen, human-verified eval set. Any drop in
   │             top-1 on *any* class > 0.5 pts blocks the update entirely
   │               └─ CANARY: 2% of devices, 48 h, monitored on agreement rate and
   │                  abstention rate
   │                    └─ FULL ROLLOUT
   │
   └─ PRICE corrections → cell re-aggregation (L6) → maturity gate → publish
```

**Additional poisoning defences:**

- **Periodic rebuild from scratch.** Every 90 days the entire prototype index is rebuilt from the
  full confirmed corpus rather than incrementally updated. Incremental updates accumulate drift
  that a slow attacker can exploit; a rebuild sheds it.
- **Influence tracing.** Every prototype centroid retains the contributor set that produced it.
  When an account is later found abusive, its contributions are subtracted and affected
  centroids rebuilt. Poisoning must be *reversible*, and that requires provenance retention from
  day one — retrofitting it is impossible.
- **Class-level anomaly monitoring.** A class whose centroid moves more than a threshold in one
  update cycle triggers human review regardless of how the update passed the gates.
- **Adapter training (Phase 5) uses gradient norm clipping** — which is a poisoning defence, not
  just a stability trick: it bounds any single example's influence on the update.
- **Red-team suite in CI.** Ticket H-11 maintains a standing set of simulated attacks (T1–T7)
  replayed against the consensus and curation pipelines on every change. Zero poisoned labels
  reaching production is a release gate, not a metric.

## Federated learning (Phase 5+, optional)

Attractive for privacy but carries real risk: FL makes poisoning *easier*, because the server
sees updates rather than data and cannot inspect the examples. If pursued (ticket J-08), require:
secure aggregation, per-client update norm clipping, central differential privacy with a tracked
budget, and Krum/trimmed-mean robust aggregation over client updates. Even then, keep the
golden-set regression gate — FL does not replace the curation gate, it feeds into it.

Recommendation: **defer FL past v1.0.** The centralized curation pipeline with retained
provenance gives better integrity, and integrity is what this product sells.

## Transparency and appeals

Integrity systems make mistakes and they fall hardest on people with unusual devices, rooted
phones, or poor connectivity — often the same users the product is most meant to serve.

- Users can see their own submission history and its published status.
- A user whose submissions are being down-weighted sees no accusatory messaging, but a user who
  is *rate-limited or blocked* gets a clear notice and a human appeal path.
- Never surface reputation, weights, or tiers numerically — to anyone.
- Publish an aggregate integrity transparency report: submissions received, share quarantined,
  appeals upheld. Accountability for the system's own error rate.
