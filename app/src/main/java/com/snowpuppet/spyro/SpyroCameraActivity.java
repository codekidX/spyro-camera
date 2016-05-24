package com.snowpuppet.spyro;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;

import com.snowpuppet.spyro.helpers.CompareSize;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SpyroCameraActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1515;
    TextureView spyroTextureView;
    ImageButton imageCaptureButton;

    ImageReader mImageReader;

    File imageFile;
    File mSpyroFolder;

    private String mSpyroCameraId;
    private Size mPreviewSize;

    private CaptureRequest.Builder captureRequest;

    private CameraCaptureSession mCameraCaptureSession;

    private CameraCharacteristics mCameraCharacteristics;

    private CameraManager mCamManager;

    TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            spyroCameraSetup(i, i1);
            connectSpyroCamera(i,i1);

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            configureTransform(i,i1);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private CameraDevice mSpyroCamera;

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mSpyroCamera = cameraDevice;

            startSpyroPreview();

        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
            mSpyroCamera = null;

        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            mSpyroCamera = null;

        }
    };

    private ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            mSpyroHandler.post(new ImageSaver(imageReader.acquireNextImage(), imageFile));
        }
    };

    private HandlerThread mSpyroHandlerThread;
    private Handler mSpyroHandler;

    private static SparseIntArray orientationArray = new SparseIntArray();

    static {
        orientationArray.append(Surface.ROTATION_0, 0);
        orientationArray.append(Surface.ROTATION_90, 90);
        orientationArray.append(Surface.ROTATION_180, 180);
        orientationArray.append(Surface.ROTATION_270, 270);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spyro_camera);

        spyroTextureView = (TextureView) findViewById(R.id.spyro_texture_view);

        imageCaptureButton = (ImageButton) findViewById(R.id.camera_shutter);

        imageCaptureButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                try {
                    final CaptureRequest.Builder captureRequest =
                            mSpyroCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

                    captureRequest.addTarget(mImageReader.getSurface());

                    // Use the same AE and AF modes as the preview.
                    captureRequest.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);


                    // Orientation
                    int rotation = getWindowManager().getDefaultDisplay().getRotation();
                    captureRequest.set(CaptureRequest.JPEG_ORIENTATION, spyroRotationSensor(mCameraCharacteristics,rotation));

                    CameraCaptureSession.CaptureCallback CaptureCallback
                            = new CameraCaptureSession.CaptureCallback() {

                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                       @NonNull CaptureRequest request,
                                                       @NonNull TotalCaptureResult result) {
                            File mFile = imageFile;
                            Log.d("Spyro", mFile.toString());
                        }
                    };

                    mCameraCaptureSession.stopRepeating();
                    mCameraCaptureSession.capture(captureRequest.build(), CaptureCallback, null);

                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        startSpyroThread();

        if (spyroTextureView.isAvailable()) {
            spyroCameraSetup(spyroTextureView.getWidth(), spyroTextureView.getHeight());
        } else {
            spyroTextureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        closeSpyroCamera();
        stopSpyroThread();

        super.onPause();
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        View windowDecor = getWindow().getDecorView();
        if (hasFocus) {
            windowDecor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                try {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        mCamManager.openCamera(mSpyroCameraId, stateCallback, mSpyroHandler);
                    }

                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /*
     * App methods
     */


    private void spyroCameraSetup(int width, int height) {
         mCamManager= (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
            for (String camId :
                    mCamManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = mCamManager.getCameraCharacteristics(camId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    break;
                }

                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                int totalRotation = spyroRotationSensor(cameraCharacteristics, deviceOrientation);
                boolean swapRotation = totalRotation == 90 || totalRotation == 270;
                int rotatedWidth = width;
                int rotatedHeight = height;
                if (swapRotation) {
                    rotatedWidth = height;
                    rotatedHeight = width;
                }
                mPreviewSize = chooseOptimalPreviewSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                mSpyroCameraId = camId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void connectSpyroCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        configureTransform(width,height);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(mSpyroCameraId, stateCallback, mSpyroHandler);
                } else {

                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);

                }
            } else {
                cameraManager.openCamera(mSpyroCameraId, stateCallback, mSpyroHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startSpyroPreview() {
        SurfaceTexture s = spyroTextureView.getSurfaceTexture();
        s.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

        Surface surface = new Surface(s);

        try {
            captureRequest = mSpyroCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            captureRequest.addTarget(surface);

            mSpyroCamera.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    mCameraCaptureSession = cameraCaptureSession;
                    try {
                        cameraCaptureSession.setRepeatingRequest(captureRequest.build(), null, mSpyroHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {

                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeSpyroCamera() {
        if (mSpyroCamera != null) {
            mSpyroCamera.close();
            mSpyroCamera = null;
        }

    }

    private void startSpyroThread() {
        mSpyroHandlerThread = new HandlerThread("SpyroBgThread");
        mSpyroHandlerThread.start();
        mSpyroHandler = new Handler(mSpyroHandlerThread.getLooper());
    }

    private void stopSpyroThread() {
        mSpyroHandlerThread.quitSafely();

        try {
            mSpyroHandlerThread.join();
            mSpyroHandlerThread = null;
            mSpyroHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static int spyroRotationSensor(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        deviceOrientation = orientationArray.get(deviceOrientation);

        return (deviceOrientation + sensorOrientation + 270) % 360;
    }

    private static Size chooseOptimalPreviewSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<Size>();

        for (Size options : choices) {
            if (options.getHeight() == options.getWidth() * height / width &&
                    options.getWidth() >= width && options.getHeight() >= height) {
                bigEnough.add(options);
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSize());
        } else {
            return choices[0];
        }
    }


    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `spyroTextureView` is fixed.
     *
     * @param viewWidth  The width of `spyroTextureView`
     * @param viewHeight The height of `spyroTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = SpyroCameraActivity.this;
        if (null == spyroTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        spyroTextureView.setTransform(matrix);
    }

    private class ImageSaver implements Runnable {

        Image mImage;
        File mFile;

        public ImageSaver(Image image, File file) {
            this.mImage = image;
            this.mFile = file;
        }

        @Override
        public void run() {

            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
