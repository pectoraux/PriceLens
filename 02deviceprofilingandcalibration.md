# 02 — Device Profiling & Camera Calibration

> This is the part of the system that makes everything else work. A food recognition model that
> scores 94% on a Pixel and 71% on a budget device with an ultra-wide lens under fluorescent
> light does not have a model problem. It has a normalization problem.

## The thesis

Two phones photographing the same tomato produce wildly different tensors:

- **Geometry.** A 13 mm ultra-wide and a 77 mm tele put the same object at radically different
  apparent scales and with different barrel/pincushion distortion. Object scale is a *feature*
  the model uses (a cherry tomato vs a beefsteak differ mainly in size), and uncalibrated
  cameras destroy it.
- **Color.** Sensor spectral response, the color filter array, the ISP's white balance
  decisions, and the vendor's "vibrancy" tuning mean the same tomato is `#C1252A` on one device
  and `#D94434` on another. Produce identification is heavily color-dependent — ripeness,
  variety, and species all live in hue.
- **Noise and sharpness.** A high-ISO frame from a small sensor has texture statistics that
  masquerade as surface detail. Models read that as a different material.
- **Compute.** The same model quantized and run through three different NNAPI vendor drivers can
  produce embeddings that differ enough to change a nearest-neighbour result.

We fix all four by profiling the device once and then projecting every frame into a single
**canonical camera space** before inference. Downstream, exactly one camera exists.

## The `DeviceProfile`

Persisted as a Proto DataStore record, versioned, and rebuilt when the schema version, OS
version, or camera hardware changes.

```protobuf
message DeviceProfile {
  string profile_id = 1;             // UUID, regenerated on rebuild
  int32  schema_version = 2;
  string device_class_id = 3;        // hash(manufacturer, model, cameraId, osMajor)

  OpticalProfile   optical    = 10;  // per selected camera
  PhotometricProfile photometric = 11;
  NoiseProfile     noise      = 12;
  ComputeProfile   compute    = 13;
  SensorSuite      sensors    = 14;

  int64 built_at_epoch_ms = 20;
  Confidence overall_confidence = 21; // HIGH | MEDIUM | LOW (drives abstention margins)
  repeated string degradations = 22;  // what we could not measure and why
}
```

### 1. Optical profile — passive enumeration

Read from `CameraCharacteristics` at profile build time. Camera2 interop is required; CameraX
alone does not expose these.

| Characteristic | Use |
|---|---|
| `SENSOR_INFO_PHYSICAL_SIZE` | Sensor width/height in mm |
| `SENSOR_INFO_PIXEL_ARRAY_SIZE` | Native resolution → pixel pitch |
| `SENSOR_INFO_ACTIVE_ARRAY_SIZE` | Crop region reference frame |
| `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` | Focal length(s) in mm |
| `LENS_INTRINSIC_CALIBRATION` | `[f_x, f_y, c_x, c_y, s]` — **use directly when present** |
| `LENS_DISTORTION` | Brown–Conrady `[k1,k2,k3,p1,p2]` — radial + tangential |
| `LENS_POSE_ROTATION` / `LENS_POSE_TRANSLATION` | Multi-camera extrinsics |
| `LENS_INFO_MINIMUM_FOCUS_DISTANCE` | Macro capability, close-range validity |
| `SCALER_AVAILABLE_MAX_DIGITAL_ZOOM`, `CONTROL_ZOOM_RATIO_RANGE` | Effective focal at capture time |
| `INFO_SUPPORTED_HARDWARE_LEVEL` | LEGACY devices need the degraded path |
| `REQUEST_AVAILABLE_CAPABILITIES` | `RAW`, `DEPTH_OUTPUT`, `MANUAL_SENSOR`, `MOTION_TRACKING` |
| `LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION` | OIS presence → motion blur expectations |
| `SENSOR_INFO_COLOR_FILTER_ARRANGEMENT` | RGGB / BGGR / mono / **NIR** — quad-Bayer sensors need care |

