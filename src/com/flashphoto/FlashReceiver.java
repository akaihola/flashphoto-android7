package com.flashphoto;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Takes a torch-illuminated photo via Camera2 API.
 *
 * Strategy: uses TEMPLATE_PREVIEW with FLASH_MODE_TORCH on a full-resolution
 * JPEG ImageReader. After warmup, waits for autofocus lock (or uses manual
 * focus distance), then saves a preview frame directly — avoiding the
 * TEMPLATE_STILL_CAPTURE which causes Huawei EMUI to kill the torch.
 *
 * Trigger:
 *   am broadcast -n com.flashphoto/.FlashReceiver -a com.flashphoto.TAKE \
 *     -e file /path/to/photo.jpg
 *
 * Optional extras:
 *   -e camera 0              camera ID (default: 0 = rear)
 *   --ei warmup 2000         torch warmup ms before AF lock (default: 2000)
 *   --ei skip 5              preview frames to skip (manual focus only, default: 5)
 *   --ei iso 0               manual ISO (0 = auto, try 400-1600)
 *   --ei exposure_ms 0       manual exposure ms (0 = auto, try 33-100)
 *   --ef focus_diopters -1   manual focus distance in diopters (default: -1 = autofocus)
 *                            Examples: 2.0 for ~50cm, 3.3 for ~30cm, 1.0 for ~1m, 0.0 for infinity
 *   --ei af_timeout 3000     AF lock timeout ms before saving anyway (default: 3000)
 */
public class FlashReceiver extends BroadcastReceiver {
    private static final String TAG = "FlashPhoto";

