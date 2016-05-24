package com.snowpuppet.spyro;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.app.AppCompatActivity;
import android.view.TextureView;
import android.view.View;

public class SpyroCameraActivity extends AppCompatActivity {

    TextureView spyroTextureView;

    private String mSpyroCameraId;

    TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            spyroCameraSetup(i,i1);

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

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

    private HandlerThread mSpyroHandlerThread;
    private Handler mSpyroHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spyro_camera);

        spyroTextureView = (TextureView) findViewById(R.id.spyro_texture_view);
    }

    @Override
    protected void onResume() {
        super.onResume();

        startSpyroThread();

        if(spyroTextureView.isAvailable()) {
            spyroCameraSetup(spyroTextureView.getWidth(),spyroTextureView.getHeight());
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
        if(hasFocus) {
            windowDecor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }


    /*
     * App methods
     */


    private void spyroCameraSetup(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
            for (String camId:
                 cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(camId);
                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    break;
                }
                mSpyroCameraId = camId;
            }
        }catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeSpyroCamera() {
        if(mSpyroCamera !=null) {
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
}
