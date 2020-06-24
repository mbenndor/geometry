package de.jugl.andmeasure;

import android.os.Parcel;
import android.os.Parcelable;

public class CalibrationProfile implements Parcelable {

    /**
     * Default value for {@link #getPixelRadius()} if the pixel radius hasn't been calculated yet.
     */
    public static final double UNDETERMINED = -1d;

    /**
     * Marker name.
     */
    private final String mName;

    /**
     * Distance to marker during calibration.
     */
    private final double mDistance;

    /**
     * Marker radius in pixels.
     */
    private double mPixelRadius;

    /**
     * Marker radius in centimeters.
     */
    private final double mRadius;

    public CalibrationProfile(String name, double distance, double radius) {
        this(name, distance, radius, UNDETERMINED);
    }

    public CalibrationProfile(String name, double distance, double radius, double pixelRadius) {
        this.mName = name;
        this.mDistance = distance;
        this.mRadius = radius;
        this.mPixelRadius = pixelRadius;
    }

    private CalibrationProfile(Parcel data) {
        // Parcel order: name, distance, radius, pixel radius.
        this.mName = data.readString();
        this.mDistance = data.readDouble();
        this.mRadius = data.readDouble();
        this.mPixelRadius = data.readDouble();
    }

    @Override
    public String toString() {
        return this.mName;
    }

    /**
     * @return Marker name
     */
    public String getName() {
        return mName;
    }

    /**
     * @return Distance to marker during calibration
     */
    public double getDistance() {
        return mDistance;
    }

    /**
     * @return Marker radius in pixels
     */
    public double getPixelRadius() {
        return mPixelRadius;
    }

    /**
     * Sets the marker radius in pixels.
     *
     * @param pixelRadius Marker radius in pixels
     * @return <code>true</code> if the marker radius hasn't been set prior to this call, <code>false</code> otherwise
     */
    public boolean setPixelRadius(double pixelRadius) {
        if (this.mPixelRadius == UNDETERMINED) {
            this.mPixelRadius = pixelRadius;
            return true;
        }

        return false;
    }

    /**
     * @return Marker radius in centimeters
     */
    public double getRadius() {
        return mRadius;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mName);
        dest.writeDouble(this.mDistance);
        dest.writeDouble(this.mRadius);
        dest.writeDouble(this.mPixelRadius);
    }

    public static final Parcelable.Creator<CalibrationProfile> CREATOR = new Parcelable.Creator<CalibrationProfile>() {

        @Override
        public CalibrationProfile createFromParcel(Parcel source) {
            return new CalibrationProfile(source);
        }

        @Override
        public CalibrationProfile[] newArray(int size) {
            return new CalibrationProfile[size];
        }

    };

}
