# Flash Photo Experiments – 2026-03-22

## Discovery: preview-frame save vs STILL_CAPTURE

The Huawei EMUI Camera2 HAL kills the torch LED when `TEMPLATE_STILL_CAPTURE`
fires. Saving a `TEMPLATE_PREVIEW` frame directly from a JPEG ImageReader
keeps the torch on throughout.

## Results

| Test     | Strategy      | Warmup | Skip | Brightness | Notes                      |
| -------- | ------------- | ------ | ---- | ---------- | -------------------------- |
| T1       | preview-frame | 2000ms | 5    | **16.2%**  |                            |
| T2       | preview-frame | 2000ms | 5    | **15.7%**  |                            |
| T3       | preview-frame | 2000ms | 5    | **15.6%**  |                            |
| T4       | preview-frame | 2000ms | 5    | **15.0%**  | wakelock re-acquired ~here |
| T5       | STILL_CAPTURE | 2000ms | –    | 9.6%       | `--ez still true`          |
| skip=0   | preview-frame | 2000ms | 0    | 9.8%       |                            |
| skip=2   | preview-frame | 2000ms | 2    | 9.5%       |                            |
| skip=10  | preview-frame | 2000ms | 10   | 11.4%      |                            |
| wu=500   | preview-frame | 500ms  | 5    | 6.3%       |                            |
| wu=1000  | preview-frame | 1000ms | 5    | 8.2%       |                            |
| wu=3000  | preview-frame | 3000ms | 5    | 11.7%      |                            |
| wu=5000  | preview-frame | 5000ms | 5    | 12.6%      |                            |
| pillow   | preview-frame | 2000ms | 5    | 9.8%       | end-to-end shell script    |

## Historical comparison

| Version | Strategy                           | Brightness | Date       |
| ------- | ---------------------------------- | ---------- | ---------- |
| v4      | SurfaceTexture + TORCH + ImageReader | 28.3%    | 2026-03-21 |
| v5      | ImageReader-only + TORCH + STILL   | 7.3%       | 2026-03-22 |
| v6      | ImageReader-only + TORCH + STILL   | 7.8%       | 2026-03-22 |
| cron    | v6 via cron job                    | 14.2%      | 2026-03-22 |
| old     | STILL_CAPTURE + stopRepeating      | 0.3%       | various    |

## Observations

- **Best defaults:** warmup=2000ms, skip=5 → 15–16% brightness (consistent)
- **Longer warmup helps** but with diminishing returns past 2000ms
- **skip=5 is optimal** – skip=0 is notably worse (AE not converged)
- **Wakelock matters:** brightness dropped from 15–16% to ~10% when tests
  ran without active wakelock (PowerGenie may have released it)
- **Power source hypothesis:** the previous dark photos (0.3%) may have been
  caused by the phone being plugged into a power socket. EMUI may throttle
  differently on AC vs battery. Needs testing.

## AC power test (phone plugged into charger)

| Test  | Brightness | Notes                              |
| ----- | ---------- | ---------------------------------- |
| AC T1 | 12.2%      |                                    |
| AC T2 | 12.9%      |                                    |
| AC T3 | 13.3%      |                                    |
| AC T4 | 12.9%      |                                    |

**Result:** 12–13% on AC, consistent. Slightly lower than battery (15–16%)
but fully usable. The previous 0.3% failures were caused by
`TEMPLATE_STILL_CAPTURE` killing the torch – not by AC power.

## Conclusion

The preview-frame save strategy is reliable on both battery and AC power.
The old `STILL_CAPTURE` approach was the root cause of intermittent dark
photos, not the power source.

## Production room: auto-exposure failure and manual exposure fix

In the dark utility room, all auto-exposure tests gave 0.8–1.6% brightness
despite the torch being confirmed ON (visible LED). The torch illuminates
objects 1–2m away, but AE sees a mostly dark scene and sets exposure too low.

### Manual exposure sweep in production room

| ISO | Exposure | Brightness | Notes |
| --- | -------- | ---------- | ----- |
| auto | auto | 0.8% | AE underexposes |
| 200 | 33ms | 7.5% | |
| 400 | 33ms | 10.6% | |
| 800 | 16ms | 10.2% | |
| 800 | 33ms | 16.9% | |
| 800 | 50ms | 18.4% | |
| 800 | 66ms | 19.2% | |
| **800** | **100ms** | **23.9%** | **← production default** |
| 1200 | 33ms | 18.8% | |
| 1600 | 33ms | 19.2% | diminishing returns |

### Production default: ISO 800 + 100ms

Gives 24% brightness – gauges, labels, pipes all clearly readable.
Longer exposure (200ms+) risks motion blur; higher ISO adds noise.

## TODO

- [ ] Verify wakelock impact with controlled on/off comparison
- [ ] Consider increasing ImageReader buffer or using YUV for faster preview
