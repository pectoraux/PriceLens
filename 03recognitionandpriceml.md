# 03 — Recognition & Price ML

## Part 1 — Food recognition

### Why not just train a classifier

A fixed-class classifier is the obvious approach and it is wrong for this product:

- The taxonomy must grow continuously. Every new locality brings produce the model has never
  seen — and a retrain-per-item loop cannot keep up.
- Long tail dominance. The top 200 items are easy; the value is in item 800, which has 40
  photos worldwide.
- Corrections from users are the primary learning signal, and they arrive one item at a time.

So the architecture is **open-vocabulary detection + embedding retrieval**. The backbone is
frozen and rarely updated; *the index is what learns*. Adding an item means adding text and
prototype embeddings — no retraining, no redeployment, and it can happen server-side within
hours of the first confirmed correction.

### Stage 2 — Detection

**Job:** find the dominant food region, and find people so they can be redacted.

**Approach:** a distilled open-vocabulary detector exported to LiteRT. Candidates to evaluate in
ticket E-02, in preference order:

1. **YOLO-World / YOLOE-small, distilled and INT8-quantized.** Best speed/quality on mobile;
   accepts text prompts so the "is this food" gate is free. Check licence (Ultralytics AGPL is
   a real blocker for a commercial release — this is exactly why licence review is an
   acceptance criterion).
2. **OWLv2-ViT-B/16 distilled to a MobileViT student.** Better open-vocab recall, Apache-2.0
   lineage, but heavier; may not fit the 90 ms budget on budget hardware.
3. **Class-agnostic proposals (a small EfficientDet-Lite trained on a food/not-food superclass)
   + saliency.** The safe fallback: fastest, lowest quality, but its output feeds the same
   embedding stage so degradation is graceful.

Ship the best that clears the latency budget on the *reference mid-tier device*, and keep option
3 as the low-end variant selected by the compute profile.

**Dominant region selection:**
```
score = area_norm^0.5 × centrality × objectness × focus_sharpness
```
where `centrality` is a Gaussian on distance from the frame centre (users point at what they
mean) and `focus_sharpness` is the local Laplacian variance normalized by the noise profile —
the in-focus object is the subject.

**Person redaction** runs unconditionally, before anything is written to disk. A frame with a
detected face gets the face region blurred in the stored evidence copy. This is a privacy
requirement, not an option, and it is tested in H-09.

### Stage 3a — Embedding

**Model:** a mobile CLIP-family dual encoder. Evaluate MobileCLIP-S2, SigLIP-Tiny/Base-patch16,
and EfficientViT-CLIP. Requirements:

| Requirement | Value |
|---|---|
| Output | 512-d, L2-normalized |
| Aligned text tower | Required — zero-shot depends on it |
| Latency | ≤ 110 ms p95 on the reference device |
| Quantization | INT8 with ≤ 1e-3 mean cosine drift vs fp32 |
| Licence | Permissive, commercial use allowed |

The image tower runs on-device per capture. The **text tower runs server-side only** — text
embeddings for the taxonomy are precomputed, signed, and shipped in the catalog bundle. There is
no reason to pay 20 MB of text encoder on the device.

**Domain adaptation (Phase 5, ticket J-03):** rather than fine-tuning the backbone (which
invalidates every stored embedding and forces a full index rebuild), train a small **adapter** —
a 512×512 residual linear projection trained on confirmed food pairs with a contrastive loss.
Cheap to train, cheap to ship, and re-projectable: an index rebuild is a matrix multiply. This
is the mechanism through which the model actually improves from user corrections.

### Stage 4 — Retrieval

Two parallel retrieval paths over the same 512-d space:

**(i) Zero-shot text similarity.** Cosine similarity between the image embedding and the
precomputed text embeddings of every taxonomy entry. Text prompts are ensembled per item:
```
"a photo of a {name}"
"a photo of {name}, a type of {parent_category}"
"{name} for sale at a market"
"{local_vernacular_name}"          ← per-locale, this matters a lot
```
Ensemble by averaging the normalized text embeddings. Covers items with zero photos.

**(ii) Few-shot prototype kNN.** An on-device HNSW index over **prototype centroids** built from
confirmed user observations. Each taxonomy item accumulates up to 32 centroids (k-means over
its confirmed embeddings) to capture within-class variation — ripeness, variety, packaging,
lighting regime. Scored as a distance-weighted kNN vote.

This second path is what makes corrections pay off. An item with 30 confirmed local photos beats
zero-shot by a wide margin, and it improves *without touching the model*.

**Index management:**
- Ships as a signed bundle per locality cluster (~1,200 items × up to 32 centroids × 512 d,
  int8-quantized ≈ 20 MB, well within on-demand delivery).
- Delta-updated weekly; full rebuild on adapter change.
- On-device HNSW with `M=16, efConstruction=200, efSearch=64` — 15 ms is a generous budget for
  this size.

### Stage 3b — OCR and barcode

