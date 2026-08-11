# 10 — Price Resolution, Extrapolation & Input Validation

> How the system answers "what does this cost *here*, today" when the honest answer ranges from
> "forty people told us this week" to "nobody has ever reported this item within 300 km".

Doc 03 specifies a hierarchical Bayesian model for food prices. That model is correct and stays.
What it does not specify is the **query-time procedure**: which evidence is consulted, in what
order, how far the system is willing to reach for a substitute, and — critically — how it tells the
user which of those things it did. That gap is what this document closes, and it is where the
"smart way of getting the right price" actually lives.

## Principle: the basis is part of the answer

A band with no stated basis invites the user to trust a regional extrapolation as much as a local
consensus. Every price response therefore carries an explicit **basis**, and the UI renders it
differently at each level. This is the same discipline as abstention: the system is allowed to be
uncertain, and is not allowed to hide it.

```
PriceAnswer {
  p10, p50, p90, currency, unit
  basis: LOCAL_OBSERVED | NEIGHBOR_POOLED | REGIONAL_MODEL
       | SKU_NATIONAL_ADJUSTED | SUBSTITUTE_INFERRED | PUBLIC_REFERENCE | INSUFFICIENT
  n_effective        // Kish effective sample size, not raw count
  n_contributors
  freshness_days
  spatial_reach_km   // how far we had to go. 0 = this locality
  confidence: HIGH | MEDIUM | LOW | INSUFFICIENT
  verdict_allowed: bool   // may the app show "you're being overcharged"?
  source_mix
}
```

`n_effective = (Σw)² / Σw²` over the surviving weights. With reputation-weighted aggregation, raw
counts lie: twelve observations where one contributor holds 60% of the weight is not twelve
observations, and the effective size is what should gate the maturity threshold and the verdict.

`verdict_allowed` is a separate flag rather than a threshold the client re-derives, because the
client must never be the place where "is it safe to tell someone they are being cheated" is decided.
The server owns it; the client obeys it.

## The resolution ladder

Evaluated in order. Each rung is attempted only if the one above it fails its own sufficiency test.
Every rung widens the band relative to the one above — extrapolation is paid for in uncertainty, and
that is the mechanism that keeps a far-fetched answer from looking like a confident one.

```
QUERY (item_ref, unit, condition, channel?, location, t)
        │
        ▼
┌── L0 · LOCAL OBSERVED ─────────────────────────────────────────────┐
│ Published cell at the profile's cell_scope_type, period covering t │
│ Sufficient if: n_eff ≥ profile.consensus_min_obs                   │
│                AND freshness ≤ period length × 1.5                 │
│ basis = LOCAL_OBSERVED · reach 0 km                                │
└────────┬───────────────────────────────────────────────────────────┘
         │ insufficient
┌── L1 · NEIGHBOUR POOLED ───────────────────────────────────────────┐
│ Same item, neighbouring localities, weighted by a similarity       │
│ kernel (below). Sufficient if pooled n_eff ≥ min_obs and the       │
│ kernel mass from outside the home locality is ≤ 0.7                │
│ basis = NEIGHBOR_POOLED · reach = weighted mean distance           │
└────────┬───────────────────────────────────────────────────────────┘
         │
┌── L2 · REGIONAL MODEL ─────────────────────────────────────────────┐
│ The doc-03 hierarchical posterior: regional item level × locality  │
│ price-level offset α_locality × seasonal/trend δ_t                 │
│ Always available once the region has any data on the item          │
│ basis = REGIONAL_MODEL                                             │
└────────┬───────────────────────────────────────────────────────────┘
         │ item unseen in region
┌── L3 · SKU NATIONAL ANCHOR  (allows_national_ref only) ────────────┐
│ National reference price for this exact SKU/model, adjusted by the │
│ locality's category price level and channel offset, and decayed by │
│ price_decay_per_month × months since the reference                 │
│ basis = SKU_NATIONAL_ADJUSTED                                      │
└────────┬───────────────────────────────────────────────────────────┘
         │ no national reference
┌── L4 · SUBSTITUTE INFERENCE ───────────────────────────────────────┐
│ Comparable items (taxonomy_item.substitutes, or same class + near  │
│ attribute vector) priced locally, mapped through the national      │
│ price ratio between the substitute and the target                  │
│ basis = SUBSTITUTE_INFERRED · verdict_allowed = false, always      │
└────────┬───────────────────────────────────────────────────────────┘
         │
┌── L5 · PUBLIC REFERENCE ───────────────────────────────────────────┐
│ WFP VAM / FAO / national market APIs / CPI-adjusted retailer feeds  │
│ basis = PUBLIC_REFERENCE · verdict_allowed = false                 │
└────────┬───────────────────────────────────────────────────────────┘
         ▼
     INSUFFICIENT — show the item, show no price, invite a contribution
```