**Derived intrinsics** when `LENS_INTRINSIC_CALIBRATION` is absent (common on mid-tier):

```
f_x_px = focal_length_mm × (pixel_array_width  / sensor_physical_width_mm)
f_y_px = focal_length_mm × (pixel_array_height / sensor_physical_height_mm)
c_x, c_y = active_array_center            // assume principal point at centre
```
Then scale to the analysis stream resolution. Mark `optical.source = DERIVED` and drop
`overall_confidence` one tier — a derived principal point is an assumption, and Stage 6 portion
estimation must know that.

**Camera selection.** Profile and use the *main* rear camera, not the default. Choose by:
largest `SENSOR_INFO_PHYSICAL_SIZE` among rear cameras whose focal length is within 0.8×–1.4× of
the "standard" equivalent, preferring one that reports `LENS_INTRINSIC_CALIBRATION`. On
multi-camera devices, pin the logical camera to that physical ID so the system does not silently
hand off to the ultra-wide at close focus — that handoff is a real and commonly missed source of
scale error.

### 2. Photometric profile — active + passive calibration

**Passive (always available):**

| Characteristic | Use |
|---|---|
| `SENSOR_COLOR_TRANSFORM1/2` | Sensor RGB → XYZ under reference illuminants |
| `SENSOR_CALIBRATION_TRANSFORM1/2` | Per-unit manufacturing variation |
| `SENSOR_FORWARD_MATRIX1/2` | White-balanced sensor → XYZ(D50) |
| `SENSOR_REFERENCE_ILLUMINANT1/2` | The illuminants those matrices apply to |
| `CONTROL_AWB_AVAILABLE_MODES` | Whether we can lock AWB |
| `TONEMAP_AVAILABLE_TONE_MAP_MODES` | Whether we can request a linear tone curve |

**Active (onboarding, 20 seconds, skippable):** the *white-sheet calibration*. Ask the user to
photograph any plain white or grey surface — printer paper, a wall, a tile — under the light they
usually shop in. From that frame:

- Estimate the scene illuminant (chromaticity of the neutral patch).
- Solve a 3×3 correction matrix mapping device output under that illuminant to sRGB-D65.
- Interpolate against the two reference illuminant matrices for intermediate color temperatures.
- Record vignetting: radial falloff of the neutral patch → per-pixel gain map.

**Fallback when the user skips:** statistical color constancy per frame — Shades-of-Grey
(Minkowski p=6) with a Grey-Edge cross-check, plus the passive matrices. Measurably worse than
the active path (expect ~2–4 points of top-1 on color-sensitive classes), so the onboarding
copy should sell the benefit honestly rather than nagging.

**Per-frame photometric normalization at inference:**
```
YUV → RGB (BT.601/709 per ImageFormat)
    → linearize (remove sRGB/device gamma; request LINEAR tonemap when supported)
    → apply per-device vignetting gain map
    → apply illuminant-interpolated CCM → XYZ
    → XYZ → sRGB-D65 canonical, chromatically adapted (Bradford)
    → re-encode to model input space
```

**Also lock the ISP where possible.** During a capture session, request `AWB_MODE_OFF` with
explicit gains derived from the profile, `NOISE_REDUCTION_MODE_MINIMAL`, `EDGE_MODE_OFF`, and
`TONEMAP_MODE_CONTRAST_CURVE` with a linear curve. Vendor "enhancement" is the enemy of
reproducibility. Where the device refuses (LEGACY level), record the refusal in `degradations`
and fall back to statistical correction.

### 3. Noise profile — measured, not assumed

Capture a 5-frame burst of a static scene at three ISO levels during onboarding (or
opportunistically during the first sessions):

- **Temporal noise σ per ISO**, per channel, from inter-frame differences on static regions.
  Fit `σ²(I) = a·I + b` (shot + read noise) and store `(a, b)` per ISO.
