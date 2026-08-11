# 00 — Product Brief

## The problem

In informal and semi-formal markets, food prices are not posted. The buyer has no reference
point, and the seller has near-perfect information about what similar buyers paid this week.
The result is systematic overcharging of the people least able to absorb it: newcomers,
tourists, the elderly, non-native speakers, and anyone shopping outside their usual market.

Existing price data is either (a) national-average government statistics that are useless at
the stall level, or (b) supermarket catalogs that do not cover the market where the price
asymmetry actually bites.

## The product

Point the phone at a food item. In under three seconds the app tells you **what it is** and
**what it should cost here, today** — as a range with a confidence level. You then confirm or
correct both answers, and your correction becomes part of the local price record for the next
person.

The app is simultaneously a consumer tool and a data collection instrument. Both roles have to
work, and the second one is what makes the first one durable.

## Primary users

**Shopper (the core user).** Wants a fast answer at the point of transaction, often one-handed,
often in poor light, often on a mid-range or older device. Will not tolerate a 10-second wait
or a signup wall. May be offline.

**Contributor.** A shopper who cares enough to correct the app. Motivated by usefulness and
local pride, not points. Needs corrections to feel like they landed and mattered.

**Analyst / partner (secondary, Phase 6).** NGOs, consumer-protection bodies, journalists, and
researchers who want the aggregate locality price series. Consumes the API, not the app.

## Jobs to be done

1. *"Am I being overcharged for this right now?"* — the whole product in one sentence.
2. *"What does this cost around here normally?"* — pre-shopping reference.
3. *"What even is this?"* — unfamiliar produce, unfamiliar market, unfamiliar language.
4. *"I know the real price — record it."* — contribution as a first-class action, not a
   correction-only afterthought.
5. *"Show me how prices moved."* — locality price history (Phase 6).

## Scope

### In scope for v1.0

- Single-item capture and recognition from the live camera.
- Open-vocabulary food labeling across a ~1,200-item seed taxonomy, extensible without a model
  retrain.
- Packaged-goods identification via barcode and on-pack OCR.
- Price band estimation per unit (kg / L / piece / bunch) for a locality.
- User validation of both label and price, with correction.
- Locality-scoped price database with peer cross-validation and public-source seeding.
- Offline capture with deferred sync.
- Device profiling and per-device camera calibration.
- Anti-tamper: attestation, provenance, geo-integrity, robust aggregation, reputation.

### Explicitly out of scope for v1.0

- iOS. (Architecture keeps the ML layer portable; ship Android first.)
- Multi-item / whole-basket scene parsing. Single dominant item only.
- Nutrition, calories, allergens, dietary analysis. Different product.
- Payments, ordering, marketplace, seller accounts.
- Cooked / restaurant dishes. Raw and packaged retail goods only. (Prepared food pricing depends
  on preparation, venue and portioning in ways the vision stack cannot resolve.)
- Cross-border currency arbitrage advice.
- Any social feed, comments, or public user profiles. The abuse surface is not worth it.

### Deliberate anti-features

- **No point-estimate prices.** Always a band. A single number implies precision we do not have.
- **No gamified leaderboard.** Leaderboards are a Sybil-farming incentive, directly at odds with
  data integrity. Contribution feedback is private and qualitative.
- **No gallery uploads in the trusted tier.** Photos not produced by the app's own camera
  pipeline can be submitted but are marked untrusted and carry zero consensus weight.
- **No precise location storage.** Coarse locality (geohash-6, ~1.2 km) is the finest granularity
  ever persisted server-side.

## Success metrics

### Product

| Metric | v1.0 target | Notes |
|---|---|---|
| Time from app-open to prediction shown | p50 ≤ 2.0 s, p95 ≤ 4.0 s | Mid-tier device, warm start |
| Prediction accepted without correction | ≥ 70% | Both label and price |
| Validation completion rate | ≥ 55% | Users who answer the validate prompt |
| Localities with a mature price record | 25 by launch + 90 days | "Mature" defined below |
| D30 retention | ≥ 22% | For users with ≥ 3 sessions in week 1 |