L4 and L5 never authorize a verdict. They are good enough to orient someone ("roughly 3,000–5,000")
and not good enough to tell them they are being overcharged. Doc 00's harm analysis applies with
more force here than in food: telling someone a fair price for a fridge is wrong by 40% can cost
them a month's income.

## The similarity kernel — how "nearby" is decided

Plain distance is the obvious weighting and it is wrong. Two markets 3 km apart on opposite sides of
an income boundary are far less comparable than two similar markets 25 km apart. The kernel
therefore combines geography with **learned locality similarity**:

```
w(i → j) = exp( −(d_ij / ℓ_a)² )              geographic decay, ℓ_a from the category profile
         × exp( −|α_i − α_j| / λ )            price-level similarity
         × m(market_type_i, market_type_j)    channel/market-type compatibility
         × f(Δt)                              freshness decay
```

- `α_locality` is not a new quantity — it is the locality random effect the doc-03 hierarchical
  model already fits, reused as a similarity coordinate. A locality that prices 20% above regional
  on everything measured is a poor donor for a locality that prices at regional, whatever the map
  says.
- `ℓ_a` is **fitted per category profile from a semivariogram** on mature cells, not chosen. Expect
  roughly 2–5 km for loose produce, 20–60 km for graded materials (haulage-shaped), and effectively
  national for durables. Refit quarterly; a hardcoded length scale will be wrong in the second
  country the product launches in.
- `m(·,·)` blocks the pathological pool: a supermarket price must not be pooled into an informal
  market band. Where `cell_channel_split` is on, cross-channel weight is zero, not small.

Pooled estimation uses the same robust machinery as a single cell — weighted median, MAD rejection,
influence caps — so pooling cannot be used to launder an outlier from a neighbouring locality.

**Guard against pooling collapse.** If the kernel mass from outside the home locality exceeds 0.7,
the answer is really a regional estimate wearing local clothes; it is demoted to L2 and labelled as
such. Without this rule, a locality with one observation and many neighbours reports
`NEIGHBOR_POOLED` forever and never appears to need more data.

## Temporal handling

The current model treats a cell as a week and freshness as decay toward useless. That is right for
produce and wrong for the rest:

- **Durables decay in level, not in relevance.** A phone price from three months ago is highly
  informative *after* applying `price_decay_per_month`. Discarding it wastes the only observation
  the cell has. Apply the decay, widen for the extrapolation, keep the observation.
- **Materials are step-functioned.** Cement moves on supplier announcements, not smoothly. The
  Bayesian online change-point detector from doc 04 (L6) already exists for attack discrimination;
  the same detector serves here, and a detected step *truncates* the pooling window rather than
  averaging across it.
- **Produce is seasonal and mean-reverting.** Unchanged from doc 03.

One consequence worth stating plainly: `freshness_days > 21 ⇒ no verdict`, currently a global
constant, becomes `freshness > profile.max_verdict_age`. Twenty-one days is right for tomatoes and
absurd for a washing machine.

## Price input: validation before submission

The user can correct the price. That input is the product's most valuable asset and its largest
attack surface, and most bad inputs are **honest mistakes**, not attacks — which means the response
should be a question, not a rejection.

Client-side, before anything is queued (`:domain`, unit-tested, no network required):

| Check | Example | Response |
|---|---|---|
| Currency minor-unit confusion | `50.00` typed where the currency has no minor unit | Reformat and confirm |
| Order-of-magnitude | Input is 10× or 0.1× the prior band's P50 | "Did you mean 250 or 2,500?" — offer both |
| Digit transposition | Input is a transposition of a plausible value | Silent flag; raises the evidence ask |
| Unit mismatch | Per-kg price entered where the item sells per bag | Show both interpretations, make the user pick |
| Quantity bounds | 400 kg of tomatoes bought by a shopper | `profile.quantity_bounds` violation → re-ask |
| Total vs unit price | Paid 900 for 3 items | Explicit "total" / "each" toggle, never inferred |

The "total vs each" ambiguity deserves emphasis: it is the single most common corruption in every
crowdsourced price dataset, it is invisible after the fact, and it is fully preventable with one
toggle at entry.

