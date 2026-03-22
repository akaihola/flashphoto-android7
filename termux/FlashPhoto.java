// FAILED APPROACH: Run Camera2 flash photo via app_process on the device.
//
// FAILURE MODES:
// 1. ActivityThread.systemMain() creates a "system" process. Android's process
//    manager immediately kills it (SIGKILL) because Termux's UID (u0_a133) is
//    not authorized to run as a system process.
// 2. ActivityThread.attach(false) to register as a regular application also
//    fails – the ActivityManagerService doesn't recognize the process, and
//    Android kills it with SIGKILL or throws IllegalAccessException.
// 3. Even if the process survived, Camera2 API needs a proper application
//    Context (for CameraManager), which app_process can't provide for an
//    unregistered process.
// 4. Note: Termux's "am" command also uses app_process, but it only makes
//    simple Binder IPC calls to ActivityManagerService – it never creates an
//    ActivityThread or requests a Context. That's why am works but this doesn't.
//
// The working solution is the FlashPhoto APK (src/com/flashphoto/FlashReceiver.java)
// which runs as a proper installed Android app with its own UID and permissions.

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FlashPhoto {
    static File outFile;
    static int flashAeMode;
    static CountDownLatch latch = new CountDownLatch(1);
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: FlashPhoto <output.jpg> [camera_id]");
            System.exit(1);
        }
        
        final String outputPath = args[0];
        final String cameraId = args.length > 1 ? args[1] : "0";
        
        System.err.println("Taking photo with flash to: " + outputPath);
        
        // Create an application-level ActivityThread (NOT system)
        Looper.prepareMainLooper();
        
        Class<?> atClass = Class.forName("android.app.ActivityThread");
        Constructor<?> atConstructor = atClass.getDeclaredConstructor();
        atConstructor.setAccessible(true);
        Object at = atConstructor.newInstance();
        
        // Attach as application (false = not system)
        Method attachMethod = atClass.getDeclaredMethod("attach", boolean.class);
        attachMethod.setAccessible(true);
        attachMethod.invoke(at, false);
        
        Method getContextMethod = atClass.getMethod("getSystemContext");
        Context context = (Context) getContextMethod.invoke(at);
        
        System.err.println("Got context, package: " + context.getPackageName());
        
        final CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        
        CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
        
        Boolean flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        System.err.println("Flash available: " + flashAvailable);
        
        int[] aeModes = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
        flashAeMode = CameraMetadata.CONTROL_AE_MODE_ON;
        for (int mode : aeModes) {
            if (mode == CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH) {
                flashAeMode = mode;
                System.err.println("Using CONTROL_AE_MODE_ON_ALWAYS_FLASH");
            }
        }
        
        StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        Size largest = sizes[0];
        for (Size s : sizes) {
            if (s.getWidth() * s.getHeight() > largest.getWidth() * largest.getHeight()) {
                largest = s;
            }
        }
        System.err.println("Output size: " + largest.getWidth() + "x" + largest.getHeight());
        
        outFile = new File(outputPath);
        if (outFile.getParentFile() != null) outFile.getParentFile().mkdirs();
        
        final HandlerThread handlerThread = new HandlerThread("CameraThread");
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());
        
        final int w = largest.getWidth();
        final int h = largest.getHeight();
        
        manager.openCamera(cameraId, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(final CameraDevice camera) {
                try {
                    handleCameraOpened(camera, w, h, handler);
                } catch (Exception e) {
                    System.err.println("Error: " + e.getMessage());
                    e.printStackTrace(System.err);
                    camera.close();
                    latch.countDown();
                }
            }
            
            @Override
            public void onDisconnected(CameraDevice camera) {
                System.err.println("Camera disconnected");
                camera.close();
                latch.countDown();
            }
            
            @Override
            public void onError(CameraDevice camera, int error) {
                System.err.println("Camera error: " + error);
                camera.close();
                latch.countDown();
            }
        }, handler);
        
        boolean completed = latch.await(20, TimeUnit.SECONDS);
        if (!completed) {
            System.err.println("Timeout");
        }
        
        handlerThread.quitSafely();
        
        if (outFile.exists() && outFile.length() > 0) {
            System.out.println(outputPath);
            System.exit(0);
        } else {
            System.err.println("Failed to capture photo");
            System.exit(1);
        }
    }
    
    static void handleCameraOpened(final CameraDevice camera, int w, int h, final Handler handler) throws Exception {
        System.err.println("Camera opened!");
        
        final ImageReader reader = ImageReader.newInstance(w, h, ImageFormat.JPEG, 2);
        
        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader r) {
                Image image = r.acquireNextImage();
                try {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    FileOutputStream fos = new FileOutputStream(outFile);
                    fos.write(bytes);
                    fos.close();
                    System.err.println("Photo saved: " + bytes.length + " bytes");
                } catch (Exception e) {
                    System.err.println("Error saving: " + e.getMessage());
                } finally {
                    image.close();
                    reader.close();
                    camera.close();
                    latch.countDown();
                }
            }
        }, handler);
        
        final Surface readerSurface = reader.getSurface();
        SurfaceTexture tex = new SurfaceTexture(0);
        final Surface dummySurface = new Surface(tex);
        
        final List<Surface> surfaces = new ArrayList<Surface>();
        surfaces.add(readerSurface);
        surfaces.add(dummySurface);
        
        camera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(final CameraCaptureSession session) {
                try {
                    handleSessionConfigured(session, camera, dummySurface, readerSurface, handler);
                } catch (Exception e) {
                    System.err.println("Capture error: " + e.getMessage());
                    e.printStackTrace(System.err);
                    camera.close();
                    latch.countDown();
                }
            }
            
            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                System.err.println("Session configuration failed");
                camera.close();
                latch.countDown();
            }
        }, handler);
    }
    
    static void handleSessionConfigured(CameraCaptureSession session, CameraDevice camera, 
            Surface dummySurface, Surface readerSurface, Handler handler) throws Exception {
        // Preview phase
        CaptureRequest.Builder previewReq = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        previewReq.addTarget(dummySurface);
        previewReq.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        previewReq.set(CaptureRequest.CONTROL_AE_MODE, flashAeMode);
        
        session.setRepeatingRequest(previewReq.build(), null, handler);
        System.err.println("Preview started with flash AE mode " + flashAeMode);
        Thread.sleep(1500);
        session.stopRepeating();
        
        // Precapture trigger
        CaptureRequest.Builder precaptureReq = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        precaptureReq.addTarget(dummySurface);
        precaptureReq.set(CaptureRequest.CONTROL_AE_MODE, flashAeMode);
        precaptureReq.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, 
            CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        session.capture(precaptureReq.build(), null, handler);
        System.err.println("Precapture trigger sent");
        Thread.sleep(500);
        
        // Actual capture with flash
        CaptureRequest.Builder captureReq = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        captureReq.addTarget(readerSurface);
        captureReq.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        captureReq.set(CaptureRequest.CONTROL_AE_MODE, flashAeMode);
        captureReq.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE);
        
        System.err.println("Firing capture with flash...");
        session.capture(captureReq.build(), new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest req, TotalCaptureResult result) {
                Integer flashState = result.get(CaptureResult.FLASH_STATE);
                System.err.println("Capture complete! Flash state: " + flashState);
            }
        }, handler);
    }
}