For packaged goods this path is not a hint, it is close to ground truth:

- **Barcode → GTIN → SKU.** Resolves to an exact product with an exact net weight. When a GTIN
  resolves confidently, it *overrides* the vision path entirely.
- **On-pack text.** Brand, product name, and net weight via ML Kit text recognition, matched
  against the catalog with fuzzy string matching. Net weight extraction (`500 g`, `1 kg`,
  `2 × 250 ml`) removes the need for portion estimation completely.
- **Price tag OCR.** Where a price is posted, read it — and treat it as a *high-trust price
  observation* separate from the user's typed answer. A photographed price tag is much harder
  to fake than a typed number, and doc 04 gives it a higher provenance tier accordingly.

Public product databases for GTIN resolution: Open Food Facts (ODbL — attribution and share-alike
obligations apply to derived DB distribution; review in F-02), GS1 verified sources where
regionally available.

### Stage 5 — Fusion

The evidence sources, each producing a score vector over the taxonomy:

| Source | Symbol | Typical strength |
|---|---|---|
| Zero-shot text similarity | `s_zs` | Broad coverage, weak precision |
| Prototype kNN | `s_knn` | Strong where data exists |
| OCR/barcode match | `s_ocr` | Near-certain when it fires |
| Locality catalogue prior | `s_loc` | What is actually sold here |
| Seasonality prior | `s_seas` | Meaningful for produce |
| User history prior | `s_hist` | Weak, but free |

**Fusion:**
```
logits = w_zs·s_zs + w_knn·s_knn + w_ocr·s_ocr + log(prior_loc) + log(prior_seas) + log(prior_hist)
p      = softmax(logits / T(quality))
```

The weights `w` are learned (multinomial logistic regression on a held-out validation set), not
hand-tuned. Crucially, **`w_knn` is conditioned on the number of prototypes available for the
top candidates** — kNN evidence from three prototypes should not outvote zero-shot.

**Quality-conditioned temperature.** `T` is a learned function of the Stage 0/1 quality metrics
(blur, exposure, ISO, motion, device-profile degradations, `NARROW_FOV`). Bad conditions raise
`T`, flattening the posterior, raising abstention. This is the mechanism that turns "my camera
is bad" into "I'm not sure" rather than into a wrong answer.

**Calibration.** Temperature scaling first, then Dirichlet calibration on a held-out set,
refit per major model version and validated per device tier. Target ECE ≤ 0.05, measured
separately for each device tier — a globally calibrated model can still be badly miscalibrated
on budget hardware, and that is precisely the population we must not mislead.

**Abstention policy** (`:domain`, single source of truth, unit-tested):
```
ABSTAIN if  p_top1 < τ_label                       // τ_label ≈ 0.62, tuned on the precision target
         or p_top1 − p_top2 < margin_min           // ambiguous between two items
         or device_confidence == LOW and p_top1 < 0.80
         or quality_gate_failed

On abstain: show top-3 as a picker, ask the user, and treat the answer as a
label observation of the same weight as a correction.
```
Abstention is not a failure state in the UI. "Is this one of these?" with three tappable
options is a *better* experience than a confident wrong guess, and it produces cleaner training
data than a confirmation click.

### Stage 6 — Portion and unit

Per-kg prices require mass. Getting mass wrong corrupts the price database, so the bar for
asserting a quantity is high.

**Distance estimation**, in preference order:
1. ARCore Depth API — metric depth at the object centroid. Best.
2. Camera2 `LENS_FOCUS_DISTANCE` at AF lock, where the device reports it in dioptres and
   `LENS_INFO_FOCUS_DISTANCE_CALIBRATION` is `APPROXIMATE` or better.
3. Reference object in frame — a detected coin, bank card, or hand. Card detection is reliable
   and its dimensions are ISO-standard (85.60 × 53.98 mm).
4. None → **do not estimate mass**. Ask the user for the unit and quantity.

**Mass estimation:** the segmented mask's area and the estimated distance give physical
dimensions; a per-class shape factor converts projected area to volume (spheroid for citrus,
cylinder for cucumbers, and a "pile" model with an occlusion correction for heaps of loose
produce, which is the common market case and the hardest). Then `mass = volume × density`, from
a per-class density lookup table (ticket G-04) built from published food-density references.

**Uncertainty propagation is mandatory.** Distance error is the dominant term and it enters
squared through area:
```
σ_mass/mass ≈ sqrt( (2·σ_d/d)² + σ_shape² + σ_density² )
```
If the resulting relative error exceeds 25%, the app must not assert a mass. It asks. The
product rule: **never silently guess weight** — a bad mass estimate produces a plausible-looking
but wrong per-kg price, which is the exact failure mode the product exists to prevent.

For heaps and bunches, prefer the natural retail unit ("bunch", "pile", "bag") and record the
price against *that* unit, with the mass conversion left as a separate, lower-confidence
inference. Markets price by the pile; the database should too.

---

## Part 2 — Price estimation

