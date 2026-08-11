# 11 — Civic Points, Data Revenue & the Partner Platform

> Contributors who supply verifiably correct price data earn civic points; points entitle them to a
> share of revenue earned from selling aggregate data to governments, NGOs and commercial partners
> through the API and analytics platform.

## Read this first: the conflict with doc 00

Doc 00 lists two deliberate anti-features that this requirement contradicts head-on:

> **No gamified leaderboard.** Leaderboards are a Sybil-farming incentive, directly at odds with
> data integrity. Contribution feedback is private and qualitative.

and doc 04, L5:

> Reputation is never displayed. A visible score is a target to game.

Those are correct, and the reasoning behind them does not stop being true because a reward exists.
Attaching money to contribution changes the threat model in a specific way: doc 04's adversaries
(T1–T7) are motivated by *moving a price*, which is a narrow interest held by few people. Payment
creates a new adversary — **T9, the yield farmer** — who does not care what the price says and simply
wants volume to convert into cash. T9 is not rare and not ideological. They are the single most
numerous attacker any paid-crowdsourcing system faces, and every naive scheme (pay per submission,
pay per accepted submission, leaderboard bonuses) is defeated by them on day one.

The reconciliation, which the rest of this document builds:

1. **Two separate ledgers.** Reputation stays private and stays the only thing that weights data.
   Civic points are public *to their owner only*, and weight nothing. A contributor cannot buy
   influence over the index with points, and cannot convert data influence into money except by
   being right.
2. **No leaderboard.** Ever. The visible social object is **locality coverage** — "this market is
   62% covered, 14 items still need a first price" — a collective goal, not a ranking. It creates
   the pull that a leaderboard creates without creating a farming target.
3. **Points reward information, not volume.** This is the load-bearing decision, developed below.

Doc 00's anti-feature list is amended accordingly rather than silently violated: the amendment is
"no leaderboard and no visible reputation", not "no rewards".

## What earns points

An observation earns points when, and only when:

- it reaches **`CONFIRMED`** through the doc-04 L7 consensus state machine — never at submission,
  never on acceptance;
- it survives the arbitration and reversal window (30 days);
- its contributor is not in a collapsed collusion cluster at vesting time;
- and the cell it belongs to is **published** (has cleared the maturity gate).

Nothing is paid for merely arriving. The delay is not friction to be optimized away — it is the
mechanism. A farm cannot be paid before detection, which means the cost of detection is borne by
the attacker rather than by us.

### Points measure information gain, not effort

The award for observation *o* in cell *c*:

```
IG_o  =  H(posterior of c without o)  −  H(posterior of c with o)      [nats, LOO]
points_o = round( K · max(0, IG_o) · profile.info_value_multiplier · q_o )
```

where `H` is the differential entropy of the cell's price posterior, computed leave-one-out over
the confirmed set, and `q_o ∈ [0,1]` is a quality factor from provenance tier (a photographed price
tag earns more than a typed number, matching its higher trust weight).

The consequences of this formula are exactly the behaviours we want, and they fall out rather than
being bolted on:

| Situation | IG | Effect |
|---|---|---|
| First price ever for an item in an uncovered market | large | Strongly rewarded — the coverage frontier pays best |
| 40th confirmation of a well-measured cell | ≈ 0 | Farming a busy market is worthless |
| Duplicate of one's own earlier observation | 0 | Near-duplicate detection already collapses it |
| A genuine price *change* others have not yet caught | large | Rewards vigilance, which is what a price index needs |
| Correct observation in a thin, high-variance cell | large | Steers effort to where the index is weak |
| Echoing the displayed band in a cell that already agrees | ≈ 0 | Removes the self-confirmation incentive of doc 10 |

That last row is the reason this formula is preferred to anything simpler. Paying for *agreement*
makes echoing the shown band the optimal strategy and quietly destroys the dataset. Paying for
*information* makes echoing worthless, with no need to detect it.

Leave-one-out is an approximation of the Shapley value, which is the theoretically correct
attribution and is exponential in cell size. LOO under-credits genuinely redundant contributions —
which here is the desired behaviour, not an error. Cells are small (tens of observations), so the
computation is cheap and, importantly, **deterministic**: any auditor with the confirmed record can
recompute every award. That reproducibility is what makes the payout defensible when someone
disputes it.

### Anti-farming, in economic terms

The design target is that the expected payout from a fabricated observation is below the cost of
producing one that survives the gates. Working through T9's options:

- **Submit agreeing fabrications in covered markets** → IG ≈ 0 → earns nothing.
- **Submit disagreeing fabrications** → never reaches `CONFIRMED` alone → earns nothing, and burns
  reputation at 2.5× (doc 04, L5).
- **Fabricate a whole uncovered locality** — the high-IG attack, and the real one. Mitigation:
  points in an unmatured cell accrue to **escrow** and vest only when the cell matures with
  independent contributors and the observation is retro-confirmed against them. Inventing a market
  earns nothing until real people arrive and agree; if they never arrive, it never pays.
