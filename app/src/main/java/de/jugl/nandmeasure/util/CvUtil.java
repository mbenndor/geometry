package de.jugl.nandmeasure.util;

import org.opencv.core.*;

public class CvUtil {

    private CvUtil() {}

    /**
     * Translates a point by another offset point. Can be used to convert coordinates from a submatrix
     * into its parent matrix.
     *
     * @param offset Offset point
     * @param point Point to adjust
     */
    public static void adjustForOffset(Point offset, Point point) {
        point.x += offset.x;
        point.y += offset.y;
    }

    /**
     * Converts a point matrix into a point matrix with floating point numbers.
     *
     * @param contour Point matrix
     * @return Point matrix with floating point numbers
     */
    public static MatOfPoint2f toMatOfPoint2f(MatOfPoint contour) {
        return new MatOfPoint2f(contour.toArray());
    }

    /**
     * Calculates the length of the major axis of an ellipse.
     *
     * @param rect Ellipse
     * @return Length of the major axis of the ellipse
     */
    public static double getDiameterFromEllipse(RotatedRect rect) {
        return Math.max(rect.size.width, rect.size.height);
    }

    /**
     * Calculates the euclidean Distance between two points.
     *
     * @param p1 First point
     * @param p2 Second point
     * @return Euclidean distance between the two points
     */
    public static double euclid(Point p1, Point p2) {
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;

        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Black in RGB.
     */
    public static final Scalar RGB_BLACK = rgb(0, 0, 0);

    /**
     * White in RGB.
     */
    public static final Scalar RGB_WHITE = rgb(255, 255, 255);

    /**
     * Red in RGB.
     */
    public static final Scalar RGB_RED = rgb(255, 0, 0);

    /**
     * Green in RGB.
     */
    public static final Scalar RGB_GREEN = rgb(0, 255, 0);

    /**
     * Blue in RGB.
     */
    public static final Scalar RGB_BLUE = rgb(0, 0, 255);

    /**
     * Creates a new RGB scalar.
     *
     * @param r Red value
     * @param g Green value
     * @param b Blue value
     * @return RGB scalar
     */
    public static Scalar rgb(int r, int g, int b) {
        return new Scalar(r, g, b);
    }

}
