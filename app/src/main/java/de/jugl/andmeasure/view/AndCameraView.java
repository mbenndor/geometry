package de.jugl.andmeasure.view;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import org.opencv.android.JavaCameraView;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.List;

/**
 * <p>This class is basically a reimplementation of {@link JavaCameraView} while
 * also leveraging some of its functionality. The main reason as to why a custom
 * camera view class is needed is because of the inability to tweak camera
 * parameters in the view classes OpenCV provides.</p>
 *
 * <p>This class takes most of its code from the original {@link JavaCameraView} because of the
 * weird use of access modifiers for class fields (some are private, some are protected,
 * where's the pattern?). So to make this view work, I had to copy most of the code from
 * the base class.</p>
 */
public class AndCameraView extends JavaCameraView implements Camera.PreviewCallback {

    /**
     * Debugging tag.
     */
    private static final String TAG = "AndCameraView";

    /**
     * ID of the OpenGL texture to be used with {@link SurfaceTexture}. No clue where this
     * comes from but otherwise it wouldn't be magic.
     */
    private static final int MAGIC_TEXTURE_ID = 10;

    /**
     * Byte buffer for the camera to store image data in.
     */
    private byte[] mBuffer;

    /**
     * Frame buffer for OpenCV.
     */
    private Mat[] mFrameBuffer;

    /**
     * Camera frame buffer for OpenCV.
     */
    private AndCameraFrame mCameraFrames[];

    /**
     * <code>true</code> if a new camera frame is ready to be evaluated, <code>false</code> otherwise.
     */
    private boolean mCameraFrameReady;

    /**
     * {@link SurfaceTexture} for the camera to draw on.
     */
    private SurfaceTexture mSurfaceTexture;

    /**
     * <code>true</code> if the camera worker thread needs to be stopped, <code>false</code> otherwise.
     */
    private boolean mStopThread;

    /**
     * Camera worker thread.
     */
    private Thread mCameraThread;

    /**
     * Current index in the frame buffer. Flicks between 0 and 1.
     */
    private int mChainIdx = 0;

    public AndCameraView(Context context, int cameraId) {
        super(context, cameraId);
    }

    public AndCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @return {@link Camera} associated with this view
     */
    public Camera getCamera() {
        return this.mCamera;
    }

    @Override
    protected boolean initializeCamera(int width, int height) {
        Log.d(TAG, "Using custom camera view initialization");

        synchronized (this) {
            // We will ignore the camera index because we're only interested
            // in the back camera.
            this.mCamera = null;

            try {
                // Camera.open() opens the first back-facing camera.
                this.mCamera = Camera.open();
            } catch (Exception e) {
                Log.e(TAG, "Couldn't open camera.", e);
                return false;
            }

            try {
                // Get the best preview size available.
                Camera.Parameters params = this.mCamera.getParameters();
                List<Camera.Size> sizes = params.getSupportedPreviewSizes();

                if (sizes == null) {
                    Log.e(TAG, "Camera preview not supported.");
                    return false;
                }

                Size frameSize = this.calculateCameraFrameSize(sizes, new JavaCameraSizeAccessor(), width, height);
                Log.d(TAG, String.format("Preview size: %.0fx%.0f", frameSize.width, frameSize.height));

                // OpenCV distinguishes between phones and emulators here. We know
                // we're running on a phone so we can safely set NV21.
                params.setPreviewFormat(ImageFormat.NV21);
                params.setPreviewSize((int) frameSize.width, (int) frameSize.height);
                params.setRecordingHint(true);

                // Set focus mode to infinity, if available.
                List<String> focusModes = params.getSupportedFocusModes();

                if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
                }

                this.mCamera.setParameters(params);

                // Propagate preview size to CameraBridgeViewBase.
                this.mFrameWidth = params.getPreviewSize().width;
                this.mFrameHeight = params.getPreviewSize().height;

                // Propagate scaling factor to CameraBridgeViewBase.
                if (this.getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT &&
                    this.getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT) {

                    this.mScale = Math.min((float) height / this.mFrameHeight, (float) width / this.mFrameWidth);
                } else {
                    this.mScale = 0;
                }

                // If there's a FPS meter associated with this view, let it know our dimensions.
                if (this.mFpsMeter != null) {
                    this.mFpsMeter.setResolution(this.mFrameWidth, this.mFrameHeight);
                }

                // Buffer size = frame width * frame height * frame bit depth.
                int size = this.mFrameWidth * this.mFrameHeight * ImageFormat.getBitsPerPixel(params.getPreviewFormat());
                // Allocate new frame buffer.
                this.mBuffer = new byte[size];

                // Make the camera recognize our frame buffer.
                this.mCamera.addCallbackBuffer(this.mBuffer);
                this.mCamera.setPreviewCallbackWithBuffer(this);

                // Frame data consists of grayscale and YUV data.
                int matRows = this.mFrameHeight + (this.mFrameHeight / 2);
                int matCols = this.mFrameWidth;

                // Okay so the frame buffer in the super class is private but the camera
                // frames are protected??? So I can't reuse JavaCameraView functions??????
                this.mFrameBuffer = new Mat[2];
                this.mFrameBuffer[0] = new Mat(matRows, matCols, CvType.CV_8UC1);
                this.mFrameBuffer[1] = new Mat(matRows, matCols, CvType.CV_8UC1);

                this.AllocateCache();

                // Frames keep the mats they consist of as references.
                this.mCameraFrames = new AndCameraFrame[2];
                this.mCameraFrames[0] = new AndCameraFrame(this.mFrameBuffer[0], this.mFrameWidth, this.mFrameHeight);
                this.mCameraFrames[1] = new AndCameraFrame(this.mFrameBuffer[1], this.mFrameWidth, this.mFrameHeight);

                // Assign the drawing surface to the camera.
                this.mSurfaceTexture = new SurfaceTexture(MAGIC_TEXTURE_ID);
                this.mCamera.setPreviewTexture(this.mSurfaceTexture);

                Log.d(TAG, "Start preview");
                this.mCamera.startPreview();

                return true;
            } catch (Exception e) {
                Log.e(TAG, "Couldn't set camera params.", e);
            }
        }

