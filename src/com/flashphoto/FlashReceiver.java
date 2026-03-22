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
 *   --ez still false      use STILL_CAPTURE instead of preview save (default: false)
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
        boolean useStill = intent.getBooleanExtra("still", false);

        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "No file. Use: am broadcast -a com.flashphoto.TAKE -e file /path/to/photo.jpg");
            return;
        }

        Log.i(TAG, "Config: file=" + filePath
            + " warmup=" + warmupMs + "ms skip=" + skipFrames
            + " still=" + useStill);
        File outFile = new File(filePath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        
        final PendingResult pendingResult = goAsync();
        takePicture(context, outFile, cameraId, warmupMs, skipFrames, useStill, pendingResult);
    }

    private void takePicture(Context context, File outputFile, String cameraId,
                              int warmupMs, int skipFrames, boolean useStill,
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
                                         warmupMs, skipFrames, useStill, pendingResult);
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
                                   int warmupMs, int skipFrames, boolean useStill,
                                   PendingResult pendingResult) throws CameraAccessException {
        
        CameraCharacteristics chars = mgr.getCameraCharacteristics(cameraId);
        
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
                    // Warmup phase - discard
                    img.close();
                    return;
                }
                
                if (p == 1) {
                    // Skip phase - count down
                    int left = skipsLeft.decrementAndGet();
                    Log.d(TAG, "Skip frame, " + left + " remaining");
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
                if (img != null && !img.getTimestamp().equals(null)) {
                    // img already closed above in each branch
                }
                if (saved.get()) {
                    reader.close();
                    camera.close();
                    ht.quitSafely();
                    pendingResult.finish();
                }
            }
        }, handler);
        
        Surface readerSurface = reader.getSurface();
        
        camera.createCaptureSession(Arrays.asList(readerSurface),
            new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                try {
                    // Start repeating preview with TORCH on the JPEG ImageReader
                    CaptureRequest.Builder preview = camera.createCaptureRequest(
                        CameraDevice.TEMPLATE_PREVIEW);
                    preview.addTarget(readerSurface);
                    preview.set(CaptureRequest.CONTROL_AF_MODE,
                        CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    preview.set(CaptureRequest.CONTROL_AE_MODE,
                        CameraMetadata.CONTROL_AE_MODE_ON);
                    preview.set(CaptureRequest.FLASH_MODE,
                        CameraMetadata.FLASH_MODE_TORCH);
                    
                    session.setRepeatingRequest(preview.build(),
                        new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(CameraCaptureSession s,
                                CaptureRequest r, TotalCaptureResult result) {
                                if (phase.get() == 2 && !saved.get()) {
                                    Integer fs = result.get(CaptureResult.FLASH_STATE);
                                    Integer fm = result.get(CaptureResult.FLASH_MODE);
                                    Long expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                                    Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                                    Log.i(TAG, "Save frame: FLASH_STATE=" + fs
                                        + " FLASH_MODE=" + fm
                                        + " exp=" + (expNs != null ? expNs/1000000 + "ms" : "?")
                                        + " ISO=" + iso);
                                }
                            }
                        }, handler);
                    Log.i(TAG, "Preview+TORCH started, warming up " + warmupMs + "ms");
                    
                    // Warmup in background thread
                    new Thread(() -> {
                        try {
                            Thread.sleep(warmupMs);
                            Log.i(TAG, "Warmup done, skipping " + skipFrames + " frames");
                            phase.set(1);
                            
                            if (useStill) {
                                // Alternative: issue still capture while preview runs
                                Thread.sleep(500);
                                Log.i(TAG, "Issuing STILL_CAPTURE...");
                                phase.set(2);
                                CaptureRequest.Builder cap = camera.createCaptureRequest(
                                    CameraDevice.TEMPLATE_STILL_CAPTURE);
                                cap.addTarget(readerSurface);
                                cap.set(CaptureRequest.CONTROL_AF_MODE,
                                    CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                cap.set(CaptureRequest.CONTROL_AE_MODE,
                                    CameraMetadata.CONTROL_AE_MODE_ON);
                                cap.set(CaptureRequest.FLASH_MODE,
                                    CameraMetadata.FLASH_MODE_TORCH);
                                Integer orient = chars.get(
                                    CameraCharacteristics.SENSOR_ORIENTATION);
                                if (orient != null)
                                    cap.set(CaptureRequest.JPEG_ORIENTATION, orient);
                                session.capture(cap.build(), null, handler);
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
}
