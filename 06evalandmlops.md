# 06 — Evaluation & MLOps

> The headline requirement is "getting the label and price prediction right is extremely
> important". That is an evaluation problem before it is a modelling problem. This document
> defines what "right" means, how it is measured, and what blocks a release.

## Evaluation datasets

Four distinct sets, and they must not be confused with each other.

### 1. Golden set — the release gate

- **Size:** 3,000 images minimum at v1.0, growing to 10,000.
- **Composition:** every taxonomy item with ≥ 5 confirmed observations gets ≥ 8 golden images.
  Stratified across: device tier (flagship / mid / budget / LEGACY), lighting (daylight, shade,
  indoor incandescent, fluorescent, low light), background (market stall, table, hand, bag),
  and presentation (single item, pile, bunch, packaged).
- **Labels:** human-verified, double-annotated, disagreements adjudicated by a third annotator.
  **Never** auto-labelled from model output or from unverified user corrections — a golden set
  contaminated by model predictions makes accuracy unfalsifiable.
- **Frozen.** Additions require sign-off. Removals require justification. Version-controlled with
  the images stored by content hash.
- **Held out absolutely.** Never used for training, calibration, or hyperparameter selection.

### 2. Calibration set

Separate held-out set used only to fit temperature and Dirichlet calibration, and to fit the
fusion weights `w`. Refit per model version. Must be stratified by device tier, because
calibration parameters that are correct on average are wrong for budget devices specifically.

### 3. Device fleet set — the cross-device gate

The physical reference kit from doc 02: the same 40 physical items, photographed on all fleet
devices under three controlled lighting conditions, plus a colour target and a scale reference.

**Metric:** pairwise cosine distance between post-normalization embeddings of the same physical
item across devices.

| Stage | Expected |
|---|---|
| Raw frames | 0.20–0.35 |
| After canonical normalization | **< 0.08 — this is the Phase 1 acceptance criterion** |

Also tracked: top-1 accuracy per device tier, and the **spread** between the best and worst tier
(≤ 3 points).

### 4. Price backtest set

Historical observations with temporal splits. Train on weeks ≤ *t*, evaluate week *t+1*, rolling
over 26 weeks. **Random splits are forbidden** — they leak future prices into the past and roughly
halve the apparent error.

Includes a deliberately adversarial slice: cells that experienced genuine regime shifts
(seasonal gluts, fuel price shocks, currency moves). The change-point logic must track these,
not reject them.

## Metrics

### Recognition

| Metric | Definition | Gate |
|---|---|---|
| Top-1 (non-abstained) | Correct / (total − abstained) | ≥ 92% |
| Top-5 | Correct in top 5 | ≥ 98% |
| Abstention rate | Abstained / total | ≤ 15% |
| ECE (15 bins) | Expected calibration error | ≤ 0.05 |
| ECE per device tier | Same, stratified | ≤ 0.07 each |
| Cross-device spread | max tier top-1 − min tier top-1 | ≤ 3 pts |
| Risk-coverage AUC | Area under the selective-risk curve | Tracked, must not regress |
| Per-class worst | Min top-1 over classes with ≥ 20 golden images | ≥ 70% |
| Confusion pairs | Any pair confused > 15% of the time | Flagged for review |

The **per-class worst** metric matters as much as the mean. A model at 93% mean that is at 20%
on plantains is broken for anyone shopping for plantains, and mean accuracy hides it completely.

### Price

| Metric | Gate |
|---|---|
| Band coverage (P10–P90) | ≥ 88% |
| MdAPE of P50, mature cells | ≤ 12% |
| MdAPE of P50, cold-start cells (n ∈ 1–5) | ≤ 25% |
| Pinball loss (0.1/0.5/0.9) | Must not regress |
| Sharpness, mean (P90−P10)/P50 | Minimize *subject to* coverage |
| Regime-shift tracking lag | ≤ 2 weeks to adopt a genuine shift |

Coverage and sharpness are adversarial to each other. Track them together, always: a band from
zero to infinity has perfect coverage and no value; a band of width zero has perfect sharpness
and no coverage.

### Latency and resource

| Metric | Gate |
|---|---|
| End-to-end capture → label, p95, reference mid-tier | ≤ 450 ms |
| Cold start to camera ready, p95 | ≤ 1.8 s |
| Peak memory during inference | ≤ 380 MB |
| Battery per 100 captures | ≤ 4% on reference device |
| Base APK size | ≤ 25 MB |
| Thermal: sustained 5-min capture without CRITICAL | Required |

