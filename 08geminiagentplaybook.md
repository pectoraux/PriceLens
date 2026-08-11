# 08 — Gemini Agent Playbook

How to actually drive Gemini in Android Studio (Agent Mode) so it builds this app instead of
building *an* app.

## The core problem

A coding agent given "build a food price predictor" will produce a plausible camera app with a
hardcoded class list, a mean-based price average, and no calibration. It will look right and be
worthless. Everything below exists to prevent that.

Three rules govern all agent work here:

1. **One ticket per session.** Never "implement Epic D". Context degrades, the agent starts
   inventing, and reviewing 3,000 lines of speculative code is slower than writing it.
2. **Contracts before implementation.** Interfaces and data classes are written and reviewed as
   their own step. The agent implements against a reviewed contract, not against a description.
3. **Tests are part of the ticket, not a follow-up.** A ticket with no test for its acceptance
   criteria is not started.

## Setup

1. **Android Studio** on a recent stable release with Gemini Agent Mode enabled.
2. **Open the project at the module root** so the agent can see the full Gradle graph. Agents
   reason badly about modules they cannot see.
3. **Create `GEMINI.md` at the repository root** (below). Agent Mode reads it as standing context
   on every session — this is the highest-leverage file in the repo.
4. **Attach context explicitly per session.** Do not rely on the agent finding the right doc.
   Attach the ticket file and the one or two spec docs it depends on. Nothing else.
5. **Enable the build/test tooling** so the agent can run `./gradlew` itself. An agent that
   cannot run tests cannot self-correct, and you become the compiler.

## `GEMINI.md` — repository standing context

**The live file is [`GEMINI.md`](GEMINI.md) at the repository root.** It is not duplicated here —
an inline copy drifts from the real one within a month, and a stale standing-context file is worse
than none, because the agent trusts it completely.

Read it before writing any ticket prompt. Keep it short: it is loaded on every session, so every
line competes for the agent's attention with the ticket itself. Update it when an invariant
changes, not when a detail does.

**Backend caveat.** `GEMINI.md` is Android-shaped, and epics L, M and N are substantially backend
work (Alembic, FastAPI, Celery, the ledger). Either add an equivalent standing-context file next to
`backend/`, or state the backend rules explicitly in each backend ticket prompt. Do not assume the
Android rules transfer — several of them do not.

## Per-ticket prompt template

```
TICKET: {id} — {title}
SPEC: read docs/{n}-{doc}.md sections {sections} before writing any code.
      Follow GEMINI.md architecture rules without exception.

SCOPE — implement exactly this, nothing more:
{the ticket's description}

FILES you may create or modify:
{explicit list from the ticket}

DO NOT:
- modify any file outside that list
- add a dependency not listed in the ticket
- implement anything from a later ticket, even if it seems natural
- weaken or skip a test to make it pass

ACCEPTANCE CRITERIA — each needs a test that actually exercises it:
{criteria}

PROCESS:
1. Write the interfaces and data classes first. Show them to me. Stop.
2. After I approve, implement.
3. Write tests for each acceptance criterion.
4. Run ./gradlew :{module}:test and fix failures.
5. Summarize what you built and anything you were unsure about.

Start with step 1 only.
```

The two-step stop at step 1 is what keeps this productive. Reviewing an interface takes 90
seconds; reviewing a wrong implementation takes an hour.

## Worked example — a hard ticket

```
TICKET: D-03 — Canonical camera space reprojection
SPEC: read docs/02-device-profiling-and-calibration.md, sections "Optical profile" and
      "Canonical camera space", in full. This ticket is the accuracy foundation of the
      entire app; a silently wrong principal point produces plausible output and costs
      accuracy invisibly. Correctness matters far more than elegance here.

SCOPE:
Implement projection from a device's native camera frame into canonical camera space:
55° hFOV, 448×448, 0.1228 °/px angular resolution.
  1. Undistort using Brown-Conrady coefficients from LENS_DISTORTION when present.
  2. Build the homography from device intrinsics (real or derived) to canonical intrinsics.
  3. Remap with bilinear sampling.
  4. If native hFOV < 55°, letterbox — never upscale — and set the NARROW_FOV degradation.

FILES:
  ml/deviceprofile/src/main/kotlin/.../CanonicalProjector.kt
  ml/deviceprofile/src/main/kotlin/.../Intrinsics.kt
  ml/deviceprofile/src/main/kotlin/.../DistortionModel.kt
  ml/deviceprofile/src/test/kotlin/.../CanonicalProjectorTest.kt

DO NOT: touch the photometric pipeline (D-05), add native/JNI code, or add a dependency.
        Use RenderScript replacements only — no deprecated APIs.

ACCEPTANCE CRITERIA:
1. Given synthetic intrinsics for a 13mm ultra-wide and a 24mm main camera viewing the same
   simulated 6cm object at 30cm, the object's canonical-space pixel extent matches within 2%.
2. Round-trip distort→undistort of a synthetic grid recovers corner positions within 0.5 px.
3. Missing LENS_DISTORTION → undistortion skipped, NO_DISTORTION_COEFFS degradation recorded,
   no crash.
4. Native hFOV of 40° → letterboxed output, NARROW_FOV recorded, no upscaling.
5. Projection completes in ≤ 25 ms p95 for 1920×1080 input on the reference device
   (instrumented benchmark).
6. Principal point offset is honored: a synthetic camera with c_x offset by 50 px produces a
   correctly centered canonical frame.

PROCESS: interfaces first, stop for review. Then implement, then tests, then run
./gradlew :ml:deviceprofile:test.

Start with step 1 only.
```

