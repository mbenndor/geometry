package de.jugl.nandmeasure.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import de.jugl.nandmeasure.CalibrationProfile;
import de.jugl.nandmeasure.util.*;
import de.jugl.nandmeasure.view.AndCameraView;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class CaoActivity extends BaseCvCameraActivity implements BaseCvCameraActivity.CvMatTouchListener {

    private static final String TAG = "CaoActivity";

    /**
     * Request code for calibration mode.
     */
    public static final int REQUEST_CALIBRATION = 0;

    /**
     * Request code for measuring mode.
     */
    public static final int REQUEST_MEASUREMENT = 1;

    /**
     * Extra field for the request code.
     */
    public static final String EXTRA_REQUEST_CODE = "requestCode";

    /**
     * Extra field for the calculated distance.
     */
    public static final String EXTRA_DISTANCE = "distance";

    /**
     * Extra field for the calibration profile to use.
     */
    public static final String EXTRA_CALIBRATION_PROFILE = "profile";

    /**
     * Calibration profile to use.
     */
    private CalibrationProfile mProfile;

    /**
     * Provided request code.
     */
    private int mRequest;

    /**
     * Matrices for image processing.
     */
    private Mat mMatRgba, mMatGray;

    /**
     * Grayscale image processor.
     */
    private MatProcessor mMatProcessor;

    /**
     * Current state of the activity.
     */
    private ActivityState mCurrentState;

    /**
     * User selection in image.
     */
    private Rect mUserSelection;

    /**
     * Sample accumulator.
     */
    private SampleAccumulator mMeasureSampleAccumulator;

    /**
     * Focal length of the camera.
     */
    private double mFocalLength;

    /**
     * Helper class for user inputs on the image.
     */
    private UserSelectionHelper mUserSelectionHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = this.getIntent();

        this.mProfile = i.getParcelableExtra(EXTRA_CALIBRATION_PROFILE);
        this.mRequest = i.getIntExtra(EXTRA_REQUEST_CODE, REQUEST_CALIBRATION);

        this.mCurrentState = ActivityState.IDLE;
        this.mMeasureSampleAccumulator = new SampleAccumulator();

        this.setCvMatTouchListener(this);

        Log.d(TAG, "Marker distance: " + this.mProfile.getDistance());
        Log.d(TAG, "Marker pixel radius: " + this.mProfile.getPixelRadius());
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        super.onCameraViewStarted(width, height);

        this.mMatProcessor = new MatProcessor();
        this.mMatProcessor.set(MatProcessor.PARAM_MIN_CONTOUR_AREA, 200);

        this.mUserSelectionHelper = new UserSelectionHelper();

        // We can retrieve the focal length from the camera parameters.
        // Provided in mm, needed in cm, therefore divide by 10.
        this.mFocalLength = ((AndCameraView) this.getCameraView()).getCamera().getParameters().getFocalLength() / 10d;
    }

    @Override
    public void onCameraViewStopped() {
        super.onCameraViewStopped();

        this.safelyDeallocate(this.mMatGray);
        this.safelyDeallocate(this.mMatRgba);
    }

    /**
     * Draws the user selection into the image.
     */
    private void handleDraw() {
        Imgproc.rectangle(this.mMatRgba, this.mUserSelectionHelper.getP1(), this.mUserSelectionHelper.getP2(), CvUtil.RGB_BLUE, 3);
    }

    /**
     * Calculates the area of a circle with a given radius.
     *
     * @param circleRadius Circle radius
     * @return Circle area
     */
    private double calculateCircleArea(double circleRadius) {
        return Math.PI * Math.pow(circleRadius, 2d);
    }

    /**
     * Calculates the size of a marker within the camera's focal point given its projected size.
     * The result depends on the submitted calibration profile.
     *
     * @param circleArea Marker size
     * @return Size of marker within the camera's focal point
     */
    private double calculateFocalSize(double circleArea) {
        return circleArea * Math.pow(this.mProfile.getDistance(), 2d) / Math.pow(this.mFocalLength, 2d);
    }

    /**
     * Calculates the distance to a marker given its projected size. The result depends on the submitted
     * calibration profile.
     *
     * @param circleArea Marker size
     * @return Distance to the marker
     */
    private double calculateDistance(double circleArea) {
        double focalSize = this.calculateFocalSize(this.calculateCircleArea(this.mProfile.getPixelRadius()));
        return this.mFocalLength / Math.sqrt(circleArea / focalSize);
    }

    /**
     * Sets the return values of this activity depending on the request code and ends its lifecycle.
     */
    private void setResultAndFinish() {
        Intent i = new Intent();

        double result = this.mMeasureSampleAccumulator.getAverage();

        // When calibrating, we want the marker size in the camera's focal point. When measuring,
        // we want the distance to the marker.
        if (this.mRequest == REQUEST_CALIBRATION) {
            this.mProfile.setPixelRadius(result);
            i.putExtra(EXTRA_CALIBRATION_PROFILE, this.mProfile);
        } else {
            i.putExtra(EXTRA_DISTANCE, this.calculateDistance(this.calculateCircleArea(result)));
        }

        if (!this.tryWriteLog()) {
            Log.e(TAG, "Couldn't write log file.");
        }

        this.setResult(RESULT_OK, i);
        this.finish();
   }

    /**
     * Tries to write a CSV log with the collected samples.
     *
     * @return <code>true</code> if saved successfully, <code>false</code> otherwise
     */
   private boolean tryWriteLog() {
        CsvWriter writer = new CsvWriter("cao", this, new String[] { "radius" });

        if (!writer.open()) {
            return false;
        }

        for (double d : this.mMeasureSampleAccumulator.getSamples()) {
            writer.write(new Object[] { d });
        }

        return writer.close();
   }

    /**
     * Searches for an ellipse in the area drawn by the user. Runs measurements on the ellipse.
     */
    private void handleMeasure() {
        Mat contourArea = this.mMatGray.submat(this.mUserSelection);
        List<MatOfPoint> contours = new ArrayList<>();

        this.mMatProcessor.preprocess(contourArea);
        this.mMatProcessor.findContoursForEllipseFit(contours, contourArea);

        // Abort if no contour was found.
        if (contours.size() == 0) {
            this.mCurrentState = ActivityState.IDLE;
            return;
        }

        MatOfPoint ellipseMat = contours.get(0);
        MatOfPoint2f ellipseMat2f = new MatOfPoint2f(ellipseMat.toArray());
        RotatedRect ellipse = Imgproc.fitEllipse(ellipseMat2f);

        // Circle radius is the same as half the ellipse's major axis.
        double circleRadius = CvUtil.getDiameterFromEllipse(ellipse) / 2d;
        this.mMeasureSampleAccumulator.push(circleRadius);

        if (this.mMeasureSampleAccumulator.isFull()) {
            this.setResultAndFinish();
            return;
        }

        Point offset = new Point();
        Size size = new Size();

        contourArea.locateROI(size, offset);
        CvUtil.adjustForOffset(offset, ellipse.center);

        // Render user selection and ellipse.
        Imgproc.rectangle(this.mMatRgba, this.mUserSelection, CvUtil.RGB_BLUE, 3);
        Imgproc.ellipse(this.mMatRgba, ellipse, CvUtil.RGB_RED, 3);

        this.setProgress((float) this.mMeasureSampleAccumulator.getSampleCount() / this.mMeasureSampleAccumulator.getSampleSize());
        this.renderProgressBar(this.mMatRgba);
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        this.mMatRgba = inputFrame.rgba();
        this.mMatGray = inputFrame.gray();

        switch (this.mCurrentState) {
            case DRAW: this.handleDraw(); break;
            case MEASURE: this.handleMeasure(); break;
        }

        return this.mMatRgba;
    }

    @Override
    public void onBackPressed() {
        if (this.mCurrentState != ActivityState.IDLE) {
            this.mCurrentState = ActivityState.IDLE;
        } else {
            super.onBackPressed();
        }
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
        // Continue user selection in case we're still drawing.
        if (this.mCurrentState == ActivityState.DRAW) {
            this.mUserSelectionHelper.onTouchMove(evt, x, y);
        }
    }

    @Override
    public void onTouchUp(MotionEvent evt, int x, int y) {
        // Finish user selection if we're still drawing.
        if (this.mCurrentState == ActivityState.DRAW) {
            this.mUserSelectionHelper.onTouchUp(evt, x, y);
            this.mUserSelection = this.mUserSelectionHelper.getSelection();

            // Make sure that the user selection actually has an area.
            if (Math.min(this.mUserSelection.width, this.mUserSelection.height) == 0) {
                this.mCurrentState = ActivityState.IDLE;
                return;
            }

            // Sample accumulator might contain samples from an earlier measurement.
            // Needs to be cleared.
            this.mMeasureSampleAccumulator.clear();
            this.mCurrentState = ActivityState.MEASURE;
        }
    }

    private enum ActivityState {

        IDLE,
        DRAW,
        MEASURE

    }

}