**The escalation path is evidence, not refusal.** When an input is far outside the prior band, the
app does not discard it — the price may simply have moved, and a system that rejects surprise can
never learn about inflation. Instead it offers the highest-value thing available: *"That's higher
than we've seen. Snap the price tag and it'll count for much more."* A photographed price tag enters
at provenance tier `RECEIPT_OCR` (weight 1.15 — deliberately above a typed price, per doc 04),
turning the most suspicious input into the strongest evidence in the system. This converts an
adversarial moment into a data-quality win and is the correct answer to "validating that input".

Server-side, on intake: the doc-04 chain is unchanged (signature → attestation → nonce → geo →
trust weight), plus a **sequential plausibility test** against the current cell posterior. An
observation more than `k` MADs from the posterior is not rejected; it is admitted with `status =
pending` and flagged as a change-point candidate. Whether it was a lie or the first sign of a price
shift is not knowable from one observation, and pretending otherwise is how a price index goes
stale and confidently wrong.

## Learning from corrections

The system already stores `predicted_*` alongside the user's answer, which is the hard part. What is
missing is the loop that closes on it. Four feedback paths, each with an owner:

1. **Cell re-aggregation** (exists, doc 04 L6). Corrections move bands.
2. **Calibration feedback** (new). Per `(archetype, basis, locality maturity)`, track empirical
   band coverage: of the prices users reported, what fraction fell inside the P10–P90 we had
   shown? Coverage below the 88% target means the bands are too narrow *for that basis*, and the
   shrinkage strength is adjusted. This makes the honesty of the uncertainty a measured quantity
   rather than an assertion — and it is measurable per rung of the ladder, so extrapolation quality
   is auditable rather than assumed.
3. **Kernel refit** (new). Prediction error as a function of donor distance is exactly the data
   needed to refit `ℓ_a`. The ladder generates its own training signal.
4. **Recognition curation** (exists, doc 04 L9). Label corrections → prototypes, unchanged.

**Guard against self-confirmation.** An agreement-based system that shows a band and then rewards
users for agreeing with it converges on its own initial guess regardless of reality. Doc 04 names
this and defers the fix (Bayesian Truth Serum, J-07). With civic points attached to agreement
(doc 11) it stops being optional — money makes echoing rational. Three mitigations, all required
before points go live:

- **Blind entry for a sampled fraction.** For ~10% of validations, chosen at random, collect the
  price *before* showing the band. This sample is the only unbiased estimate of the true
  distribution, and it is what the anchored majority gets scored against.
- **Anchoring drift monitor.** Compare the blind-entry distribution to the shown-band distribution
  per locality. Divergence beyond a threshold means the published band is pulling reality toward
  itself; that locality's band widens and its self-confirmation is flagged.
- **Reward information, not agreement** (doc 11). An observation that merely repeats consensus
  earns approximately nothing, which removes the incentive to echo at its root.

## Serving

Nothing on this ladder runs MCMC at request time. The nightly regional fit exports a compact
parameter table (item effects, locality offsets `α`, seasonal splines, per-profile length scales);
serving is a lookup plus arithmetic, with a Redis cache keyed by
`(item_ref, unit, condition, channel, scope, period, model_version)`.

The client's offline path walks the same ladder against its cached cells, reaching only L1, and
reports `freshness_days` honestly. **Never hide staleness** — doc 03's rule, which matters more when
the answer may now be extrapolated in space as well as time.

## Targets

| Metric | Target | Notes |
|---|---|---|
| Band coverage, `LOCAL_OBSERVED` | ≥ 88% | doc 00 target, unchanged |
| Band coverage, `NEIGHBOR_POOLED` | ≥ 85% | wider bands must earn their width |
| Band coverage, `REGIONAL_MODEL` | ≥ 80% | |
| Band coverage, `SKU_NATIONAL_ADJUSTED` | ≥ 80% | durables; validate before enabling verdicts |
| MdAPE, `LOCAL_OBSERVED`, mature | ≤ 12% | |
| MdAPE, `NEIGHBOR_POOLED` | ≤ 20% | |
| Share of queries answered above `INSUFFICIENT` | ≥ 75% at 90 days post-launch per locality | the ladder's reason to exist |
| Blind-entry vs anchored median divergence | ≤ 5% | anchoring guard |

Backtesting is temporal *and* spatial: hold out whole localities, not only later weeks, and measure
whether the ladder would have priced them correctly from their neighbours. A purely temporal split
cannot detect a broken kernel, and the kernel is the part most likely to be wrong.
