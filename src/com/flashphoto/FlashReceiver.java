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

public class FlashReceiver extends BroadcastReceiver {
    private static final String TAG = "FlashPhoto";

    @Override
    public void onReceive(Context context, Intent intent) {
        String filePath = intent.getStringExtra("file");
        String cameraId = intent.getStringExtra("camera");
        if (cameraId == null) cameraId = "0";
        
        if (filePath == null || filePath.isEmpty()) {
            Log.e(TAG, "No file. Use: am broadcast -a com.flashphoto.TAKE -e file /path/to/photo.jpg");
            return;
        }

        Log.i(TAG, "Taking torch photo -> " + filePath);
        File outFile = new File(filePath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        
        final PendingResult pendingResult = goAsync();
        takePicture(context, outFile, cameraId, pendingResult);
    }

    private void takePicture(Context context, File outputFile, String cameraId, PendingResult pendingResult) {
        HandlerThread ht = new HandlerThread("Cam");
        ht.start();
        Handler handler = new Handler(ht.getLooper());

        try {
            CameraManager mgr = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            
            mgr.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    try {
                        captureWithTorch(mgr, camera, cameraId, outputFile, handler, ht, pendingResult);
                    } catch (Exception e) {
                        Log.e(TAG, "onOpened error", e);
                        camera.close(); ht.quitSafely(); pendingResult.finish();
                    }
                }
                @Override public void onDisconnected(CameraDevice c) { c.close(); ht.quitSafely(); pendingResult.finish(); }
                @Override public void onError(CameraDevice c, int e) { Log.e(TAG, "Open err: "+e); c.close(); ht.quitSafely(); pendingResult.finish(); }
            }, handler);
        } catch (Exception e) {
            Log.e(TAG, "Open failed", e);
            ht.quitSafely(); pendingResult.finish();
        }
    }

    private void captureWithTorch(CameraManager mgr, CameraDevice camera, String cameraId,
                                   File outputFile, Handler handler, HandlerThread ht,
                                   PendingResult pendingResult) throws CameraAccessException {
        
        CameraCharacteristics chars = mgr.getCameraCharacteristics(cameraId);
        
        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        Size largest = sizes[0];
        for (Size s : sizes)
            if ((long)s.getWidth()*s.getHeight() > (long)largest.getWidth()*largest.getHeight())
                largest = s;
        Log.i(TAG, "Size: " + largest);
        
        // NO SurfaceTexture! Use only ImageReader.
        // This avoids the OpenGL context dependency that fails when screen is off.
        final ImageReader reader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 4);
        
        // Flag: only save the final capture, discard preview frames
        final AtomicBoolean doSave = new AtomicBoolean(false);
        
        reader.setOnImageAvailableListener(r -> {
            Image img = null;
            try {
                img = r.acquireLatestImage();
                if (img == null) return;
                
                if (!doSave.get()) {
                    // Preview frame - discard
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
                if (img != null) img.close();
                // Only close on actual capture (not preview frames)
                if (doSave.get()) {
                    reader.close();
                    camera.close();
                    ht.quitSafely();
                    pendingResult.finish();
                }
            }
        }, handler);
        
        Surface readerSurface = reader.getSurface();
        
        // Session with ONLY the ImageReader surface - no SurfaceTexture needed
        camera.createCaptureSession(Arrays.asList(readerSurface), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                try {
                    // Step 1: "Preview" frames to ImageReader with TORCH to stabilize AE
                    // These frames are discarded by the listener (doSave=false)
                    CaptureRequest.Builder preview = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    preview.addTarget(readerSurface);
                    preview.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    preview.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    preview.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    
                    session.setRepeatingRequest(preview.build(), null, handler);
                    Log.i(TAG, "Preview+TORCH started (AE converging)");
                    
                    // Wait for torch + AE to stabilize
                    Thread.sleep(2000);
                    session.stopRepeating();
                    
                    // Drain any pending preview frames
                    Thread.sleep(500);
                    Log.i(TAG, "Capturing...");
                    
                    // Step 2: Actual capture
                    doSave.set(true);
                    CaptureRequest.Builder cap = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    cap.addTarget(readerSurface);
                    cap.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    cap.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                    cap.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                    
                    Integer orient = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    if (orient != null) cap.set(CaptureRequest.JPEG_ORIENTATION, orient);
                    
                    session.capture(cap.build(), new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) {
                            Integer fs = result.get(CaptureResult.FLASH_STATE);
                            Log.i(TAG, "Capture done, FLASH_STATE=" + fs);
                        }
                    }, handler);
                    
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
