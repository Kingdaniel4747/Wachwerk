package de.danberg.wachwerk;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class QrScannerActivity extends Activity implements TextureView.SurfaceTextureListener {
    private UiPalette palette;
    private static final int CAMERA_PERMISSION = 6201;
    private TextureView texture;
    private TextView status;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader imageReader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private String expected;
    private final AtomicBoolean decoding = new AtomicBoolean(false);
    private final MultiFormatReader decoder = new MultiFormatReader();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = UiPalette.from(this);
        expected = getIntent().getStringExtra("expectedToken");
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        decoder.setHints(hints);
        buildUi();
        cameraThread = new HandlerThread("WachwerkCamera"); cameraThread.start(); cameraHandler = new Handler(cameraThread.getLooper());
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        else if (texture.isAvailable()) openCamera();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        texture = new TextureView(this); texture.setSurfaceTextureListener(this);
        root.addView(texture, new FrameLayout.LayoutParams(-1, -1));
        ViewGroup scanFrame = new FrameLayout(this);
        GradientDrawable border = new GradientDrawable(); border.setColor(Color.TRANSPARENT); border.setCornerRadius(dp(24)); border.setStroke(dp(3), palette.map(Color.rgb(155,245,177))); scanFrame.setBackground(border);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(dp(270), dp(270), Gravity.CENTER); root.addView(scanFrame, frameParams);
        TextView title = label("QR-Code scannen", 24, Color.WHITE); title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP); titleParams.setMargins(dp(20), dp(58), dp(20), 0); root.addView(title, titleParams);
        status = label("Halte deinen gedruckten Wachwerk-Code in den Rahmen.", 14, palette.map(Color.rgb(210,225,240))); status.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM); statusParams.setMargins(dp(28), 0, dp(28), dp(55)); root.addView(status, statusParams);
        setContentView(root);
    }

    @SuppressWarnings("MissingPermission")
    private void openCamera() {
        if (camera != null) return;
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String chosen = null;
            for (String id : manager.getCameraIdList()) {
                Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { chosen = id; break; }
            }
            if (chosen == null && manager.getCameraIdList().length > 0) chosen = manager.getCameraIdList()[0];
            if (chosen == null) { status.setText("Keine Kamera auf diesem Gerät gefunden."); return; }
            manager.openCamera(chosen, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) { camera = device; createSession(); }
                @Override public void onDisconnected(CameraDevice device) { device.close(); camera = null; }
                @Override public void onError(CameraDevice device, int error) { device.close(); camera = null; runOnUiThread(() -> status.setText("Kamera konnte nicht geöffnet werden.")); }
            }, cameraHandler);
        } catch (Exception error) { status.setText("Kamera konnte nicht gestartet werden."); }
    }

    private void createSession() {
        try {
            SurfaceTexture surfaceTexture = texture.getSurfaceTexture();
            if (surfaceTexture == null || camera == null) return;
            surfaceTexture.setDefaultBufferSize(1280, 720);
            Surface preview = new Surface(surfaceTexture);
            imageReader = ImageReader.newInstance(1280, 720, android.graphics.ImageFormat.YUV_420_888, 2);
            imageReader.setOnImageAvailableListener(this::decodeFrame, cameraHandler);
            CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(preview); request.addTarget(imageReader.getSurface());
            request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            camera.createCaptureSession(Arrays.asList(preview, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession captureSession) {
                    session = captureSession;
                    try { session.setRepeatingRequest(request.build(), null, cameraHandler); }
                    catch (CameraAccessException ignored) {}
                }
                @Override public void onConfigureFailed(CameraCaptureSession captureSession) { runOnUiThread(() -> status.setText("Kameravorschau nicht verfügbar.")); }
            }, cameraHandler);
        } catch (Exception error) { runOnUiThread(() -> status.setText("Kameravorschau nicht verfügbar.")); }
    }

    private void decodeFrame(ImageReader reader) {
        Image image = reader.acquireLatestImage();
        if (image == null) return;
        if (!decoding.compareAndSet(false, true)) { image.close(); return; }
        try {
            int width = image.getWidth(), height = image.getHeight();
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int rowStride = plane.getRowStride(), pixelStride = plane.getPixelStride();
            byte[] y = new byte[width * height];
            if (pixelStride == 1 && rowStride == width) buffer.get(y);
            else {
                byte[] row = new byte[rowStride];
                for (int r = 0; r < height; r++) {
                    int length = Math.min(rowStride, buffer.remaining()); buffer.get(row, 0, length);
                    for (int c = 0; c < width; c++) y[r * width + c] = row[Math.min(c * pixelStride, length - 1)];
                }
            }
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(y, width, height, 0, 0, width, height, false)));
            Result result = decoder.decodeWithState(bitmap);
            if (result != null) {
                if (expected == null || expected.equals(result.getText())) runOnUiThread(() -> { setResult(RESULT_OK); finish(); });
                else runOnUiThread(() -> status.setText("Das ist ein anderer QR-Code. Suche deinen Wachwerk-Code."));
            }
        } catch (Exception ignored) {
            // Most camera frames do not contain a complete QR code.
        } finally {
            decoder.reset(); decoding.set(false); image.close();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) openCamera();
        else {
            status.setText("Ohne Kamerazugriff kann der QR-Wecker nicht beendet werden."); setResult(RESULT_CANCELED);
            new AlertDialog.Builder(this).setTitle("Kamera erlauben")
                .setMessage("Öffne die Android-App-Einstellungen und aktiviere dort die Kamera für Wachwerk.")
                .setPositiveButton("App-Einstellungen", (dialog, which) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Zurück", (dialog, which) -> finish()).show();
        }
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) { if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    @Override protected void onDestroy() { closeCamera(); if (cameraThread != null) cameraThread.quitSafely(); super.onDestroy(); }
    private void closeCamera() { try { if (session != null) session.close(); } catch (Exception ignored) {} try { if (camera != null) camera.close(); } catch (Exception ignored) {} try { if (imageReader != null) imageReader.close(); } catch (Exception ignored) {} session = null; camera = null; imageReader = null; }
    private TextView label(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
