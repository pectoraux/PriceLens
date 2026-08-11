# 09 — Multi-Category Architecture

> Docs 00–08 specify a food price system. This document generalizes it to a price index over all
> consumer goods — FMCG, electronics, appliances, construction materials, household goods — without
> forking the pipeline into one stack per category.

## The problem with "just add categories"

The temptation is to widen the taxonomy and move on. That fails, because categories do not differ
only in *what the item is*. They differ in **how price is formed**, and price formation determines
the shape of nearly every component:

| | Loose tomatoes | A specific 55" TV | Cement, 42.5N, 50 kg bag |
|---|---|---|---|
| What identifies it | a class | an exact model | a class + a graded spec |
| Best evidence | appearance | model label / barcode | bag print, context |
| Does the price vary in 2 km? | strongly | barely | moderately (haulage) |
| Does it vary in 200 km? | strongly | weakly (national retail) | strongly (haulage) |
| Useful period | week | month, monotone decay | month |
| Does portion estimation apply? | yes, load-bearing | never | no — the bag is the unit |
| Two honest sellers disagree by | ±30% | ±8% | ±15% |
| Realistic observations per cell | many | ~1 per locality per quarter | few |

A single set of constants cannot serve that table. `Thresholds.CONSENSUS_PRICE_LOG_TOLERANCE =
0.25f` is approximately right for produce, far too loose for a TV (a 25% band on a $800 set is
$200 of "agreement"), and too tight for sand. The consensus rule "≥ 3 observations of the same
`(locality, item, unit)` within 14 days" is achievable for onions in a market and will *never* fire
for a specific refrigerator model in one geohash-6 cell. Applied unchanged, the existing design
does not merely underperform outside food — it silently publishes nothing at all for durables,
while reporting `INSUFFICIENT` forever.

## The core abstraction: the Category Profile

Every parameter that varies by category is lifted out of code and into a **Category Profile**: a
signed, versioned, server-authored record shipped in the catalog bundle alongside the taxonomy. The
pipeline reads it; the pipeline does not branch on category names.