        return false;
    }

    @Override
    public void onPreviewFrame(byte[] frame, Camera arg1) {
        synchronized (this) {
            // Let the camera worker thread know that we have a new frame to process.
            this.mFrameBuffer[this.mChainIdx].put(0, 0, frame);
            this.mCameraFrameReady = true;

            this.notify();
        }

        // Request next frame.
        if (this.mCamera != null) {
            this.mCamera.addCallbackBuffer(this.mBuffer);
        }
    }

    @Override
    protected boolean connectCamera(int width, int height) {
        if (!this.initializeCamera(width, height)) {
            return false;
        }

        this.mCameraFrameReady = false;
        this.mStopThread = false;

        // Create and start camera worker thread.
        this.mCameraThread = new Thread(new CameraWorker());
        this.mCameraThread.start();

        return true;
    }

    @Override
    protected void disconnectCamera() {
        try {
            // Stop the camera thread.
            this.mStopThread = true;

            synchronized (this) {
                this.notify();
            }

            // Wait for worker thread to finish.
            if (this.mCameraThread != null) {
                this.mCameraThread.join();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted during camera disconnect.", e);
        } finally {
            this.mCameraThread = null;
        }

        this.releaseCamera();
        this.mCameraFrameReady = false;
    }

    @Override
    protected void releaseCamera() {
        // Call to super makes the camera preview stop. Also it may or
        // may not have already allocated some resources which it can free here.
        super.releaseCamera();

        synchronized (this) {
            // Deallocate frame buffer.
            if (this.mFrameBuffer != null) {
                this.mFrameBuffer[0].release();
                this.mFrameBuffer[1].release();
            }

            // Deallocate frames.
            if (this.mCameraFrames != null) {
                this.mCameraFrames[0].release();
                this.mCameraFrames[1].release();
            }
        }
    }

    private static class AndCameraFrame implements CvCameraViewFrame {

        /**
         * Dimensions of the frame.
         */
        private int mWidth, mHeight;

        /**
         * YUV and RGBA matrices.
         */
        private Mat mYuv, mRgba;

        @Override
        public Mat rgba() {
            // No distinction to be made here because we only use NV21.
            Imgproc.cvtColor(mYuv, mRgba, Imgproc.COLOR_YUV2RGB_NV21, 4);

            return this.mRgba;
        }

        @Override
        public Mat gray() {
            return this.mYuv.submat(0, mHeight, 0, mWidth);
        }

        public AndCameraFrame(Mat yuv, int width, int height) {
            super();

            this.mWidth = width;
            this.mHeight = height;
            this.mYuv = yuv;
            this.mRgba = new Mat();
        }

        public void release() {
            // We only release the RGBA matrix because the camera will actually keep
            // writing to the grayscale matrix. So if we deallocate it, we would be
            // in trouble.
            this.mRgba.release();
        }

    }

    private class CameraWorker implements Runnable {

        @Override
        public void run() {
            Log.d(TAG, "Starting camera worker");

            do {
                boolean hasFrame = false;

                synchronized (AndCameraView.this) {
                    try {
                        // Wait for a new frame to arrive.
                        while (!mCameraFrameReady && !mStopThread) {
                            AndCameraView.this.wait();
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Interruption in worker thread.", e);
                    }

                    // If we have a new frame, prepare to let the main thread write to the next frame buffer.
                    if (mCameraFrameReady) {
                        mChainIdx = 1 - mChainIdx; // This switches between 0 and 1. Neat.
                        mCameraFrameReady = false;
                        hasFrame = true;
                    }
                }

                // Draw the frame.
                if (!mStopThread && hasFrame) {
                    if (!mFrameBuffer[1 - mChainIdx].empty()) {
                        deliverAndDrawFrame(mCameraFrames[1 - mChainIdx]);
                    }
                }
            } while (!mStopThread);

            Log.d(TAG, "Ending camera worker");
        }

    }

}