- **MTF50** from the sharpest available slanted edge in the calibration frame, or from a
  focus-sweep gradient if none is found. Gives an effective-resolution ceiling.
- **Rolling shutter skew estimate** from a gyro-correlated burst — used by Stage 0 to reject
  frames captured during fast pans.

Consumed by:
- The denoise strength in Stage 1 (over-denoising smooth textures destroys the exact cues that
  separate a smooth-skinned from a russeted variety).
- The **confidence widener**: predicted noise at the capture ISO inflates the fusion temperature,
  raising the abstention rate in bad light instead of producing confident garbage.

### 4. Compute profile — the delegate parity check

This is the step most implementations skip and it silently costs several points of accuracy.

For each candidate execution path — `NNAPI` (per accelerator where enumerable), `GPU`
(OpenCL/OpenGL), `XNNPACK` multi-threaded, `CPU` reference:

1. Run a fixed **golden input tensor set** (shipped with the model, 32 samples).
2. Compare embeddings against the CPU float reference:
   - **Reject** the delegate if mean cosine distance > `1e-3` or if any single sample exceeds
     `5e-3`. Quantization on some vendor NNAPI drivers is materially lossy, and a delegate that
     shifts embeddings breaks the retrieval index that was built against the reference.
   - **Reject** on any NaN/Inf.
3. Benchmark latency (p50/p95 over 20 runs after 5 warm-ups) and measure thermal headroom.
4. Select the fastest *parity-passing* path. Record all results, including rejections and why.

```protobuf
message ComputeProfile {
  string selected_delegate = 1;      // NNAPI:qti-dsp | GPU | XNNPACK | CPU
  int32  thread_count = 2;
  map<string, DelegateResult> evaluated = 3;   // full audit trail
  float  p95_latency_ms = 4;
  bool   supports_fp16 = 5;
  ThermalPolicy thermal = 6;         // when to downshift
}
```

**Thermal policy.** Register a `PowerManager.OnThermalStatusChangedListener`. At
`THERMAL_STATUS_MODERATE`, drop analysis frame rate; at `SEVERE`, fall back to the next-fastest
delegate and reduce input resolution to the smaller model variant; at `CRITICAL`, suspend
continuous analysis and switch to single-shot on tap. Never let sustained inference cook the
device — on budget hardware it will, and thermally throttled results are also *less accurate*.

### 5. Sensor suite

| Sensor | Use |
|---|---|
| Gyroscope | Motion gate in Stage 0; rolling-shutter rejection |
| Accelerometer | Device tilt → oblique-angle correction for portion estimation |
| Rotation vector | Gravity direction → assume the supporting surface is perpendicular |
| Ambient light | Cross-check on the illuminant estimate; flags mixed lighting |
| ARCore Depth support | Metric distance for Stage 6; hugely improves size estimation |
| Barometer | (Weak) altitude sanity check for geo-integrity |

## Canonical camera space

The target every frame is projected into:

| Parameter | Value | Why |
|---|---|---|
| Horizontal FOV | 55° | Close to a 40 mm-equivalent; inside almost every phone's native FOV so we crop rather than extrapolate |
| Resolution | 448 × 448 | Matches the embedding backbone's input |
| Angular resolution | 0.1228 °/px | Derived; constant across devices — **this is the invariant** |
| Color space | sRGB, D65, gamma 2.2 | Model training space |
| Bit depth | 8-bit per channel post-normalization | Pipeline works in fp16 internally |

**Projection procedure:**
1. Undistort using `LENS_DISTORTION` (skip if absent; log a degradation).
2. Build the homography from device intrinsics to canonical intrinsics.
3. Remap with bilinear sampling (bicubic when the compute profile affords it).
4. If the device's native FOV is narrower than 55° (tele-only capture), **do not upscale** —
   letterbox and flag `NARROW_FOV`; the fusion stage widens the posterior accordingly.