```sql
CREATE TABLE category_profile (
  id                    BIGSERIAL PRIMARY KEY,
  slug                  TEXT UNIQUE NOT NULL,      -- 'fungible_loose', 'packaged_sku', ...
  archetype             TEXT NOT NULL,             -- see the five below
  version               INT  NOT NULL DEFAULT 1,

  -- identity resolution
  identity_strategy     TEXT NOT NULL,             -- CLASS | GTIN | MODEL | CLASS_PLUS_GRADE
  resolver_order        TEXT[] NOT NULL,           -- ['barcode','ocr_model','embed','context']
  requires_variant_axes TEXT[],                    -- ['capacity_l','energy_class'] etc.

  -- price cell geometry
  cell_scope_type       TEXT NOT NULL,             -- geohash6|geohash5|admin2|admin1|country
  cell_period_type      TEXT NOT NULL,             -- week|month|quarter
  cell_channel_split    BOOLEAN NOT NULL,          -- price differs by retail channel?
  cell_condition_split  BOOLEAN NOT NULL,          -- new vs used priced separately?

  -- spatial behaviour (doc 10)
  pooling_length_km     NUMERIC(8,2) NOT NULL,     -- kernel length scale, fitted not guessed
  allows_national_ref   BOOLEAN NOT NULL,          -- can a national SKU price anchor a locality?
  price_decay_per_month NUMERIC(6,4),              -- durables depreciate; produce does not

  -- agreement and consensus
  log_tolerance         NUMERIC(4,3) NOT NULL,     -- honest-disagreement width
  consensus_min_obs     INT NOT NULL,
  consensus_min_contributors INT NOT NULL,
  consensus_window_days INT NOT NULL,
  consensus_scope_type  TEXT NOT NULL,             -- may be coarser than cell_scope_type

  -- measurement
  portion_estimation    TEXT NOT NULL,             -- REQUIRED | OPTIONAL | FORBIDDEN
  canonical_units       TEXT[] NOT NULL,
  quantity_bounds       JSONB NOT NULL,            -- plausibility, replaces MAX_PLAUSIBLE_KG

  -- economics (doc 11)
  info_value_multiplier NUMERIC(4,2) NOT NULL DEFAULT 1.0,

  effective_from        TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

`Thresholds.kt` stops being a table of constants and becomes a resolver over the active profile.
The constants that remain global are the ones that genuinely are: abstention thresholds on
calibrated probabilities, integrity limits, travel plausibility.

### The five archetypes

**1. `FUNGIBLE_LOOSE`** — loose produce, grains, pulses, sand, gravel, firewood, charcoal.
Identity is a *class*. Price is intensely local and weekly. Portion estimation is load-bearing.
This is the system docs 00–08 already describe; it becomes one archetype among five rather than the
whole product.

**2. `PACKAGED_SKU`** — FMCG, packaged food, toiletries, batteries, small electronics accessories.
Identity is a **GTIN**, and the barcode path is not a hint but near-ground-truth. Portion estimation
is *forbidden* — the pack states its net contents, and a vision-estimated mass would be strictly
worse than reading the label. The unit is the pack, with per-canonical-unit derived from declared
net contents. Prices vary by retail channel (kiosk vs supermarket vs wholesaler) more than by
distance; `cell_channel_split = true` is essential or the band becomes a bimodal smear.

**3. `DURABLE_MODEL`** — TVs, phones, laptops, refrigerators, cookers, generators, power tools.
Identity is a **manufacturer model**, resolved from the model-number label, box, or spec plate via
OCR before vision is consulted. Observations are sparse everywhere: the cell scope must be coarse
(admin1 or country), the period monthly, and consensus must be allowed to corroborate *across*
localities — legitimate, because the object is literally identical in both places, which is not true
of two tomato piles. Durables also depreciate monotonically; `price_decay_per_month` lets a
three-month-old observation still inform today's band instead of being discarded as stale.

**4. `GRADED_MATERIAL`** — cement, rebar, timber, roofing sheets, paint, tiles, blocks, pipe.
Identity is a class plus a **graded specification** (rebar Ø12 mm vs Ø16 mm are different goods,
not variants). Price is regional — haulage cost means distance matters, but per-market variation is
modest. Bought in supplier tiers, so channel split is on. Portion estimation is forbidden; these are
sold in defined trade units (bag, tonne, length, sheet, m³) and the correct behaviour is to require
the unit, never to infer it.

**5. `SECOND_HAND`** — a *modifier*, applied over `DURABLE_MODEL` and some `GRADED_MATERIAL`, not a
standalone archetype. It adds a `condition` axis to the cell key and widens `log_tolerance` sharply,
because two used fridges of the same model genuinely are not the same good. Keeping it as a modifier
rather than a sixth archetype means the recognition path is unchanged — a used fridge looks like a
fridge — and only pricing branches.

Adding a category later means inserting a `category_profile` row and pointing taxonomy items at it.
It does not mean a release.

## Identity: items are not SKUs

The current schema has one identity table, `taxonomy_item`, and it is a *class*. That is sufficient
for food and insufficient for everything else. "Top-freezer refrigerator" and "Samsung RT28
253 L top-freezer refrigerator" are both real, both needed, and are not the same row.

- The **class** is what makes prices comparable across markets and countries, and what a CPI-style
  index is computed over.
- The **SKU/model** is what a user actually photographs and what a price is actually attached to.

So identity becomes two levels, with observations attaching to the most specific one available:

```sql
CREATE TABLE product_sku (
  id              BIGSERIAL PRIMARY KEY,
  item_id         BIGINT NOT NULL REFERENCES taxonomy_item(id),  -- the class it belongs to
  gtin            TEXT UNIQUE,                    -- NULL for unbarcoded durables
  brand           TEXT,
  model_code      TEXT,                           -- 'RT28K3022S8' — what OCR actually reads
  display_name    TEXT NOT NULL,
  net_content     NUMERIC(12,4),                  -- declared, from pack or spec sheet
  net_content_unit TEXT,
  attributes      JSONB NOT NULL DEFAULT '{}',    -- {capacity_l: 253, energy_class: 'A+'}
  release_year    INT,
  status          TEXT NOT NULL DEFAULT 'active', -- active|proposed|merged|retired
  merged_into_id  BIGINT REFERENCES product_sku(id),
  source          TEXT NOT NULL,                  -- off|gs1|user_proposed|partner
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON product_sku (item_id);
CREATE INDEX ON product_sku (brand, model_code);
CREATE INDEX ON product_sku USING GIN (attributes jsonb_path_ops);
```

`observation` gains `sku_id BIGINT NULL REFERENCES product_sku(id)`. `item_id` stays mandatory —
every SKU rolls up to a class, so the class-level index is always computable, even when SKU
resolution failed. **This is the rule that keeps the index coherent:** SKU is an optional
refinement, class is the invariant.

`taxonomy_item` also gains:

```sql
ALTER TABLE taxonomy_item
  ADD COLUMN category_profile_id BIGINT NOT NULL REFERENCES category_profile(id),
  ADD COLUMN attribute_schema    JSONB,   -- which variant axes are meaningful for this class
  ADD COLUMN substitutes         BIGINT[];-- comparable classes, for substitution-based inference
```

and the produce-specific columns (`density_kg_per_l`, `shape_model`, `is_perishable`,
`seasonality`, `scientific_name`) move into a nullable `attributes JSONB` blob owned by the
`FUNGIBLE_LOOSE` profile. They are meaningless for a laptop and should not be columns on the shared
table. This is a migration, specified in doc 12.

## Units, generalized

`CanonicalUnit` (kg, L, piece, bunch, bag, crate) is a food unit list. The generalized set, per
archetype via `canonical_units`:

| Family | Units |
|---|---|
| Mass | mg, g, kg, tonne |
| Volume | ml, L, m³ |
| Count | piece, pair, dozen, pack |
| Length | mm, m, linear-m |
| Area | m², sheet, tile |
| Trade | bag, crate, bunch, bundle, roll, drum, truckload |
| Energy/service | kWh, day-hire |

Two rules survive from doc 03 unchanged and matter more at this scale:

1. **Vernacular units are locality-scoped and versioned.** A "bag" of cement is 50 kg in most
   markets and 42.5 kg in some; a "truckload" of sand is not a unit at all until measured. The
   existing `locality_unit_conversion` table is the right mechanism — it now keys on
   `category_profile` rather than `item_category`.
2. **Where no reliable conversion exists, the vernacular unit *is* canonical for that locality.**
   Do not manufacture a kg price for a truckload of sand. A price per truckload, honestly labelled,
   is more useful than a fabricated price per tonne.

New for packaged goods: **derived per-unit price**. A 900 ml oil bottle at 450 KES yields both a
per-pack observation and a per-litre derivation. Store the pack price as the observation (that is
what was paid) and compute the per-litre figure at query time from `product_sku.net_content`, so a
later correction to the declared net content retroactively fixes every derived price rather than
leaving bad data frozen in the record.

## Recognition: route before you infer

Doc 01's pipeline runs detection → embedding → retrieval for everything. Across five archetypes
that is both slower and less accurate than it needs to be, because for a barcoded item the
embedding path is a worse source than a signal already in the frame.

Insert **Stage 2.5 — Archetype Router**, between detection and the evidence stages:

```
 STAGE 2 · DETECTION  (unchanged: dominant region + person redaction)
        │
        ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ STAGE 2.5 · ARCHETYPE ROUTER                    :ml:route    │
 │ Cheap signals only, ≤ 12 ms:                                 │
 │   · barcode present in region?            → PACKAGED_SKU     │
 │   · dense machine text / model-code regex → DURABLE_MODEL    │
 │   · printed sack / slab / bar geometry    → GRADED_MATERIAL  │
 │   · organic texture, no print             → FUNGIBLE_LOOSE   │
 │ Outputs a posterior over archetypes, not a hard choice.      │
 │ Selects which Stage-3 branches run and with what weights.    │
 └────────┬─────────────────────────────────────────────────────┘
          ▼
  branch-conditional Stage 3 / 4 / 5
```

The router outputs a *distribution*, and the fusion stage marginalizes over it. A hard router that
guesses wrong is unrecoverable; a soft one degrades to running both branches, which is the current
behaviour and therefore never worse than today. The latency win comes from the confident cases: a
resolved barcode short-circuits embedding and retrieval entirely, freeing ~125 ms.

Per-archetype evidence ordering — the `resolver_order` field:

| Archetype | Order |
|---|---|
| `PACKAGED_SKU` | barcode → on-pack OCR + fuzzy SKU match → embedding → locality catalogue |
| `DURABLE_MODEL` | model-code OCR → brand-logo + form-factor embedding → catalogue → user picker |
| `GRADED_MATERIAL` | bag/label OCR → embedding → context prior (what this supplier stocks) |
| `FUNGIBLE_LOOSE` | embedding → zero-shot text → seasonality + locality prior |

**Stage 6 (portion) becomes conditional** on `portion_estimation`. For `FORBIDDEN` archetypes it is
skipped and the unit comes from the SKU's declared net content or from an explicit user choice.
This removes the single largest source of wrong prices outside food: a vision-estimated mass for an
object whose mass is printed on its own label.

Recognition targets differ by archetype and must be tracked separately or the food numbers will mask
regressions elsewhere:

| Archetype | Top-1 target | Notes |
|---|---|---|
| `PACKAGED_SKU` | ≥ 97% where a barcode is legible; ≥ 85% otherwise | GTIN resolution is the metric |
| `DURABLE_MODEL` | ≥ 90% to class, ≥ 75% to exact model | model-to-class fallback is expected |
| `GRADED_MATERIAL` | ≥ 88% to class+grade | grade confusion is the failure mode |
| `FUNGIBLE_LOOSE` | ≥ 92% | unchanged from doc 00 |

## Decentralized labelling, properly

User correction currently feeds prototype centroids (doc 04, L9). That covers *"the model got a
known item wrong"*. Across all categories, the dominant case is different: **the item is not in the
catalogue at all**, and never will be unless users can add it. There are on the order of 10⁵ FMCG
SKUs in a single country and no central team is going to enter them.

So the labelling loop needs a second track, with its own state machine:

```
PROPOSED ──┬─ duplicate of existing (embedding + name fuzzy + GTIN match) ──→ MERGED
           │
           ├─ < k independent proposers ──────────────────────────────────→ PENDING (held)
           │
           └─ ≥ k independent proposers, distinct devices, distinct
              localities or distinct sessions, no shared collusion cluster
                   │
                   ▼
              CANDIDATE ── attribute elicitation (class, archetype, default unit,
                   │        net content) from the proposers' own submissions
                   ▼
              REVIEW ──── automated checks: name is not abusive, class parent is
                   │      plausible under the embedding, no near-duplicate SKU
                   ▼
              ACTIVE ──── enters catalogue at next bundle build; prices attach
```

`k = 3` for `PACKAGED_SKU` where a GTIN corroborates (the barcode is objective evidence, so the bar
is low), `k = 5` and mandatory human review for classes, which are structural and where a bad merge
corrupts history. Proposals carry the proposer set for the same reason prototypes do: **reversibility
requires provenance retained from day one, and cannot be retrofitted.**

A GTIN that resolves against Open Food Facts or a GS1 source skips straight to `ACTIVE` with
`source = 'off'`. Most FMCG breadth comes from ingestion, not from users; the user path exists for
the long tail and for the markets those databases do not cover, which are exactly the markets this
product is for.

**Attribute elicitation is part of labelling.** When a user adds a new durable, "what is it" is not
enough — the cell key needs capacity, size, or grade. The review UI asks for the two or three axes
named in the parent class's `attribute_schema`, and asks them as choices, not free text. Free-text
attributes produce an unmergeable catalogue within a month.

## What this changes in the module graph

Three additions, no restructuring:

| Module | Owns | Must not |
|---|---|---|
| `:ml:route` | Archetype posterior from cheap frame signals | Resolve identity itself |
| `:domain:category` | Category profile resolution, unit families, per-archetype threshold lookup | Depend on Android |
| `:core:catalog` | SKU cache, attribute schemas, proposal drafts, profile bundle verification | Contain policy |

`:ml:portion`, `:ml:price`, and `:feature:review` all become profile-driven rather than
food-assuming. `:domain`'s `Thresholds` object is replaced by a `ProfileThresholds(profile)`
resolver — mechanical, but it touches every threshold call site, so it should happen before the
category work rather than after.

The dependency rule is unchanged and still enforced: `:domain` stays pure Kotlin, features never
depend on each other, category profiles arrive as data.
