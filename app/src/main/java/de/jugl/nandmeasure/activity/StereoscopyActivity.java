package de.jugl.nandmeasure.activity;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import de.jugl.nandmeasure.util.*;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class StereoscopyActivity extends BaseCvCameraActivity implements SensorEventListener, BaseCvCameraActivity.CvMatTouchListener {

    private static final String TAG = "StereoscopyActivity";

    /**
     * Extra field for distance between the two camera locations.
     */
    public static final String EXTRA_CAMERA_DISTANCE = "camDistance";

    /**
     * Extra field for the camera's horizontal FOV.
     */
    public static final String EXTRA_HORIZONTAL_FOV = "horizontalFov";

    /**
     * Extra field for the calculated marker distance.
     */
    public static final String EXTRA_DISTANCE = "distance";

    /**
     * Extra field for the calculated, corrected marker distance.
     */
    public static final String EXTRA_CORRECTED_DISTANCE = "correctedDistance";

    /**
     * Extra field to request no marker distance correction.
     */
    public static final String EXTRA_IGNORE_CORRECTION = "ignoreCorrection";

    /**
     * Request code for distance measurement.
     */
    public static final int REQUEST_DISTANCE = 0;

    /**
     * Maximum deviation in the device orientation between the two camera locations.
     */
    private static final float ANGLE_DIFFERENCE = (float) Math.toRadians(1);

    /**
     * User inputs. Distance between the two camera locations and horizontal FOV.
     */
    private double mCameraDistance, mHorizontalFov;

    /**
     * x-coordinate of the marker's location in the right and left half of the image.
     */
    private SampleAccumulator mStartX, mStopX;

    /**
     * Matrices for image processing.
     */
    private Mat mMatRgba, mMatGray;

    /**
     * Grayscale image processor.
     */
    private MatProcessor mMatProcessor;

    /**
     * Current activity state.
     */
    private ActivityState mCurrentState;

    /**
     * Current iteration. (1 = right half, 2 = left half)
     */
    private int mIteration = 1;

    /**
     * User selection in image.
     */
    private Rect mUserSelection;

    /**
     * Helper class for user inputs.
     */
    private UserSelectionHelper mUserSelectionHelper;

    /**
     * Helper class for continuous orientation angles.
     */
    private OrientationUtil.ContinuousAngleWrapper mYawWrapper;

    /**
     * Sample accumulators for device orientation at the two camera locations.
     */
    private SampleAccumulator mStartYaw, mStopYaw;

    /**
     * Device sensor manager.
     */
    private SensorManager mSensorManager;

    /**
     * Rotation vector sensor.
     */
    private Sensor mRotationVectorSensor;

    /**
     * Reusable array to write the orientation angles to.
     */
    private float[] mOrientationArray;

    /**
     * Intermediate values for the average device orientation at the first camera location and the current
     * deviation of the device's orientation from said average.
     */
    private double mAvgStartYaw, mStartStopYawDifference;

    /**
     * <code>true</code> if the angle deviation check should be overridden, <code>false</code> otherwise.
     */
    private boolean mIgnoreAngleCheck;

    /**
     * Top and bottom points of the line that separates the two image halves.
     */
    private Point mHalfTopPoint, mHalfBottomPoint;

    /**
     * Half of the preview width in pixels.
     */
    private int mPreviewWidthHalf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = this.getIntent();

        this.mCameraDistance = i.getDoubleExtra(EXTRA_CAMERA_DISTANCE, -1d);
        this.mHorizontalFov = i.getDoubleExtra(EXTRA_HORIZONTAL_FOV, -1d);
        this.mIgnoreAngleCheck = i.getBooleanExtra(EXTRA_IGNORE_CORRECTION, false);

        // Sample accumulators.
        this.mStartX = new SampleAccumulator();
        this.mStopX = new SampleAccumulator();
        this.mStartYaw = new SampleAccumulator();
        this.mStopYaw = new SampleAccumulator();

        // Orientation fields.
        this.mYawWrapper = new OrientationUtil.ContinuousAngleWrapper();
        this.mOrientationArray = new float[3];

        // Sensor service.
        this.mSensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        this.mRotationVectorSensor = this.mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        this.mCurrentState = ActivityState.IDLE;

        this.setCvMatTouchListener(this);

        Log.d(TAG, "Cam distance: " + this.mCameraDistance);
        Log.d(TAG, "Horizontal FOV: " + Math.toDegrees(this.mHorizontalFov));
        Log.d(TAG, "Half h. FOV: " + Math.toDegrees(this.mHorizontalFov / 2d));
        Log.d(TAG, "Ignore angle check: " + this.mIgnoreAngleCheck);
    }

    @Override
    public void onTouchDown(MotionEvent evt, int x, int y) {
        // Start user selection if activity is currently doing nothing.
        if (this.mCurrentState == ActivityState.IDLE) {
            this.mUserSelectionHelper.onTouchDown(evt, x, y);
            this.mCurrentState = ActivityState.DRAW;
        }
    }

    @Override
    public void onTouchMove(MotionEvent evt, int x, int y) {
        // Continue user selection if we're still drawing.
        if (this.mCurrentState == ActivityState.DRAW) {
            this.mUserSelectionHelper.onTouchMove(evt, x, y);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        this.mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        this.mSensorManager.registerListener(this, this.mRotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    public void onTouchUp(MotionEvent evt, int x, int y) {
        // Stop user selection if we're still drawing.
        if (this.mCurrentState == ActivityState.DRAW) {
            this.mUserSelectionHelper.onTouchUp(evt, x, y);
            this.mUserSelection = this.mUserSelectionHelper.getSelection();

            // Make sure that the user selection has an area.
            if (Math.min(this.mUserSelection.width, this.mUserSelection.height) == 0) {
                this.mCurrentState = ActivityState.IDLE;
                return;
            }

            // Clear sample accumulators.
            this.mStopX.clear();
            this.mStopYaw.clear();

            if (this.mIteration == 1) {
                this.mStartX.clear();
                this.mStartYaw.clear();
            } else {
                // If we're in the second phase, make sure that the device orientation didn't change much
                // while repositioning the device.
                if (Math.abs(this.mStartStopYawDifference) > ANGLE_DIFFERENCE && !this.mIgnoreAngleCheck) {
                    this.mCurrentState = ActivityState.IDLE;
                    return;
                }
            }

            this.mCurrentState = ActivityState.MEASURE;
        }
    }

    @Override
    public void onBackPressed() {
        // Reset to idle first.
        if (this.mCurrentState != ActivityState.IDLE) {
            this.mCurrentState = ActivityState.IDLE;
        } else {
            // If we are already idling and in the second phase, reset to first phase.
            if (this.mIteration == 2) {
                this.mIteration--;
            } else {
                // If we're in first phase, close activity.
                super.onBackPressed();
            }
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        super.onCameraViewStarted(width, height);

        this.mMatProcessor = new MatProcessor();
        this.mUserSelectionHelper = new UserSelectionHelper();

        this.mPreviewWidthHalf = width / 2;
        this.mHalfTopPoint = new Point(this.mPreviewWidthHalf, 0);
        this.mHalfBottomPoint = new Point(this.mPreviewWidthHalf, height);
    }

    @Override
    public void onCameraViewStopped() {
        super.onCameraViewStopped();

        this.safelyDeallocate(this.mMatRgba);
        this.safelyDeallocate(this.mMatGray);
    }

    /**
     * Draws the user selection.
     */
    private void handleDraw() {
        Imgproc.rectangle(this.mMatRgba, this.mUserSelectionHelper.getP1(), this.mUserSelectionHelper.getP2(), CvUtil.RGB_BLUE, 3);
    }

    /**
     * Calculates the distance to the marker.
     *
     * @param applyError <code>true</code> if the change in device orientation should be accounted for, <code>false</code> otherwise
     * @return Distance to marker
     */
    private double calculateDistance(boolean applyError) {
        double fovHalf = this.mHorizontalFov / 2d;
        double fovHalfTan = Math.tan(fovHalf); // We only use the tangent anyway.

        // Calculate horizontal offset.
        double xr = this.mStartX.getAverage() - this.mPreviewWidthHalf;
        double xl = this.mStopX.getAverage() - this.mPreviewWidthHalf;

        Log.d(TAG, "xr=" + xr + ", xl=" + xl);

        if (applyError) {
            // Calculate error due to change in device orientation.
            double errorAngle = this.mStopYaw.getAverage() - this.mStartYaw.getAverage();
            double xError = Math.tan(Math.abs(errorAngle)) * this.mPreviewWidthHalf / fovHalfTan;

            // Project change in device orientation onto the projection plane.
            if (Math.signum(errorAngle) == -1) {
                // Anticlockwise change in orientation.
                double xProjected = xl / Math.cos(errorAngle);
                xl = xProjected - xError;
            } else {
                // Clockwise change in orientation.
                double xProjected = xl + xError;
                xl = Math.cos(errorAngle) * xProjected;
            }

            Log.d(TAG, "error angle=" + Math.toDegrees(errorAngle) + ", ex=" + xError);
            Log.d(TAG, "corrected xl=" + xl);
        }

        return this.mCameraDistance * this.getPreviewSize().width / (2 * fovHalfTan * (xr - xl));
    }

    /**
     * Calculates distance to marker and ends the activity lifecycle.
     */
    private void setResultAndFinish() {
        double distance = this.calculateDistance(false);
        double distanceWithError = this.calculateDistance(true);

        Intent i = new Intent();
        i.putExtra(StereoscopyActivity.EXTRA_DISTANCE, distance);
        i.putExtra(StereoscopyActivity.EXTRA_CORRECTED_DISTANCE, distanceWithError);

        if (!this.tryWriteLog()) {
            Log.e(TAG, "Couldn't write log file.");
        }

        this.setResult(RESULT_OK, i);
        this.finish();
    }

    /**
     * Tries to write a CSV log containing the collected samples.
     *
     * @return <code>true</code> if saved successfully, <code>false</code> otherwise
     */
    private boolean tryWriteLog() {
        CsvWriter angleWriter = new CsvWriter("stereo", this, new String[] {
                "xStartYaw", "x1", "xStopYaw", "x2"
        });

        if (!angleWriter.open()) {
            return false;
        }

        for (int i = 0; i < SampleAccumulator.DEFAULT_SAMPLE_SIZE; i++) {
            angleWriter.write(new Object[] {
                    this.mStartYaw.getSamples()[i],
                    this.mStartX.getSamples()[i],
                    this.mStopYaw.getSamples()[i],
                    this.mStopX.getSamples()[i]
            });
        }

        return angleWriter.close();
    }

    /**
     * Handles marker measurements.
     */
    private void handleMeasure() {
        Size size = new Size();
        Point offset = new Point();

        List<MatOfPoint> contours = new ArrayList<>();
        Mat roi = this.mMatGray.submat(this.mUserSelection);
        roi.locateROI(size, offset);

        this.mMatProcessor.preprocess(roi);
        this.mMatProcessor.findCircleContours(contours, roi);

        // Reset if no marker was found.
        if (contours.size() == 0) {
            this.mCurrentState = ActivityState.IDLE;
            return;
        }

        MatOfPoint contour = contours.get(0);
        RotatedRect rect = Imgproc.fitEllipse(CvUtil.toMatOfPoint2f(contour));
        CvUtil.adjustForOffset(offset, rect.center);

        // First phase = right half. Second phase = left half.
        if (this.mIteration == 1) {
            // Check if marker is in right half.
            if (rect.center.x < this.mPreviewWidthHalf) {
                this.mCurrentState = ActivityState.IDLE;
                return;
            }

            this.mStartX.push(rect.center.x);

            // If all samples were collected, start next phase.
            if (this.mStartX.isFull() && this.mStartYaw.isFull()) {
                this.mCurrentState = ActivityState.IDLE;
                this.mAvgStartYaw = this.mStartYaw.getAverage();
                this.mIteration++;
            }
        } else {
            // Check if marker is in left half.
            if (rect.center.x > this.mPreviewWidthHalf) {
                this.mCurrentState = ActivityState.IDLE;
                return;
            }

            this.mStopX.push(rect.center.x);

            // If all samples were collected, finish activity.
            if (this.mStopX.isFull() && this.mStopYaw.isFull()) {
                this.setResultAndFinish();
                return;
            }
        }

        // Within every phase, we collect 50 marker measurements and 50 orientation measurements.
        int sampleSize = 2 * SampleAccumulator.DEFAULT_SAMPLE_SIZE;
        float progress = 0f;

        if (this.mIteration == 1) {
            progress = this.mStartX.getSampleCount() + this.mStartYaw.getSampleCount();
        } else {
            progress = this.mStopX.getSampleCount() + this.mStopYaw.getSampleCount();
        }

        progress /= (float) sampleSize;

        this.setProgress(progress);
        this.renderProgressBar(this.mMatRgba);

        Imgproc.rectangle(this.mMatRgba, this.mUserSelection, CvUtil.RGB_BLUE, 3);
        Imgproc.ellipse(this.mMatRgba, rect, CvUtil.RGB_RED, 3);
        Imgproc.circle(this.mMatRgba, rect.center, 3, CvUtil.RGB_RED, Imgproc.FILLED);
    }

    /**
     * Renders additional info onto the image that are independent from the activity's state.
     */
    private void renderInfo() {
        // Aktuelle Phase.
        this.addFormattedDebugInfo("Iteration: %d", new Object[] { this.mIteration });

        // Abweichung in der Ausrichtung zur ersten Phase.
        if (this.mIteration == 2) {
            this.addFormattedDebugInfo("Angle difference: %.2f", new Object[] { Math.toDegrees(this.mStartStopYawDifference) });
        }

        // Linie, welche die rechte und linke Bildhälfte teilt.
        Imgproc.line(this.mMatRgba, this.mHalfTopPoint, this.mHalfBottomPoint, CvUtil.RGB_GREEN, 3);
        this.renderDebugInfo(this.mMatRgba);
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        this.mMatRgba = inputFrame.rgba();
        this.mMatGray = inputFrame.gray();

        this.renderInfo();

        switch (this.mCurrentState) {
            case DRAW:      this.handleDraw(); break;
            case MEASURE:   this.handleMeasure(); break;
        }

        return this.mMatRgba;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Orientation must always be kept up to date.
        OrientationUtil.convertQuaternionToEuler(event.values, this.mOrientationArray);
        this.mYawWrapper.update(this.mOrientationArray[0]);

        // Keep the deviation between the two phases up to date.
        if (this.mIteration == 2) {
            this.mStartStopYawDifference = this.mYawWrapper.getFullAngle() - this.mAvgStartYaw;
        }

        // If no samples are being collected, continue.
        if (this.mCurrentState != ActivityState.MEASURE) {
            return;
        }

        // Inflate sample accumulator depending on the current phase.
        float fullAngle = this.mYawWrapper.getFullAngle();

        if (this.mIteration == 1) {
            this.mStartYaw.push(fullAngle);
        } else {
            this.mStopYaw.push(fullAngle);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private enum ActivityState {

        IDLE,
        DRAW,
        MEASURE

    }

}