### The target

Not a number. For a `(locality, item, unit, week)` cell:

```json
{
  "p10": 180.0, "p50": 220.0, "p90": 280.0,
  "currency": "KES", "unit": "kg",
  "n_observations": 23, "n_contributors": 11,
  "freshness_days": 2,
  "confidence": "HIGH",
  "sources": {"user_confirmed": 19, "public": 3, "receipt_ocr": 1}
}
```

The UI verdict is derived from where the asked price falls in the band:

| Asked price | Verdict |
|---|---|
| < P10 | "Below the usual range — good deal" |
| P10–P90 | "Within the usual range here" |
| P90–1.5×P90 | "Above the usual range" |
| > 1.5×P90 | "Well above the usual range" |

With `n_observations < 5` or `freshness_days > 21`, no verdict is shown at all — only "not
enough local data yet". Displaying a verdict from thin data is how this product would do harm.

### The model

A **hierarchical Bayesian quantile model**, fit server-side, refit nightly per region.

```
log(price_ipt) = μ + α_item + β_locality + γ_{item×locality}
                     + δ_t (seasonal/trend) + ε

α_item     ~ N(0, σ_item²)
β_locality ~ N(0, σ_loc²)                 partial pooling
γ          ~ N(0, σ_int²)                 shrinks hard when n is small
δ_t        = GP or spline over week, shared within region
ε          ~ Student-t(ν=4)               heavy tails: robust to residual outliers
```

Why this shape:

- **Partial pooling solves cold start.** A locality with three observations of onions borrows
  strength from the regional onion price and from the locality's price level on other items.
  This is the single most important modelling decision — it is what lets a new locality show
  useful numbers before it is mature.
- **Student-t likelihood** absorbs outliers that survived the trust filters instead of letting
  them drag the posterior.
- **Quantiles from the posterior predictive**, so P10/P90 widen honestly when data is thin.
  A cell with 4 observations produces a wide band; that width *is* the honest answer.

**Implementation:** NumPyro or Stan for nightly regional fits; the fitted parameters are
exported as a compact table for fast serving. Online serving does not run MCMC — it looks up
the cell posterior and applies a freshness decay.

**On-device fallback** for offline use: the last-synced cell bands plus a locality-level
inflation factor, with `freshness_days` visibly stale in the UI. Never hide staleness.

### Unit normalization

The pit every price dataset falls into. `:domain` owns a single canonical unit converter:

- Canonical units: `kg`, `L`, `piece`, `bunch`, `bag`, `crate`.
- Locality-specific retail units (`gorogoro`, `debe`, `mudu`, `tin`, `heap`) map to canonical
  units through a **locality-specific, versioned conversion table** — because a "tin" is not the
  same volume in two markets, and pretending otherwise silently corrupts the data.
- Where no reliable conversion exists, the vernacular unit *is* the canonical unit for that
  locality. Do not force a conversion. Prices per "bunch" are still useful.
- Currency: store minor units as integers, always with an ISO-4217 code and the FX rate snapshot
  used at display time. Never store floats for money.

### Public source ingestion

Seeds cold-start localities and provides an independent cross-check on user data. Connectors
(ticket F-01/F-02), each normalizing into the same cell schema at trust tier `PUBLIC`:

| Source | Coverage | Notes |
|---|---|---|
| WFP VAM / DataViz food prices | 90+ countries, monthly, market-level | The best single global source for exactly this problem |
| FAO FPMA | National/subnational, monthly | Good for regional trend `δ_t` |
| National agriculture market APIs (e.g. India Agmarknet, Kenya NAFIS, Brazil CONAB) | High-resolution where they exist | Per-country connector work, high value |
| Open Food Facts | Packaged goods, GTIN → product | Not prices, but SKU resolution |
| Retailer catalogues where ToS permits | Urban formal retail | **Legal review required per retailer.** Scraping in violation of ToS is out of scope; only permitted APIs |
| Government CPI series | National, monthly | Calibrates `δ_t` trend, not levels |

Public data is a *prior*, never a veto. Government market prices are systematically different
from street prices — that gap is the product's whole thesis. The model must let user data
override public data as `n` grows, which the hierarchical structure does naturally.

### Price prediction evaluation

| Metric | Definition | Target |
|---|---|---|
| Band coverage | Fraction of held-out true prices inside [P10, P90] | ≥ 88% |
| MdAPE | Median absolute % error of P50 | ≤ 12% mature |
| Pinball loss | Quantile loss at 0.1/0.5/0.9 | Tracked, minimized |
| Sharpness | Mean (P90−P10)/P50 | Minimized *subject to* coverage — a band from 0 to ∞ has perfect coverage and zero value |
| Cold-start MdAPE | Same, on cells with n ∈ [1,5] | ≤ 25% |

Backtesting protocol: temporal split, never random. Train on weeks ≤ *t*, evaluate week *t+1*.
Random splits leak future prices into the past and will flatter the model by roughly a factor
of two.