- **Coordinate a ring to self-confirm** → doc 04's L8 collapses the cluster's *weight*; here it
  additionally collapses the cluster's **points to those of a single contributor**, so the ring
  splits one share N ways. Coordination becomes strictly worse than acting alone.
- **Print money by scale** → impossible by construction: the points pool is denominated in *shares
  of realized revenue*, not in currency per datum (below). More farmers dilute each other, they do
  not increase the payout.

Residual risks that this does **not** solve and which need operational owners: a real person in a
real market who reports real prices from a *different* stall than the one they photographed; and
low-wage organized collection that is technically legitimate but distorts the contributor mix.
Both are detection problems for trust & safety, not architecture problems, and both should be on
the transparency report.

## The ledger

Append-only, double-entry, and reconstructible from the observation record. The ledger is a
*derived* artifact: if it is ever corrupted, it can be rebuilt from `observation` and the published
cells, which is a property worth protecting deliberately.

```sql
CREATE TABLE civic_ledger_entry (
  id              BIGSERIAL PRIMARY KEY,
  contributor_id  UUID NOT NULL REFERENCES contributor(id),
  entry_type      TEXT NOT NULL,   -- ACCRUAL|VEST|CLAWBACK|PAYOUT|ADJUSTMENT|EXPIRY
  points          BIGINT NOT NULL, -- signed. integer. never float
  observation_id  UUID REFERENCES observation(id),
  cell_key        TEXT,            -- attribution target, see below
  period          DATE NOT NULL,
  reason_code     TEXT NOT NULL,
  computation     JSONB NOT NULL,  -- {ig_nats, q, multiplier, k} — recomputable
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  reversal_of     BIGINT REFERENCES civic_ledger_entry(id)
);
CREATE INDEX ON civic_ledger_entry (contributor_id, period);
CREATE UNIQUE INDEX ON civic_ledger_entry (observation_id, entry_type)
  WHERE entry_type IN ('ACCRUAL','VEST');

CREATE TABLE contributor_balance (      -- materialized, rebuildable from the ledger
  contributor_id  UUID PRIMARY KEY REFERENCES contributor(id),
  escrowed_points BIGINT NOT NULL DEFAULT 0,
  vested_points   BIGINT NOT NULL DEFAULT 0,
  paid_points     BIGINT NOT NULL DEFAULT 0,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Clawback is not optional.** Doc 04's influence tracing already requires that a later-discovered
abusive account's contributions can be subtracted from prototype centroids. The same event must
reverse its ledger entries. Points already *paid out* are not recovered from the user — chasing
small sums from people who may have been unaware is not worth the reputational cost — but the
contributor is moved to `limited` status and future accrual stops. Points still in escrow are
reversed in full.

Points **expire** after 24 months if unclaimed, which bounds the liability and is disclosed up
front.

## From revenue to payout

### Why revenue share and not a price per datum

Paying a fixed amount per accepted observation creates unbounded liability (cost scales with
supply, not with income), and in most jurisdictions starts to look like piece-rate work, with the
employment, tax and minimum-wage consequences that follow. A **share of realized revenue** is
bounded by definition, aligns contributor and platform interests, and is honestly describable:
*"when we sell this data, a fixed share of what we receive goes to the people who produced it."*

```
contributor_pool(period) = net_data_revenue(period) × CONTRIBUTOR_SHARE
```

`CONTRIBUTOR_SHARE` is a published constant (recommend 40%, set once, changed only with notice).
`net_data_revenue` is receipts from data products net of payment processing — not net of company
costs, which would be unauditable from outside and would poison the trust the scheme is meant to
build.

### Two-pool split, and why

Distributing the pool purely by API usage is defensible but produces a perverse geography: dense
urban markets that partners query most would earn nearly everything, while thin rural markets —
where the civic value of price transparency is *highest* and data is hardest to collect — would earn
almost nothing. So the pool splits:

```
usage_pool    = contributor_pool × 0.70    distributed by attributed API usage
coverage_pool = contributor_pool × 0.30    distributed by information gain, usage-blind
```

The usage pool follows a clean chain of attribution: **revenue → API responses → constituent cells →
observations → contributors.**

1. Every API response logs the cell keys it was computed from (`api_usage_cell`, tamper-evident,
   append-only — it is the basis of a payment, so it is treated as a financial record).
2. A partner's revenue for the period is attributed across the cells they consumed, weighted by
   query volume, with a cap so a single obsessive partner cannot direct the whole pool at one cell.
3. Each cell's attributed revenue is distributed to its contributors in proportion to their `IG`
   share within that cell.

The coverage pool uses the same `IG` shares but ignores query volume entirely, so a first price in a
neglected market earns regardless of whether anyone has queried it yet. That is what makes the
scheme *civic* rather than merely commercial.

### Payout mechanics

| Concern | Decision |
|---|---|
| Rails | Mobile money first (M-Pesa and equivalents), because that is what the target users have. Bank transfer where available. |
| Threshold | Minimum payout, set per country to exceed transfer fees by a wide margin. Below it, the balance rolls over. |
| Cadence | Quarterly. Frequent micropayouts are dominated by fees and increase KYC surface. |
| Identity | Payout requires identity verification at the country's regulatory threshold — **not** contribution. Reading and contributing never require KYC. |
| Non-cash option | Airtime/data top-up and a donate-to-locality option. In several target jurisdictions this materially reduces the tax and licensing burden for both sides, and some users prefer it. |
| Tax | Contributor is responsible; the platform reports where required. Needs country-by-country legal review before launch, not after. |
| Transparency | Every contributor can see their own ledger with the `computation` field for each entry — the actual numbers, not a score. |

**The contributor agreement must grant the licence to sell aggregates.** This is a hard blocker, not
a formality: without an explicit, informed grant at signup, the entire revenue side is
unsellable, and retrofitting consent across an existing contributor base is close to impossible.
It belongs in the first version of the terms, before the first observation is collected.

## The partner platform

The revenue side is a product, not an endpoint. What partners actually buy:

| Product | Buyer | Shape |
|---|---|---|
| Locality price series | NGO, research, journalism | Time series per (item, locality, unit) |
| Basket / CPI-style index | Government statistics, central banks | Weighted basket over a locality or region, with methodology disclosed |
| Coverage & availability | Humanitarian response | Which items are priced, where, how fresh; stockout proxies |
| Price shock alerts | Early warning, food security | Webhook on change-point detection in a watched cell set |
| Cross-market comparison | Consumer protection, competition authorities | Same item across localities/channels |
| Category dashboards | Commercial, trade bodies | Construction materials or FMCG movement by region |

Tiering deliberately keeps the public good free:

- **Public** — free, no key: coarse aggregates, delayed 30 days, published cells only. This tier is
  a commitment, not a funnel. The product's legitimacy with governments and NGOs depends on the
  basic data being a public good.
- **Research** — free with attribution, application-gated: full history, finer geography, no
  redistribution.
- **Commercial / Government** — paid, metered, SLA-backed, near-real-time, webhooks, bulk export.

### Privacy and disclosure control on the sell side

Selling aggregates from a dataset built on individual observations is where a project like this
does harm if it is careless. Non-negotiable output rules, enforced in the query layer rather than
in documentation:

- **Never a seller.** No stall, vendor or business identity exists anywhere in the schema (doc 00),
  so it cannot leak. This constraint is *load-bearing* for the commercial product and must survive
  every future schema change.
- **k-anonymity on every cell served:** suppress any cell with fewer than 5 distinct contributors,
  regardless of tier. In a small market a 2-contributor cell is effectively a named person's
  shopping record.
- **Query auditing against reconstruction.** A metered API that answers many overlapping
  fine-grained queries can be differenced back toward individual observations. Each partner has a
  per-period **privacy budget** over fine-grained queries; exhausting it degrades responses to
  coarser scopes rather than blocking. This is a real attack on this exact architecture and is not
  addressed anywhere in docs 00–08.
- **Contributor unlinkability.** No partner-facing surface ever exposes a contributor identifier,
  a count that could isolate one, or a timestamp finer than the cell period.

### Metering

```
GET  /v1/partner/series?item=&scope=&from=&to=&unit=      metered, k-anon enforced
GET  /v1/partner/index?basket=&scope=&period=             CPI-style, methodology in response
GET  /v1/partner/coverage?scope=                          free tier eligible
POST /v1/partner/alerts                                   webhook subscription
GET  /v1/partner/usage                                    partner's own consumption + budget left
```

Every response carries `X-PriceLens-Cells-Used` and writes the `api_usage_cell` rows that drive
attribution. If that log is wrong, contributors are paid wrongly, so it is written in the same
transaction as the response accounting and reconciled nightly against the billing meter.

## What the contributor sees

Deliberately narrow, in line with doc 00's posture:

- Their own balance: escrowed, vested, paid. With the reason for each entry.
- **Locality coverage**, collective: what this market still needs. This is the motivational
  surface, and it is a map, not a ranking.
- "Your prices were used in N partner queries this quarter" — aggregate, no partner names.
- **Never**: reputation, trust weight, attestation tier, other contributors' points, any ranking.

The point of the whole design is that the honest strategy — go where data is missing, report what
you actually paid, photograph the price tag — is also the highest-earning strategy. If that ever
stops being true, the formula is wrong and the dataset is already degrading.

## Targets

| Metric | Target |
|---|---|
| Fabricated observations reaching a vested payout, red-team suite | 0 |
| Points paid on cells later reversed | ≤ 0.5% of period issuance |
| Ledger reconstructible from observations, byte-identical | 100%, verified in CI |
| Gini of points across active contributors | ≤ 0.6 — a farm-dominated distribution is the alarm |
| Share of coverage-pool points going to `seeding`/`learning` localities | ≥ 40% |
| Contributor-visible ledger disputes upheld | tracked, published in the transparency report |
| k-anonymity violations in served responses | 0, enforced in the query layer and fuzz-tested |