**Mature locality:** ≥ 40 distinct food items, each with ≥ 5 independent confirmed observations
in the trailing 30 days, from ≥ 8 distinct contributors.

### Model (the bars from the README, restated as owned metrics)

| Metric | Target | Owner |
|---|---|---|
| Label top-1 on non-abstained | ≥ 92% | Recognition |
| Abstention rate | ≤ 15% | Recognition |
| Label top-5 | ≥ 98% | Recognition |
| Expected calibration error | ≤ 0.05 | Recognition |
| Price band coverage (P10–P90) | ≥ 88% | Pricing |
| Price MdAPE, mature localities | ≤ 12% | Pricing |
| Cross-device top-1 spread | ≤ 3 pts | Device calibration |
| On-device inference latency | ≤ 450 ms p95, mid-tier | Runtime |

### Integrity

| Metric | Target |
|---|---|
| Poisoned labels reaching production in red-team suite | 0 |
| Coordinated price manipulation detected before affecting published band | ≥ 95% of injected attacks |
| False-positive rate of integrity blocks on legitimate users | ≤ 0.5% |
| Median time from submission to consensus confirmation | ≤ 72 h in mature localities |

## Key risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| **Cold start.** No price data → no value → no contributors → no price data. | Fatal | Seed from public sources (Ticket F-01/F-02) before any locality launches. Launch locality-by-locality, never globally. Gate a locality's price display on the maturity threshold; before that, show "learning this area" and collect only. |
| **Device variance breaks recognition.** Model trained on clean data collapses on a cheap wide-angle sensor in fluorescent light. | High | The entire Phase 1 canonical-camera-space work. Cross-device spread is a tracked release gate, not a nice-to-have. |
| **Data poisoning.** Sellers with an interest in inflating recorded prices, or pranksters. | High | Layered: attestation → provenance → geo-integrity → reputation-weighted robust aggregation → consensus confirmation → curation gate before any model update. Detailed in [doc 04](04-trust-integrity-and-learning.md). |
| **Genuine price shifts look like attacks.** Inflation or seasonality rejected as outliers. | Medium | Change-point detection and a separate "regime shift" path that widens the prior instead of rejecting the observations. Explicitly tested. |
| **Confidently wrong price causes real financial harm.** | High | Abstention-first policy, band not point, prominent uncertainty display, and a hard rule that unconfirmed single observations never drive a displayed band. |
| **Portion/weight estimation is unreliable, so per-kg prices are wrong.** | Medium | Require an explicit unit choice from the user when the visual scale estimate has low confidence. Never silently guess weight. |
| **Legal exposure from price data about named sellers.** | Medium | Never record seller identity. Locality-level aggregation only, geohash-6 minimum. No stall, vendor, or business names anywhere in the schema. |
| **Model licence contamination.** A chosen backbone forbids commercial use. | Medium | Licence review is an explicit acceptance criterion on every model-selection ticket (B-06, E-02). |

## Localization and accessibility

- Launch languages: English, French, Spanish, Hindi, Swahili. Taxonomy labels carry per-locale
  display names *and* local vernacular aliases — the name on the stall is rarely the botanical one.
- Currency and unit systems are locality properties, never device-locale assumptions.
- Full TalkBack support on the capture and validation flows; prediction and confidence must be
  announced, not just drawn.
- The capture flow must be usable one-handed with a 44 dp minimum touch target.
- Design for 3-year-old mid-tier hardware as the reference device, not a flagship.

## Privacy posture

- Photos leave the device only when the user submits an observation, and only then.
- Location is coarsened to geohash-6 on-device before it is ever transmitted.
- Face and person detection runs before upload; frames containing people are blurred on-device
  or rejected.
- No account required to *use* predictions. An account (phone-verified) is required only to
  *contribute*, because Sybil resistance requires an identity cost.
- Data export and deletion on request; contributed observations are retained in aggregate but
  unlinked from the account.
