# PriceLens — Food Price Predictor

**Working title:** PriceLens
**Platform:** Android (Kotlin, Jetpack Compose, CameraX/Camera2, LiteRT)
**Build agent:** Gemini in Android Studio (Agent Mode)
**Status:** Specification + delivery plan. No code yet.

---

## What this is

A complete, buildable specification and ticket pack for an Android app that:

1. **Profiles the phone it is running on** — camera intrinsics, sensor geometry, color
   transforms, noise curves, distortion coefficients, and NPU/GPU capability — and uses that
   profile to *normalize every frame into a canonical camera space* before any model sees it.
   This is what makes the vision stack device-agnostic instead of device-lucky.
2. **Detects and labels food** using an open-vocabulary detector + embedding retrieval stack,
   reinforced by OCR and barcode evidence.
3. **Estimates a fair price** for that item, in that locality, at that time — as a *band*
   (P10 / median / P90), not a false-precision point estimate.
4. **Asks the user to validate both predictions** (label and price) through a confidence-aware
   review UI that is honest about what it does not know.
5. **Learns from corrections** through a curation pipeline that is poisoning-resistant, and
   **cross-validates every submission** against public price sources and against other
   contributors in the same locality.

The end product is a locality-level fair-price database that tells a shopper, in the moment,
whether the number in front of them is reasonable.

## Non-negotiable quality bars

These drive every design decision in this pack. They are repeated as gates in the roadmap.

| Bar | Target | Enforced by |
|---|---|---|
| Label correctness (top-1, non-abstained) | ≥ 92% | [EPIC-E](tickets/02-phase-2-3-tickets.md), [eval harness](docs/06-eval-and-mlops.md) |
| Label abstention rate | ≤ 15% at that precision | Calibrated confidence + abstention policy |
| Price band coverage (true price ∈ [P10,P90]) | ≥ 88% | Quantile calibration, [EPIC-G](tickets/02-phase-2-3-tickets.md) |
| Median absolute % error on price | ≤ 12% in mature localities | Hierarchical price model |
| Confidence calibration (ECE) | ≤ 0.05 | Temperature + Dirichlet calibration |
| Cross-device top-1 spread | ≤ 3 pts between best and worst device tier | Canonical camera space |
| Poisoned-label survival to production | 0 in red-team suite | [Trust pipeline](docs/04-trust-integrity-and-learning.md) |

**Rule:** the app abstains rather than guesses. A wrong confident price is worse than no price —
it is the exact harm the product exists to prevent.

---

## How to read this pack

Read in this order. The docs define *what is true*; the tickets define *what to build*.

### Specification

| Doc | Contents |
|---|---|
| [00 — Product brief](00productbrief.md) | Users, jobs-to-be-done, scope, success metrics, risks |
| [01 — Architecture](01architecture.md) | Module graph, data flow, tech choices and why |
| [02 — Device profiling & calibration](02deviceprofilingandcalibration.md) | The phone-fingerprinting and canonical-camera-space design |
| [03 — Recognition & price ML](03recognitionandpriceml.md) | Detection, embedding, retrieval, fusion, portion, price model |
| [04 — Trust, integrity & learning](04trustintegrityandlearning.md) | Anti-tamper, consensus, reputation, poisoning-resistant learning |
| [05 — Data model & API](05datamodelandapi.md) | Schemas, OpenAPI contract, sync protocol |
| [06 — Eval & MLOps](06evalandmlops.md) | Golden sets, metrics, gates, shadow mode, rollout |
| [07 — Roadmap](07roadmap.md) | Phases, milestones, dependency graph, staffing, critical path |
| [08 — Gemini agent playbook](08geminiagentplaybook.md) | How to actually drive the agent so it produces this app |

### Beyond food — the decentralized multi-category index

Docs 00–08 specify the food v1.0. These four extend it to a price index over all consumer
categories, with a civic-points and revenue-sharing layer.

| Doc | Contents |
|---|---|
| [09 — Multi-category architecture](09categoryarchitecture.md) | Category profiles, the five archetypes, class-vs-SKU identity, the archetype router, decentralized labelling |
| [10 — Price resolution & extrapolation](10priceresolution.md) | The resolution ladder, the locality similarity kernel, price-input validation, anti-anchoring |
| [11 — Civic points & data revenue](11civicpointsandrevenue.md) | Information-gain scoring, the ledger, anti-farming economics, revenue attribution, the partner platform |
| [12 — Audit & migration](12auditandmigration.md) | What exists today, the gaps, the ordered migration, epics L/M/N |

### Tickets

107 tickets across 11 epics (A–K), plus 39 across epics L, M and N. Each with acceptance criteria
and implementation notes.

| File | Covers |
|---|---|
| [Ticket index](00ticketindex.md) | All tickets, epics, dependencies, phase assignment |
| [Phase 0–1 tickets](01phase01tickets.md) | Foundations, device profiling, calibration |
| [Phase 2–3 tickets](02phase23tickets.md) | Recognition, price core, validation UX |
| [Phase 4–6 tickets](03phase46tickets.md) | Trust, learning loop, scale and launch |
| [Audit & migration](12auditandmigration.md) | Epics L, M, N and the order to land them in |

---

## Quick start for the build agent

1. Open this repository folder in Android Studio.
2. Read [08 — Gemini agent playbook](08geminiagentplaybook.md) first. It contains the
   context-priming prompt, the per-ticket prompt template, and the guardrails that keep the
   agent from inventing architecture mid-sprint.
3. Work tickets in dependency order from the [ticket index](00ticketindex.md).
   Do not skip Phase 1 — every downstream model quality number in this pack assumes frames have
   already been normalized by the device profile.
4. Every ticket has acceptance criteria. A ticket is done when its criteria have automated
   coverage, not when the code compiles.

## A note on where this lives

This pack was written into the `cal.com` repository because that is the working tree that was
attached to the session. It is fully self-contained under `food-price-predictor/` and shares no
code, config, or dependencies with cal.com. Move the directory into its own repository before
starting implementation — nothing in it references the parent project.
