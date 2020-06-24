package de.jugl.andmeasure.util;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MatProcessor {

    /**
     * Parameter for the size of the blur kernel.
     */
    public static final String PARAM_BLUR_KERNEL_LENGTH = "blurKernel";

    /**
     * Parameter for the blur filter to be used.
     */
    public static final String PARAM_BLUR_FILTER = "blurFilter";

    /**
     * Parameter to adjust image brightness.
     */
    public static final String PARAM_BRIGHTNESS = "brightness";

    /**
     * Parameter to adjust image contrast.
     */
    public static final String PARAM_CONTRAST = "contrast";

    /**
     * Parameter for the sigma value of the bilateral filter.
     */
    public static final String PARAM_BILATERAL_SIGMA = "bilateralSigma";

    /**
     * Parameter for the maximum value after thresholding.
     */
    public static final String PARAM_THRESH_MAXVAL = "threshMaxval";

    /**
     * Parameter for the maximum deviation of the aspect ratio of a circle's bounding box from the ideal
     * aspect ratio of 1.
     */
    public static final String PARAM_CIRCLE_ASPECT_THRESHOLD = "circleAspect";

    /**
     * Parameter for the minimum contour size.
     */
    public static final String PARAM_MIN_CONTOUR_AREA = "minContourArea";

    /**
     * Minimum amount of points in a contour for {@link Imgproc#fitEllipse(MatOfPoint2f)} to work.
     */
    private static final int MIN_ELLIPSE_FIT_POINT_COUNT = 5;

    /**
     * Mapping of parameters to their default values.
     */
    private static Map<String, Object> paramDefaults;

    static {
        paramDefaults = new HashMap<>();
        setDefaults();
    }

    /**
     * Sets the default value of every parameter to be applied to every instance of this class.
     */
    private static void setDefaults() {
        paramDefaults.put(PARAM_BILATERAL_SIGMA, 50f);
        paramDefaults.put(PARAM_BLUR_KERNEL_LENGTH, 5);
        paramDefaults.put(PARAM_BRIGHTNESS, 0f);
        paramDefaults.put(PARAM_CONTRAST, 1f);
        paramDefaults.put(PARAM_THRESH_MAXVAL, 255);
        paramDefaults.put(PARAM_BLUR_FILTER, FilterType.GAUSSIAN);
        paramDefaults.put(PARAM_CIRCLE_ASPECT_THRESHOLD, 0.1f);
        paramDefaults.put(PARAM_MIN_CONTOUR_AREA, 50);
    }

    /**
     * @param key Image processing parameter
     * @return Default value of the given parameter
     */
    public static Object getDefault(String key) {
        return paramDefaults.get(key);
    }

    /**
     * Mapping of parameters to their set values.
     */
    private Map<String, Object> mParams;

    /**
     * Size of the blur kernel.
     */
    private Size mBlurKernelSize;

    /**
     * Creates a new image processor instance and sets all parameters to their default values.
     */
    public MatProcessor() {
        this.mParams = new HashMap<>(paramDefaults);
        this.mBlurKernelSize = new Size();

        this.updateBlurKernelSize();
    }

    /**
     * @param key Image processing parameter
     * @return Current value of the given parameter
     */
    public Object get(String key) {
        return this.mParams.get(key);
    }

    /**
     * @param key Image processing parameter
     * @param newVal New value of the given parameter
     */
    public void set(String key, Object newVal) {
        Object oldVal = this.get(key);
        this.mParams.put(key, newVal);

        // Check if the value changed.
        if (!oldVal.equals(newVal)) {
            this.notifyParamUpdate(key);
        }
    }

    /**
     * Updates fields associated with certain parameters, if needed.
     *
     * @param key Parameter that had its value changed
     */
    private void notifyParamUpdate(String key) {
        switch (key) {
            case PARAM_BLUR_KERNEL_LENGTH: {
                this.updateBlurKernelSize();
            } break;
        }
    }

    /**
     * Adjusts the size of the blur kernel.
     */
    private void updateBlurKernelSize() {
        int kernelLen = (int) this.get(PARAM_BLUR_KERNEL_LENGTH);

        if (kernelLen <= 0) {
            return;
        }

        this.mBlurKernelSize.set(new double[] { kernelLen, kernelLen });
    }

    /**
     * Prepares a grayscale image for contour recognition.
     *
     * @param grayMat Grayscale image to process
     */
    public void preprocess(Mat grayMat) {
        int rows = grayMat.rows(),
                cols = grayMat.cols(),
                type = grayMat.type();

        // Adjust brightness and contrast. -1 denotes that the output matrix has the same attributes
        // as the input matrix.
        grayMat.convertTo(grayMat, -1, (float) this.get(PARAM_CONTRAST), (float) this.get(PARAM_BRIGHTNESS));

        int blurKernelLen = (int) this.get(PARAM_BLUR_KERNEL_LENGTH);

        if (blurKernelLen > 0) {
            FilterType filter = (FilterType) this.get(PARAM_BLUR_FILTER);

            if (filter == FilterType.BILATERAL) {
                // Bilateral filter does not work in-place. We need an extra matrix for that.
                Mat blurMat = new Mat(rows, cols, type);
                double blurBilateralSigma = (double) this.get(PARAM_BILATERAL_SIGMA);

                // sigmaColor and sigmaSpace stem from the OpenCV documentation. The bilateral filter
                // takes a lot of time to finish and should work well enough with the default values.
                Imgproc.bilateralFilter(grayMat, blurMat, blurKernelLen, blurBilateralSigma, blurBilateralSigma);

                // Copy the blurred image into our grayscale image.
                blurMat.copyTo(grayMat);
                blurMat.release();
            } else if (filter == FilterType.GAUSSIAN) {
                Imgproc.GaussianBlur(grayMat, grayMat, this.mBlurKernelSize, 0);
            } else if (filter == FilterType.BOX) {
                Imgproc.blur(grayMat, grayMat, this.mBlurKernelSize);
            }
        }

        int threshMax = (int) this.get(PARAM_THRESH_MAXVAL);

        Core.normalize(grayMat, grayMat, 0d, 255d, Core.NORM_MINMAX);
        Imgproc.threshold(grayMat, grayMat, 0, threshMax, Imgproc.THRESH_OTSU);
    }

    /**
     * Finds contours in a binarized image. Only the points which approximate the contours are saved. The hierarchical
     * relationships between the contours are discarded.
     *
     * @param contours List of contours
     * @param grayMat Binarized image
     */
    public void findContours(List<MatOfPoint> contours, Mat grayMat) {
        Mat hierarchyMat = new Mat();
        Imgproc.findContours(grayMat, contours, hierarchyMat, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        hierarchyMat.release();

        int minContourArea = (int) this.get(PARAM_MIN_CONTOUR_AREA);

        for (Iterator<MatOfPoint> it = contours.iterator(); it.hasNext(); ) {
            if (Imgproc.contourArea(it.next(), true) < minContourArea) {
                it.remove();
            }
        }
    }

    /**
     * Finds contours in a binarized image using {@link #findContours(List, Mat)}. Only keeps contours whose
     * bounding boxes are approximately square. The maximum deviation can be set using {@link #PARAM_CIRCLE_ASPECT_THRESHOLD}.
     *
     * @param contours List of contours
     * @param grayMat Binarized image
     */
    public void findCircleContours(List<MatOfPoint> contours, Mat grayMat) {
        this.findContours(contours, grayMat);

        Rect boundingBox;
        float aspect, aspectThreshold = (float) this.get(PARAM_CIRCLE_ASPECT_THRESHOLD);

        for (Iterator<MatOfPoint> it = contours.iterator(); it.hasNext(); ) {
            boundingBox = Imgproc.boundingRect(it.next());
            aspect = (float) boundingBox.width / boundingBox.height;

            if (Math.abs(aspect - 1f) > aspectThreshold) {
                it.remove();
            }
        }
    }

    /**
     * Finds contours in a binarized image using {@link #findContours(List, Mat)}. Only keeps contours with
     * at least five points for {@link Imgproc#fitEllipse(MatOfPoint2f)}.
     *
     * @param contours List of contours
     * @param grayMat Binarized image
     */
    public void findContoursForEllipseFit(List<MatOfPoint> contours, Mat grayMat) {
        this.findContours(contours, grayMat);

        for (Iterator<MatOfPoint> it = contours.iterator(); it.hasNext(); ) {
            if (it.next().total() < MIN_ELLIPSE_FIT_POINT_COUNT) {
                it.remove();
            }
        }
    }

    public enum FilterType {

        BILATERAL,

        GAUSSIAN,

        BOX

    }

}