Note what the criteria have in common: each is checkable by a machine, and several use
*synthetic* inputs so they do not depend on physical hardware. Acceptance criteria that require
a real device in a real market cannot be part of an agent loop — those belong in the phase gate,
not the ticket.

## Where agents reliably go wrong on this project

| Failure | Symptom | Prevention |
|---|---|---|
| Skipping normalization | Inference code takes `ImageProxy` directly | The GEMINI.md rule, plus a lint check (A-04) that flags ML modules importing `androidx.camera` |
| Hardcoding a class list | `val foods = listOf("apple", "banana", ...)` | Explicit prohibition; taxonomy always comes from `:core:data` |
| `mean()` on prices | Anywhere in aggregation | GEMINI.md rule + a CI grep gate for `.average()` in pricing modules |
| Floating-point money | `Double` price fields | Ktlint custom rule (A-04) |
| Silently swallowing errors | `try { } catch { null }` | Detekt rule; `Result<T>` convention |
| Inventing API endpoints | Client code for a route not in openapi.yaml | Generate the client from the spec; never hand-write it |
| Threshold sprawl | Magic numbers in feature modules | All thresholds in `:domain/policy/Thresholds.kt`, checked in review |
| Over-scoping | 12 files changed for a 3-file ticket | The explicit FILES list; reject the diff and re-prompt |
| Fake-passing tests | Assertions that always hold | Review every test's assertion, not just its name. This is the one an agent will not catch for you |
| Deprecated camera APIs | `Camera` (deprecated), RenderScript | Pin the API surface in GEMINI.md |

## Ticket suitability for agent work

Not all of these tickets suit an agent equally.

**Excellent (hand over confidently):** module scaffolding, Room and DataStore schemas, Compose
UI against a spec, Ktor client from OpenAPI, WorkManager plumbing, unit tests, the FTS picker,
localization scaffolding, the sync queue.

**Good with tight review:** the canonical projector, delegate parity harness, HNSW integration,
fusion implementation, capture sealing, the consensus state machine.

**Poor — humans should lead, agents assist:** model selection and export (E-02, E-03), fusion
weight fitting, calibration, the hierarchical price model, collusion detection tuning, taxonomy
construction, anything requiring judgment about a metric trade-off, and every threshold value
in the system. These need someone who can look at a validation curve and know it is lying.

**Never delegate:** the choice of what the acceptance criteria *are*, the abstention thresholds,
and the decision to promote a model. Those are product judgments with real consequences for
users acting on a price.

### Epics L, M, N — the same four tiers

The multi-category and civic-points work ([doc 12](12auditandmigration.md)) splits along one clean
line: **agents build mechanisms, humans supply numbers.** Every value in a `category_profile` row
is a product judgment; the resolver that reads it is boilerplate.

| Tier | Tickets |
|---|---|
| **Excellent** | L-00, L-01, L-02, L-03, L-04, L-05, M-01, M-07, N-02, N-13 |
| **Good, tight review** | L-06, L-07, L-09, L-10, L-11, M-02, M-05, N-03, N-04, N-10 |
| **Poor — human leads** | M-03, M-04, M-09, M-10, M-11, L-13, N-05, N-07, N-11 |
| **Never delegate** | N-01 (data licence), N-12 (payout rails, KYC), `CONTRIBUTOR_SHARE` and the pool split, every `category_profile` value, the decision to turn payouts on |

**L-02 is the ideal agent ticket** and a good one to start on: a pure refactor with an exact
oracle — `ConsensusPolicyTest`, `PriceVerdictPolicyTest` and `AggregationTest` must pass
**unchanged**, because behaviour is required to be bit-identical afterwards. An agent cannot fake
that, and you can review it in minutes.

The M-tier statistical work (kernels, length-scale fitting, calibration feedback, the anchoring
monitor) is doc 08's "poor" category almost by definition: it needs someone who can look at a
validation curve and know it is lying. Have the agent write the harness and the plots; read them
yourself.

## Session hygiene

- **New session per ticket.** Context rot is real and its symptom is confident wrongness.
- **Commit per ticket**, message `[{ticket-id}] {title}`, so a bad ticket is one revert.
- **Run the full test suite before merging**, not just the module's — cross-module regressions
  are where agent changes bite.
- **When the agent is stuck in a loop** (same fix twice, still failing), stop it. Read the code
  yourself. The loop means the ticket is under-specified or the spec is wrong, and more prompting
  will not fix either.
- **Ask the agent to explain the code it just wrote** on anything in the "poor" list above. If
  the explanation is vague, the code is wrong.
