package de.jugl.andmeasure.activity;

import android.content.pm.ActivityInfo;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.jugl.andmeasure.util.CvUtil;
import de.jugl.andmeasure.R;

public abstract class BaseCvCameraActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "BaseCvCameraActivity";

    /**
     * {@link CameraBridgeViewBase} bound to this activity.
     */
    private CameraBridgeViewBase mOpenCvCameraView;

    /**
     * Left margin of debug information in the camera view in pixels.
     */
    private static final int DEBUG_LINE_LEFT_MARGIN = 16;

    /**
     * Top margin of debug information in the camera view in pixels.
     */
    private static final int DEBUG_LINE_TOP_MARGIN = 36;

    /**
     * Space between lines in debug information in pixels.
     */
    private static final int DEBUG_LINE_SPACING = 32;

    /**
     * Offset of the shadow drawn under debug information in pixels.
     */
    private static final int DEBUG_SHADOW_OFFSET = 2;

    /**
     * Text thickness of debug information in pixels.
     */
    private static final int DEBUG_TEXT_THICKNESS = 2;

    /**
     * Height of the progress bar in pixels.
     */
    private static final int PROGRESS_BAR_HEIGHT = 16;

    /**
     * Lines to be rendered in the top-left corner of the camera view.
     */
    private List<String> mDebugInfoLines;

    /**
     * Progress of the progress bar.
     */
    private float mProgress = 0f;

    /**
     * Points in the camera view denoting the corners of the progress bar (foreground and background).
     */
    private Point mProgressTl, mProgressBgBr, mProgressFgBr;

    /**
     * Camera preview scale factor.
     */
    private float mScale;

    /**
     * Preview size of the camera.
     */
    private Size mPreviewSize;

    /**
     * Matrix touch listener associated with this activity.
     */
    private CvMatTouchListener mMatTouchListener = null;

    /**
     * Display metrics object.
     */
    private DisplayMetrics mDisplayMetrics;

    /**
     * Aspect ratio of the display.
     */
    private float mDisplayAspectRatio;

    /**
     * Aspect ratio of the camera preview.
     */
    private float mPreviewAspectRatio;

    /**
     * <code>true</code> if {@link #mDisplayAspectRatio} and {@link #mPreviewAspectRatio} are considered equal,
     * <code>false</code> otherwise.
     */
    private boolean mIsPreviewAndDisplayAspectEqual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Remove title bar and set phone to landscape mode.
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Fullscreen activity and keep the screen on.
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Starts immersive mode. See https://developer.android.com/training/system-ui/immersive.
        this.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE |
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN
        );

        this.setTheme(R.style.AppTheme_CvFullscreen);
        this.setContentView(R.layout.activity_cv);

        // Start OpenCV camera view.
        this.mOpenCvCameraView = this.findViewById(R.id.cv_camera_view);
        this.mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        this.mOpenCvCameraView.setCvCameraViewListener(this);
        this.mOpenCvCameraView.setCameraPermissionGranted();

        this.mDebugInfoLines = new ArrayList<>();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        this.mDisplayMetrics = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getMetrics(this.mDisplayMetrics);
        this.mDisplayAspectRatio = (float) this.mDisplayMetrics.widthPixels / this.mDisplayMetrics.heightPixels;

        this.mScale = Math.max((float) width / this.mDisplayMetrics.widthPixels, (float) height / this.mDisplayMetrics.heightPixels);
        this.mPreviewSize = new Size(width, height);
        this.mPreviewAspectRatio = (float) width / height;

        // Check equality of aspect ratios with a small (0.0001) delta.
        this.mIsPreviewAndDisplayAspectEqual = Math.abs(this.mDisplayAspectRatio - this.mPreviewAspectRatio) < 0.0001f;

        Log.d(TAG, String.format("Screen size: %dx%d (%.4f)", this.mDisplayMetrics.widthPixels, this.mDisplayMetrics.heightPixels, this.mDisplayAspectRatio));
        Log.d(TAG, String.format("Preview size: %dx%d (%.4f)", width, height, this.mPreviewAspectRatio));
        Log.d(TAG, String.format("Scale factor: %.4f", this.mScale));
        Log.d(TAG, String.format("Aspect ratios match: %s", this.mIsPreviewAndDisplayAspectEqual));

        // Set corner points for progress bar.
        this.mProgressTl = new Point(0, height - PROGRESS_BAR_HEIGHT);
        this.mProgressBgBr = new Point(width, height);
        this.mProgressFgBr = new Point(0, height);
    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    protected void onStop() {
        super.onStop();

        this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (this.mOpenCvCameraView != null) {
            this.mOpenCvCameraView.disableView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV failed to load. Exiting.");
            this.finish();
        } else {
            this.mOpenCvCameraView.enableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (this.mOpenCvCameraView != null) {
            this.mOpenCvCameraView.disableView();
        }
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        return inputFrame.rgba();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.mMatTouchListener != null) {
            // Scale screen coordinates to preview coordinates.
            int x = Math.round(event.getX() * this.mScale);
            int y = Math.round(event.getY() * this.mScale);

            if (!this.mIsPreviewAndDisplayAspectEqual) {
                // If display width is greater than preview width, adjust x. Otherwise adjust y.
                if (this.mDisplayAspectRatio > this.mPreviewAspectRatio) {
                    x -= Math.round((this.mDisplayMetrics.widthPixels - this.mPreviewSize.width) * this.mScale);
                    x = Math.min(Math.max(x, 0), (int) this.mPreviewSize.width); // Clamp between 0 and preview width.
                } else {
                    y -= Math.round((this.mDisplayMetrics.heightPixels - this.mPreviewSize.height) * this.mScale);
                    y = Math.min(Math.max(y, 0), (int) this.mPreviewSize.height); // Clamp between 0 and preview height.
                }
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    this.mMatTouchListener.onTouchDown(event, x, y);
                } return true;

                case MotionEvent.ACTION_MOVE: {
                    this.mMatTouchListener.onTouchMove(event, x, y);
                } return true;

                case MotionEvent.ACTION_UP: {
                    this.mMatTouchListener.onTouchUp(event, x, y);
                } return true;
            }
        }

        return super.onTouchEvent(event);
    }

    /**
     * Adds a line that can be rendered onto the image using
     * {@link #renderDebugInfo(Mat)}.
     *
     * @param line Line to render
     */
    protected void addDebugInfo(String line) {
        this.mDebugInfoLines.add(line);
    }

    /**
     * Alias for <code>this.addDebugInfo(String.format(Locale.ENGLISH, str, args))</code>.
     *
     * @param str Line to render
     * @param args Format arguments
     */
    protected void addFormattedDebugInfo(String str, Object[] args) {
        this.addDebugInfo(String.format(Locale.ENGLISH, str, args));
    }

    /**
     * Renders lines added prior with {@link #addDebugInfo(String)} in the top left corner of
     * the provided image. The list of lines will be cleared after rendering.
     * Text will be rendered in white with a black shadow.
     *
     * @param m Image to render the lines on
     */
    protected void renderDebugInfo(Mat m) {
        Point lineP = new Point();

        // Start in the top left corner and increase y-coordinate
        // with every new line.
        lineP.x = DEBUG_LINE_LEFT_MARGIN;
        lineP.y = DEBUG_LINE_TOP_MARGIN;

        for (String line : this.mDebugInfoLines) {
            // Render shadow.
            lineP.x += DEBUG_SHADOW_OFFSET;
            lineP.y += DEBUG_SHADOW_OFFSET;

            Imgproc.putText(m, line, lineP, Imgproc.FONT_HERSHEY_SIMPLEX, 1d, CvUtil.RGB_BLACK, DEBUG_TEXT_THICKNESS);

            // Render text.
            lineP.x -= DEBUG_SHADOW_OFFSET;
            lineP.y -= DEBUG_SHADOW_OFFSET;

            Imgproc.putText(m, line, lineP, Imgproc.FONT_HERSHEY_SIMPLEX, 1d, CvUtil.RGB_WHITE, DEBUG_TEXT_THICKNESS);

            lineP.y += DEBUG_LINE_SPACING;
        }

        this.mDebugInfoLines.clear();
    }

    /**
     * Sets the progress of the progress bar that can be rendered onto an image
     * using {@link #renderProgressBar(Mat)}. The value is clamped between 0 and 1.
     *
     * @param progress Progress to display
     */
    protected void setProgress(float progress) {
        this.mProgress = Math.max(Math.min(progress, 1f), 0f);
        this.mProgressFgBr.x = this.mProgressBgBr.x * this.mProgress;
    }

    /**
     * Renders a progress bar onto the bottom edge of the provided image. Progress can be set
     * using {@link #setProgress(float)}.
     *
     * @param m Image to render the progress bar to
     */
    protected void renderProgressBar(Mat m) {
        Imgproc.rectangle(m, this.mProgressTl, this.mProgressBgBr, CvUtil.RGB_BLACK, Imgproc.FILLED);
        Imgproc.rectangle(m, this.mProgressTl, this.mProgressFgBr, CvUtil.RGB_WHITE, Imgproc.FILLED);
    }

    /**
     * Alias for <code>if (m != null) m.release();</code>.
     *
     * @param m Matrix to deallocate
     */
    protected void safelyDeallocate(Mat m) {
        if (m != null) {
            m.release();
        }
    }

    /**
     * @return Scaling factor of camera preview
     */
    protected float getScale() {
        return mScale;
    }

    /**
     * Sets the {@link CvMatTouchListener} for this activity.
     *
     * @param listener Listener to attach to this activity
     */
    protected void setCvMatTouchListener(CvMatTouchListener listener) {
        this.mMatTouchListener = listener;
    }

    /**
     * @return Camera preview size
     */
    protected Size getPreviewSize() {
        return mPreviewSize;
    }

    /**
     * @return {@link CameraBridgeViewBase} bound to this activity
     */
    protected CameraBridgeViewBase getCameraView() {
        return this.mOpenCvCameraView;
    }

    public interface CvMatTouchListener {

        /**
         * Propagates a {@link MotionEvent} in a {@link MotionEvent#ACTION_DOWN} state. The x- and y-coordinates
         * refer to the location within the image.
         *
         * @param evt {@link MotionEvent} that caused this call
         * @param x x-coordinate in the image
         * @param y y-coordinate in the image
         */
        void onTouchDown(MotionEvent evt, int x, int y);

         /**
         * Propagates a {@link MotionEvent} in a {@link MotionEvent#ACTION_MOVE} state. The x- and y-coordinates
         * refer to the location within the image.
         *
         * @param evt {@link MotionEvent} that caused this call
         * @param x x-coordinate in the image
         * @param y y-coordinate in the image
         */
        void onTouchMove(MotionEvent evt, int x, int y);

        /**
         * Propagates a {@link MotionEvent} in a {@link MotionEvent#ACTION_UP} state. The x- and y-coordinates
         * refer to the location within the image.
         *
         * @param evt {@link MotionEvent} that caused this call
         * @param x x-coordinate in the image
         * @param y y-coordinate in the image
         */
        void onTouchUp(MotionEvent evt, int x, int y);

    }

}

