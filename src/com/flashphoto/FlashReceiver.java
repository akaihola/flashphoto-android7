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
 * JPEG ImageReader. After warmup, saves a preview frame directly — avoiding
 * the TEMPLATE_STILL_CAPTURE which causes Huawei EMUI to kill the torch.
 *
 * Trigger:
 *   am broadcast -n com.flashphoto/.FlashReceiver -a com.flashphoto.TAKE \
 *     -e file /path/to/photo.jpg
 *
 * Optional extras:
 *   -e camera 0           camera ID (default: 0 = rear)
 *   --ei warmup 2000      torch warmup ms before saving (default: 2000)
 *   --ei skip 5           preview frames to skip after warmup (default: 5)
 *   --ei iso 0            manual ISO (0 = auto, try 400-1600)
 *   --ei exposure_ms 0    manual exposure ms (0 = auto, try 33-100)
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

        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "No file. Use: am broadcast -a com.flashphoto.TAKE -e file /path/to/photo.jpg");
            return;
        }

        Log.i(TAG, "Config: file=" + filePath
            + " warmup=" + warmupMs + "ms skip=" + skipFrames
            + " iso=" + (manualIso > 0 ? manualIso : "auto")
            + " exposure=" + (exposureMs > 0 ? exposureMs + "ms" : "auto"));
        File outFile = new File(filePath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        final PendingResult pendingResult = goAsync();
        takePicture(context, outFile, cameraId, warmupMs, skipFrames,
                    manualIso, exposureMs, pendingResult);
    }

    private void takePicture(Context context, File outputFile, String cameraId,
                              int warmupMs, int skipFrames,
                              int manualIso, int exposureMs,
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
                                   PendingResult pendingResult) throws CameraAccessException {

        CameraCharacteristics chars = mgr.getCameraCharacteristics(cameraId);

        // Log sensor ranges for tuning
        Range<Integer> isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        Range<Long> expRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        Log.i(TAG, "ISO range: " + isoRange + " Exposure range: " + expRange);

        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        Size largest = sizes[0];
        for (Size s : sizes)
            if ((long)s.getWidth()*s.getHeight() > (long)largest.getWidth()*largest.getHeight())
                largest = s;
        Log.i(TAG, "Size: " + largest);

        final ImageReader reader = ImageReader.newInstance(
            largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 4);

        // Phase tracking: 0=warmup (discard all), 1=skipping (count down), 2=save next
        final AtomicInteger phase = new AtomicInteger(0);
        final AtomicInteger skipsLeft = new AtomicInteger(skipFrames);
        final AtomicBoolean saved = new AtomicBoolean(false);

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
                    int left = skipsLeft.decrementAndGet();
                    img.close();
                    if (left <= 0) {
                        phase.set(2);
                        Log.i(TAG, "Ready to save next frame");
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
        final boolean useManual = manualIso > 0 || exposureMs > 0;
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
                    preview.set(CaptureRequest.CONTROL_AF_MODE,
                        CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    preview.set(CaptureRequest.FLASH_MODE,
                        CameraMetadata.FLASH_MODE_TORCH);

                    if (useManual) {
                        // Manual exposure: disable AE entirely
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
                            @Override
                            public void onCaptureCompleted(CameraCaptureSession s,
                                CaptureRequest r, TotalCaptureResult result) {
                                if (phase.get() == 2 && !saved.get()) {
                                    Long expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                                    Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                                    Integer fs = result.get(CaptureResult.FLASH_STATE);
                                    Log.i(TAG, "Save frame: exp="
                                        + (expNs != null ? expNs/1000000 + "ms" : "?")
                                        + " ISO=" + iso
                                        + " FLASH_STATE=" + fs);
                                }
                            }
                        }, handler);
                    Log.i(TAG, "Preview+TORCH started, warming up " + warmupMs + "ms");

                    new Thread(() -> {
                        try {
                            Thread.sleep(warmupMs);
                            Log.i(TAG, "Warmup done, skipping " + skipFrames + " frames");
                            phase.set(1);
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
}
