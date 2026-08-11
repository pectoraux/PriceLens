# PriceLens — agent standing instructions

## What this app is
A food price predictor. Camera → recognize the food → estimate a fair local price band → 
user validates both → the correction feeds a locality price database. Full specification in 
`docs/`. Read the spec doc named in the ticket before writing code.

## Architecture rules — never violate these
- Module dependencies point downward only. `:domain` is pure Kotlin: no Android, no Room, 
  no Retrofit imports. Features never depend on other features.
- No model ever receives a raw camera frame. Every frame goes through 
  `DeviceProfileNormalizer` first. If you are writing inference code that takes an `Image` 
  or `Bitmap` directly from CameraX, you are doing it wrong.
- Never a mean over prices. Weighted median with MAD outlier rejection. Always.
- Prices are `Long` minor units plus an ISO-4217 code. Never `Double`, never `Float`.
- Every predictor returns a calibrated confidence. Abstention is a valid, expected output 
  and the UI has a designed state for it.
- Confidence thresholds and abstention rules live in `:domain` only. Never inline a 
  threshold in a feature module or an ML module.
- User corrections are never written directly to a model, index, or published price. They 
  become `ObservationDraft` records and nothing more.

## Stack
Kotlin 2.x, JDK 17, Compose + Material 3, CameraX with Camera2 interop, LiteRT, Hilt, Room, 
DataStore Proto, WorkManager, Ktor client, kotlinx.serialization, Turbine + MockK + Robolectric.

## Conventions
- Public APIs are documented with KDoc explaining *why*, not what.
- No `!!`. Use `Result<T>` from `:core:common` for fallible operations.
- All suspend functions take a `CoroutineDispatcher` parameter injected from 
  `:core:common.Dispatchers` — never hardcode `Dispatchers.IO`.
- Compose: stateless composables plus a `@Preview` per state, including error and abstain.
- Test naming: `` `method should behaviour when condition` ``.
- Camera and ML code must have a fake/test-double implementation behind the same interface.

## What to do when unsure
Stop and ask. Do not invent an architecture, a threshold, a model choice, or a schema field. 
Do not add a dependency that the ticket does not name. If the ticket seems to require a 
decision that is not in the spec, say so instead of choosing.