### Integrity

| Metric | Gate |
|---|---|
| Poisoned labels reaching production, red-team suite | 0 |
| Simulated attacks detected before affecting a published band | ≥ 95% |
| False-positive integrity blocks on legitimate traffic | ≤ 0.5% |
| Genuine regime shifts misclassified as attacks | ≤ 5% |

## Shadow mode

Before any model version is promoted, it runs in shadow: the new model executes alongside the
current one on real captures, its output logged but never shown.

Compared over ≥ 50,000 real captures:
- Agreement rate with the incumbent.
- On the subset where the user corrected: which model matched the user's answer. **This is the
  most valuable signal in the entire system** — it is the only measurement against real
  in-the-wild ground truth rather than curated data.
- Abstention rate delta.
- Latency delta per device tier.

A model that wins offline but loses on user-corrected captures does not ship. Offline eval sets
drift from reality; user corrections do not.

## Release gates

A model or index version is promotable only when **all** hold:

1. Golden set: all recognition gates met.
2. No class with ≥ 20 golden images regresses more than 0.5 pts.
3. Device fleet: cross-device spread ≤ 3 pts, embedding distance < 0.08.
4. Calibration: ECE within gate globally *and per device tier*.
5. Latency gates met on the reference mid-tier device.
6. Shadow mode: ≥ 50k captures, no regression on the user-corrected subset.
7. Red-team suite: zero poisoned labels promoted.
8. Delegate parity: golden tensor comparison passes on the top 20 device classes by install base.

## Rollout

```
CANARY 2%   → 48 h → monitor: agreement rate, abstention, crash-free, latency, correction rate
   ↓ pass
RAMP 10%    → 48 h
   ↓ pass
RAMP 50%    → 72 h
   ↓ pass
FULL 100%
```

**Automatic rollback triggers** (any one, evaluated hourly):
- Agreement rate drops > 2 pts vs baseline.
- Abstention rate rises > 5 pts.
- Crash-free sessions < 99.5%.
- p95 latency rises > 25%.
- Correction rate rises > 3 pts.

Models and indices are delivered via the signed registry, so rollback is a manifest change and
takes effect on the next client poll — not an app store release. This is why model delivery is
decoupled from APK delivery, and it is worth the extra machinery: an app-store rollback takes
days, and days of a bad price model is real harm.

## Continuous monitoring in production

| Signal | Why it matters |
|---|---|
| Correction rate by item | A rising correction rate on one item = a taxonomy or prototype problem |
| Correction rate by device class | Rising = a device-specific normalization regression, often an OS update |
| Abstention rate by locality | High = the local catalog is missing items |
| Band coverage vs later observations | The live counterpart to backtest coverage |
| Consensus disagreement rate | Rising = either an attack or a genuine market disruption. Investigate, don't assume |
| Cell staleness distribution | Where is the data going cold |
| p95 latency by device class | Thermal and delegate regressions |

Alert on *rate of change*, not absolute levels. A locality that has always had 30% abstention is
a known cold-start; a locality that went from 8% to 25% in a week is a regression.

## Experiment tracking and reproducibility

- Every model artifact carries: training data snapshot hash, code commit, hyperparameters,
  eval results, and the calibration parameters fit for it.
- Every prediction logged from the app carries `model_versions` (backbone, detector, adapter,
  prototype index, calibration, taxonomy). Without this, a production regression cannot be
  attributed to a component.
- Prototype indices carry the contributor set that built them (doc 04), making poisoning
  reversible.
- Nightly: rebuild the eval report and publish to the team dashboard. Regressions found on day
  one are cheap; found on day thirty they are archaeology.

## Annotation operations

The golden set needs sustained human annotation, and this is a real cost line, not a footnote.

- Two independent annotators per image, third-annotator adjudication on disagreement.
- Annotators are given the taxonomy with *photos*, not just names — "Roma tomato" vs "plum
  tomato" is not resolvable from text.
- Inter-annotator agreement is itself tracked. Classes with agreement < 85% are a **taxonomy
  problem**, not an annotator problem: the categories are not distinguishable and should be
  merged. This feeds back to `taxonomy_item.merged_into_id`.
- Budget roughly 0.5 FTE of annotation from Phase 2 onward, continuously.
