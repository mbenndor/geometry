package de.jugl.andmeasure.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import de.jugl.andmeasure.util.*;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class FovActivity extends BaseCvCameraActivity implements BaseCvCameraActivity.CvMatTouchListener {

    private static final String TAG = "FovActivity";

    /**
     * Ratio of the center square size to the display height.
     */
    private static final float MARKER_AREA = .5f;

    /**
     * Maximum deviation from the contour center to the display center.
     */
    private static final float MAX_MARKER_CENTER_DEVIATION = 0.015f;

    /**
     * Request code for field of view.
     */
    public static final int REQUEST_FOV = 0;

    /**
     * Extra field for distance to marker.
     */
    public static final String EXTRA_MARKER_DISTANCE = "markerDistance";

    /**
     * Extra field for marker radius.
     */
    public static final String EXTRA_MARKER_RADIUS = "markerRadius";

    /**
     * Extra field for calculated horizontal field of view.
     */
    public static final String EXTRA_FOV_HORIZONTAL = "fovHorizontal";

    /**
     * Matrices for image processing.
     */
    private Mat mMatRgba, mMatGray;

    /**
     * Grayscale image processor.
     */
    private MatProcessor mMatProcessor;

    /**
     * Provided marker radius and distance to marker.
     */
    private double mMarkerRadius, mMarkerDistance;

    /**
     * Area in which the marker is supposed to be.
     */
    private Rect mMarkerArea;

    /**
     * Radius of the circle in the center of the display.
     */
    private int mMaxMarkerCenterOffset;

    /**
     * Display center.
     */
    private Point mPreviewCenter;

    /**
     * Current activity state.
     */
    private ActivityState mCurrentState;

    /**
     * Sample accumulator for marker radius in pixels.
     */
    private SampleAccumulator mPixelRadiusSamples;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = this.getIntent();

        this.mMarkerDistance = i.getDoubleExtra(EXTRA_MARKER_DISTANCE, -1d);
        this.mMarkerRadius = i.getDoubleExtra(EXTRA_MARKER_RADIUS, -1d);

        Log.d(TAG, "Marker distance: " + this.mMarkerDistance);
        Log.d(TAG, "Marker radius: " + this.mMarkerRadius);

        this.mCurrentState = ActivityState.IDLE;
        this.mPixelRadiusSamples = new SampleAccumulator();

        this.setCvMatTouchListener(this);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        super.onCameraViewStarted(width, height);

        this.mMatProcessor = new MatProcessor();
        this.mMatProcessor.set(MatProcessor.PARAM_MIN_CONTOUR_AREA, 500);

        // Marker center needs to be within display center.
        this.mMaxMarkerCenterOffset = Math.round(MAX_MARKER_CENTER_DEVIATION * Math.min(width, height));
        this.mPreviewCenter = new Point(width / 2d, height / 2d);

        // The marker should be in the display center. The area, in which we'll search for the marker, is square.
        int markerAreaSideLength = Math.round(Math.min(width, height) * MARKER_AREA);

        this.mMarkerArea = new Rect(
                (int) (this.mPreviewCenter.x - markerAreaSideLength / 2),
                (int) (this.mPreviewCenter.y - markerAreaSideLength / 2),
                markerAreaSideLength,
                markerAreaSideLength
        );
    }

    @Override
    public void onCameraViewStopped() {
        super.onCameraViewStopped();

        this.safelyDeallocate(this.mMatRgba);
        this.safelyDeallocate(this.mMatGray);
    }

    /**
     * Calculates the horizontal field of view and ends this activity's lifecycle.
     */
    private void setResultAndFinish() {
        Intent i = new Intent();

        double pixelRadius = this.mPixelRadiusSamples.getAverage();

        double previewWidthHalf = this.getPreviewSize().width / 2;
        double lengthHalf = this.mMarkerRadius * previewWidthHalf / pixelRadius;
        double hFov = 2 * Math.atan(lengthHalf / this.mMarkerDistance);

        i.putExtra(EXTRA_FOV_HORIZONTAL, hFov);

        Log.d(TAG, "Marker pixel radius: " + pixelRadius);
        Log.d(TAG, "Horizontal FOV: " + Math.toDegrees(hFov));

        if (!this.tryWriteLog()) {
            Log.e(TAG, "Couldn't write log file.");
        }

        this.setResult(RESULT_OK, i);
        this.finish();
    }

    /**
     * Tries to write a CSV log with the taken measurements.
     *
     * @return <code>true</code> if saved successfully, <code>false</code> otherwise
     */
    private boolean tryWriteLog() {
        CsvWriter writer = new CsvWriter("calib", this, new String[] { "radius" });

        if (!writer.open()) {
            return false;
        }

        for (double d : this.mPixelRadiusSamples.getSamples()) {
            writer.write(new Object[] { d });
        }

        return writer.close();
    }


    /**
     * Renders the center area.
     */
    private void handleIdle() {
        Imgproc.rectangle(this.mMatRgba, this.mMarkerArea, CvUtil.RGB_GREEN, 3);
        Imgproc.circle(this.mMatRgba, this.mPreviewCenter, this.mMaxMarkerCenterOffset, CvUtil.RGB_GREEN, Imgproc.FILLED);
    }

    /**
     * Searches for markers within the center area.
     */
    private void handleSample() {
        // Preprocessing the grayscale image.
        Mat markerMat = this.mMatGray.submat(this.mMarkerArea);
        List<MatOfPoint> contours = new ArrayList<>();

        this.mMatProcessor.preprocess(markerMat);
        this.mMatProcessor.findCircleContours(contours, markerMat);

        // If our data series was interrupted, reset the activity.
        if (contours.size() == 0) {
            this.mCurrentState = ActivityState.IDLE;
            return;
        }

        Size size = new Size();
        Point offset = new Point();

        markerMat.locateROI(size, offset);

        // Reassignable ellipse.
        RotatedRect rect;

        for (MatOfPoint mat : contours) {
            rect = Imgproc.fitEllipse(CvUtil.toMatOfPoint2f(mat));
            CvUtil.adjustForOffset(offset, rect.center);

            // Check if the contour center is within the device center.
            if (CvUtil.euclid(this.mPreviewCenter, rect.center) > this.mMaxMarkerCenterOffset) {
                continue; // If not, keep searching.
            }

            double circleDiameter = CvUtil.getDiameterFromEllipse(rect);
            this.mPixelRadiusSamples.push(circleDiameter / 2);

            // Finish activity if all samples were collected.
            if (this.mPixelRadiusSamples.isFull()) {
                this.setResultAndFinish();
                return;
            }

            // Draw the contour we found.
            Imgproc.ellipse(this.mMatRgba, rect, CvUtil.RGB_RED, 3);
            Imgproc.circle(this.mMatRgba, rect.center, 3, CvUtil.RGB_RED, Imgproc.FILLED);

            // Draw progress.
            this.setProgress((float) this.mPixelRadiusSamples.getSampleCount() / this.mPixelRadiusSamples.getSampleSize());
            this.renderProgressBar(this.mMatRgba);

            return;
        }

        // If we end up here, then we found no contour matching all our requirements. Reset.
        this.mCurrentState = ActivityState.IDLE;
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        this.mMatRgba = inputFrame.rgba();
        this.mMatGray = inputFrame.gray();

        switch (this.mCurrentState) {
            case IDLE: this.handleIdle(); break;
            case SAMPLE: this.handleSample(); break;
        }

        return this.mMatRgba;
    }

    @Override
    public void onTouchDown(MotionEvent evt, int x, int y) {

    }

    @Override
    public void onTouchMove(MotionEvent evt, int x, int y) {

    }

    @Override
    public void onTouchUp(MotionEvent evt, int x, int y) {
        if (this.mCurrentState == ActivityState.IDLE) {
            this.mPixelRadiusSamples.clear();
            this.mCurrentState = ActivityState.SAMPLE;
        }
    }

    private enum ActivityState {

        IDLE,
        SAMPLE

    }

}