    @Override
    public void onReceive(Context context, Intent intent) {
        String filePath = intent.getStringExtra("file");
        String cameraId = intent.getStringExtra("camera");
        if (cameraId == null) cameraId = "0";

        int warmupMs = intent.getIntExtra("warmup", 2000);
        int skipFrames = intent.getIntExtra("skip", 5);
        int manualIso = intent.getIntExtra("iso", 0);
        int exposureMs = intent.getIntExtra("exposure_ms", 0);
        float focusDiopters = intent.getFloatExtra("focus_diopters", -1.0f);
        int afTimeoutMs = intent.getIntExtra("af_timeout", 3000);

        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "No file. Use: am broadcast -a com.flashphoto.TAKE -e file /path/to/photo.jpg");
            return;
        }

        boolean useManualFocus = focusDiopters >= 0;
        Log.i(TAG, "Config: file=" + filePath
            + " warmup=" + warmupMs + "ms"
            + " skip=" + skipFrames
            + " iso=" + (manualIso > 0 ? manualIso : "auto")
            + " exposure=" + (exposureMs > 0 ? exposureMs + "ms" : "auto")
            + " focus=" + (useManualFocus ? focusDiopters + " diopters" : "AF")
            + " af_timeout=" + afTimeoutMs + "ms");
        File outFile = new File(filePath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        final PendingResult pendingResult = goAsync();
        takePicture(context, outFile, cameraId, warmupMs, skipFrames,
                    manualIso, exposureMs, focusDiopters, afTimeoutMs,
                    pendingResult);
    }

    private void takePicture(Context context, File outputFile, String cameraId,
                              int warmupMs, int skipFrames,
                              int manualIso, int exposureMs,
                              float focusDiopters, int afTimeoutMs,
                              PendingResult pendingResult) {
        HandlerThread ht = new HandlerThread("Cam");
        ht.start();
        Handler handler = new Handler(ht.getLooper());

        try {
            CameraManager mgr = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

            mgr.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    try {
                        captureWithTorch(mgr, camera, cameraId, outputFile, handler, ht,
                                         warmupMs, skipFrames, manualIso, exposureMs,
                                         focusDiopters, afTimeoutMs,
                                         pendingResult);
                    } catch (Exception e) {
                        Log.e(TAG, "onOpened error", e);
                        camera.close(); ht.quitSafely(); pendingResult.finish();
                    }
                }
                @Override public void onDisconnected(CameraDevice c) {
                    Log.w(TAG, "Camera disconnected");
                    c.close(); ht.quitSafely(); pendingResult.finish();
                }
                @Override public void onError(CameraDevice c, int e) {
                    Log.e(TAG, "Open err: " + e);
                    c.close(); ht.quitSafely(); pendingResult.finish();
                }
            }, handler);
        } catch (Exception e) {
            Log.e(TAG, "Open failed", e);
            ht.quitSafely(); pendingResult.finish();
        }
    }

    private void captureWithTorch(CameraManager mgr, CameraDevice camera, String cameraId,
                                   File outputFile, Handler handler, HandlerThread ht,
                                   int warmupMs, int skipFrames,
                                   int manualIso, int exposureMs,
                                   float focusDiopters, int afTimeoutMs,
                                   PendingResult pendingResult) throws CameraAccessException {

        CameraCharacteristics chars = mgr.getCameraCharacteristics(cameraId);

        // Log sensor and lens ranges for tuning
        Range<Integer> isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        Range<Long> expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        Float minFocusDist = chars.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        int[] afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        Log.i(TAG, "ISO range: " + isoRange + " Exposure range: " + expRange);
        Log.i(TAG, "Min focus distance: " + minFocusDist + " diopters (closest focus)");
        StringBuilder afModesStr = new StringBuilder("AF modes: ");
        for (int m : afModes) afModesStr.append(afModeName(m)).append(" ");
        Log.i(TAG, afModesStr.toString());

        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        Size largest = sizes[0];
        for (Size s : sizes)
            if ((long)s.getWidth()*s.getHeight() > (long)largest.getWidth()*largest.getHeight())
                largest = s;
        Log.i(TAG, "Size: " + largest);

        final ImageReader reader = ImageReader.newInstance(
            largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 4);

        final boolean useManualFocus = focusDiopters >= 0;

        // Phase tracking:
        //   0 = warmup (torch on, AE/AF converging, discard all frames)
        //   1 = focusing:
        //       - Manual focus: skip N frames then advance to phase 2
        //       - AF lock: trigger AF, wait for lock or timeout, then advance to phase 2
        //   2 = save next frame
        final AtomicInteger phase = new AtomicInteger(0);
        final AtomicInteger skipsLeft = new AtomicInteger(skipFrames);
        final AtomicBoolean saved = new AtomicBoolean(false);
        final AtomicBoolean afTriggerSent = new AtomicBoolean(false);

        reader.setOnImageAvailableListener(r -> {
            Image img = null;
            try {
                img = r.acquireLatestImage();
                if (img == null) return;

                int p = phase.get();
                if (p == 0) {
                    img.close();
                    return;
                }

                if (p == 1) {
                    if (useManualFocus) {
                        // Manual focus: skip frames then advance
                        int left = skipsLeft.decrementAndGet();
                        img.close();
                        if (left <= 0) {
                            phase.set(2);
                            Log.i(TAG, "Manual focus: skip done, ready to save");
                        }
                    } else {
                        // AF lock mode: discard frames while waiting for lock
                        img.close();
                    }
                    return;
                }

                // p == 2: save this frame
                if (saved.getAndSet(true)) {
                    img.close();
                    return;
                }

                ByteBuffer buf = img.getPlanes()[0].getBuffer();
                byte[] data = new byte[buf.remaining()];
                buf.get(data);
                FileOutputStream fos = new FileOutputStream(outputFile);
                fos.write(data);
                fos.close();
                Log.i(TAG, "SAVED " + outputFile + " (" + data.length + " bytes)");
            } catch (Exception e) {
                Log.e(TAG, "Save err", e);
            } finally {
                if (saved.get()) {
                    reader.close();
                    camera.close();
                    ht.quitSafely();
                    pendingResult.finish();
                }
            }
        }, handler);

        Surface readerSurface = reader.getSurface();
        final boolean useManualExposure = manualIso > 0 || exposureMs > 0;
        final int finalIso = manualIso;
        final long finalExpNs = (long) exposureMs * 1_000_000L;

        camera.createCaptureSession(Arrays.asList(readerSurface),
            new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                try {
                    CaptureRequest.Builder preview = camera.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW);
                    preview.addTarget(readerSurface);

                    // Focus mode
                    if (useManualFocus) {
                        preview.set(CaptureRequest.CONTROL_AF_MODE,
                            CameraMetadata.CONTROL_AF_MODE_OFF);
                        preview.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDiopters);
                        Log.i(TAG, "Manual focus: " + focusDiopters + " diopters");
                    } else {
                        preview.set(CaptureRequest.CONTROL_AF_MODE,
                            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        Log.i(TAG, "Autofocus: CONTINUOUS_PICTURE (will lock after warmup)");
                    }

                    // Torch
                    preview.set(CaptureRequest.FLASH_MODE,
                        CameraMetadata.FLASH_MODE_TORCH);

                    // Exposure
                    if (useManualExposure) {
                        preview.set(CaptureRequest.CONTROL_AE_MODE,
                            CameraMetadata.CONTROL_AE_MODE_OFF);
                        if (finalIso > 0) {
                            preview.set(CaptureRequest.SENSOR_SENSITIVITY, finalIso);
                        }
                        if (finalExpNs > 0) {
                            preview.set(CaptureRequest.SENSOR_EXPOSURE_TIME, finalExpNs);
                        }
                        Log.i(TAG, "Manual exposure: ISO=" + finalIso
                            + " exp=" + exposureMs + "ms");
                    } else {
                        preview.set(CaptureRequest.CONTROL_AE_MODE,
                            CameraMetadata.CONTROL_AE_MODE_ON);
                    }

                    session.setRepeatingRequest(preview.build(),
                        new CameraCaptureSession.CaptureCallback() {
                            private int frameCount = 0;

                            @Override
                            public void onCaptureCompleted(CameraCaptureSession s,
                                CaptureRequest r, TotalCaptureResult result) {
                                frameCount++;

                                // Diagnostic logging for every frame
                                Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                                Float focusDist = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
                                Integer lensState = result.get(CaptureResult.LENS_STATE);
                                Long expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                                Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                                Integer fs = result.get(CaptureResult.FLASH_STATE);

                                int p = phase.get();
                                // Log every frame during warmup/focus phases,
                                // and the save frame (use Log.i – EMUI filters Log.d)
                                if (p < 2 || (p == 2 && !saved.get())) {
                                    Log.i(TAG, "Frame #" + frameCount
                                        + " phase=" + p
                                        + " AF=" + afStateName(afState)
                                        + " dist=" + focusDist
                                        + " lens=" + lensStateName(lensState)
                                        + " exp=" + (expNs != null ? expNs/1000000 + "ms" : "?")
                                        + " ISO=" + iso
                                        + " flash=" + fs);
                                }

                                // AF lock logic: trigger lock and wait for convergence
                                if (p == 1 && !useManualFocus && !saved.get()) {
                                    if (!afTriggerSent.getAndSet(true)) {
                                        // Send AF_TRIGGER_START as a single capture
                                        // to lock focus from CONTINUOUS_PICTURE mode
                                        try {
                                            CaptureRequest.Builder afLock =
                                                camera.createCaptureRequest(
                                                    CameraDevice.TEMPLATE_PREVIEW);
                                            afLock.addTarget(readerSurface);
                                            afLock.set(CaptureRequest.CONTROL_AF_MODE,
                                                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                            afLock.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                                CameraMetadata.CONTROL_AF_TRIGGER_START);
                                            afLock.set(CaptureRequest.FLASH_MODE,
                                                CameraMetadata.FLASH_MODE_TORCH);
                                            if (useManualExposure) {
                                                afLock.set(CaptureRequest.CONTROL_AE_MODE,
                                                    CameraMetadata.CONTROL_AE_MODE_OFF);
                                                if (finalIso > 0)
                                                    afLock.set(CaptureRequest.SENSOR_SENSITIVITY,
                                                        finalIso);
                                                if (finalExpNs > 0)
                                                    afLock.set(CaptureRequest.SENSOR_EXPOSURE_TIME,
                                                        finalExpNs);
                                            } else {
                                                afLock.set(CaptureRequest.CONTROL_AE_MODE,
                                                    CameraMetadata.CONTROL_AE_MODE_ON);
                                            }
                                            session.capture(afLock.build(), null, handler);
                                            Log.i(TAG, "AF_TRIGGER_START sent");
                                        } catch (CameraAccessException e) {
                                            Log.e(TAG, "AF trigger failed", e);
                                            // Fall through to save anyway
                                            phase.set(2);
                                        }
                                    }

                                    // Check AF state for convergence
                                    if (afState != null) {
                                        if (afState == CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED) {
                                            Log.i(TAG, "AF LOCKED at " + focusDist + " diopters");
                                            phase.set(2);
                                        } else if (afState == CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                            Log.w(TAG, "AF FAILED TO LOCK (NOT_FOCUSED_LOCKED)"
                                                + " dist=" + focusDist + " – saving anyway");
                                            phase.set(2);
                                        }
                                        // ACTIVE_SCAN, PASSIVE_SCAN: still working, keep waiting
                                    }
                                }

                                // Log details for the frame about to be saved
                                if (p == 2 && !saved.get()) {
                                    Log.i(TAG, ">>> Saving frame #" + frameCount
                                        + ": AF=" + afStateName(afState)
                                        + " dist=" + focusDist
                                        + " exp=" + (expNs != null ? expNs/1000000 + "ms" : "?")
                                        + " ISO=" + iso
                                        + " FLASH_STATE=" + fs);
                                }
                            }
                        }, handler);
                    Log.i(TAG, "Preview+TORCH started, warming up " + warmupMs + "ms");

                    // Timer: after warmup, enter focus phase
                    new Thread(() -> {
                        try {
                            Thread.sleep(warmupMs);
                            if (useManualFocus) {
                                Log.i(TAG, "Warmup done, skipping " + skipFrames
                                    + " frames (manual focus)");
                            } else {
                                Log.i(TAG, "Warmup done, triggering AF lock"
                                    + " (timeout=" + afTimeoutMs + "ms)");
                            }
                            phase.set(1);

                            // AF timeout: if AF hasn't locked within afTimeoutMs,
                            // force save anyway (don't lose the shot)
                            if (!useManualFocus) {
                                Thread.sleep(afTimeoutMs);
                                if (phase.get() == 1 && !saved.get()) {
                                    Log.w(TAG, "AF timeout after " + afTimeoutMs
                                        + "ms – saving best available frame");
                                    phase.set(2);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Timer error", e);
                        }
                    }).start();

                } catch (Exception e) {
                    Log.e(TAG, "Capture error", e);
                    camera.close(); ht.quitSafely(); pendingResult.finish();
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession s) {
                Log.e(TAG, "Config failed");
                camera.close(); ht.quitSafely(); pendingResult.finish();
            }
        }, handler);
    }

    /** Human-readable AF state name for diagnostics. */
    private static String afStateName(Integer state) {
        if (state == null) return "null";
        switch (state) {
            case CameraMetadata.CONTROL_AF_STATE_INACTIVE:          return "INACTIVE";
            case CameraMetadata.CONTROL_AF_STATE_PASSIVE_SCAN:      return "PASSIVE_SCAN";
            case CameraMetadata.CONTROL_AF_STATE_PASSIVE_FOCUSED:   return "PASSIVE_FOCUSED";
            case CameraMetadata.CONTROL_AF_STATE_ACTIVE_SCAN:       return "ACTIVE_SCAN";
            case CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED:    return "FOCUSED_LOCKED";
            case CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED: return "NOT_FOCUSED_LOCKED";
            case CameraMetadata.CONTROL_AF_STATE_PASSIVE_UNFOCUSED: return "PASSIVE_UNFOCUSED";
            default: return "UNKNOWN(" + state + ")";
        }
    }

    /** Human-readable lens state name for diagnostics. */
    private static String lensStateName(Integer state) {
        if (state == null) return "null";
        switch (state) {
            case CameraMetadata.LENS_STATE_STATIONARY: return "STATIONARY";
            case CameraMetadata.LENS_STATE_MOVING:     return "MOVING";
            default: return "UNKNOWN(" + state + ")";
        }
    }

    /** Human-readable AF mode name for capability logging. */
    private static String afModeName(int mode) {
        switch (mode) {
            case CameraMetadata.CONTROL_AF_MODE_OFF:                return "OFF";
            case CameraMetadata.CONTROL_AF_MODE_AUTO:               return "AUTO";
            case CameraMetadata.CONTROL_AF_MODE_MACRO:              return "MACRO";
            case CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO:   return "CONTINUOUS_VIDEO";
            case CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE: return "CONTINUOUS_PICTURE";
            case CameraMetadata.CONTROL_AF_MODE_EDOF:               return "EDOF";
            default: return "UNKNOWN(" + mode + ")";
        }
    }
}