The payoff: object apparent size in canonical pixels is now a physically meaningful quantity
across the entire device fleet. A 6 cm tomato at 30 cm subtends the same number of pixels on a
budget device and a flagship, so the model can actually use size, and Stage 6 can convert
pixels to millimetres with one device-independent formula:

```
mm_per_px = (2 × distance_mm × tan(hFOV_canonical / 2)) / canonical_width_px
```

## Profile lifecycle

```
FIRST LAUNCH
  └─ passive enumeration (instant, no user involvement)
       └─ profile usable immediately at confidence=MEDIUM
            └─ optional guided calibration (20 s) → confidence=HIGH
                 └─ opportunistic refinement over the first ~20 captures
                      (vignetting, noise curve, illuminant range)

INVALIDATION TRIGGERS  → rebuild
  · profile schema version bump
  · OS major version change (ISP behaviour changes across upgrades — this is real)
  · camera hardware set change
  · model version change (compute parity must be re-verified against new goldens)
  · 180 days elapsed
```

## Server-side use of profiles

Anonymized profiles (no `profile_id`, only `device_class_id`) are uploaded to power:

1. **Per-device-class quality priors.** If a device class shows systematically lower agreement
   with consensus, its observations get a lower `device_reliability` weight in the trust score.
   This is a fairness hazard — it must not become "cheap phones don't count" — so the weight is
   floored at 0.6 and the class-level bias is instead used to *fix the normalization* via a
   correction delta pushed back to those devices. Tracked in [D-08](../tickets/01-phase-0-1-tickets.md).
2. **Fleet regression detection.** A new OS rollout that changes ISP behaviour shows up as a
   sudden agreement drop for one `device_class_id`. This is the early-warning system for
   silent accuracy regressions.
3. **Delegate blocklist.** A vendor NNAPI driver that fails parity on many devices gets
   blocklisted centrally, so devices skip the local test.

## Testing the calibration

You cannot validate this with unit tests alone. Ticket D-09 establishes:

- **A physical reference kit**: a colour target (X-Rite ColorChecker or the printable
  substitute), a ruler card, and a fixed-distance rig. Ten items photographed on every device in
  the test fleet.
- **A device fleet** of at least 8 devices spanning: flagship, mid-tier, budget, one LEGACY-level
  device, one with no `LENS_INTRINSIC_CALIBRATION`, one quad-Bayer, one with an aggressive ISP.
  Firebase Test Lab covers breadth; physical devices cover the photometric work Test Lab cannot.
- **The cross-device spread metric**: the same physical item photographed on all fleet devices
  must produce embeddings whose pairwise cosine distance is < 0.08 *after* normalization,
  versus a typical 0.2–0.35 before. This number is the acceptance criterion for Phase 1.

## What "degraded" looks like

The system must remain useful when calibration is impossible. Each degradation has a defined
consequence, and they compose:

| Degradation | Consequence |
|---|---|
| `NO_INTRINSIC_CALIBRATION` | Derived intrinsics; portion confidence capped at MEDIUM |
| `NO_DISTORTION_COEFFS` | Skip undistortion; widen posterior by 1 temperature step |
| `LEGACY_HARDWARE_LEVEL` | No ISP locking; rely on statistical color constancy; confidence capped MEDIUM |
| `SKIPPED_USER_CALIBRATION` | Statistical color constancy only; abstention threshold raised 5 pts |
| `NO_DEPTH` | Portion estimation requires a reference object or an explicit user unit |
| `NARROW_FOV` | Letterboxed canonical frame; scale prior disabled |
| `ALL_DELEGATES_FAILED_PARITY` | CPU fallback; latency budget relaxed to 1.2 s; warn on battery |

Three or more concurrent degradations drops `overall_confidence` to `LOW`, which forces the app
into explicit-confirmation mode: it will still label, but it never asserts a price without the
user first confirming the label.
