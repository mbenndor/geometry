package de.jugl.nandmeasure;

public class AppConstants {

    private AppConstants() {}

    /**
     * Preference key for the horizontal FOV.
     */
    public static final String KEY_HORIZONTAL_FOV = "horizontalFov";

    /**
     * Preference key pointing to a boolean that defines if the FOV has already been calculated or not.
     */
    public static final String KEY_FOV_CALIBRATED = "fovCalibrated";

    /**
     * Preference key for the amount of stored calibration profiles.
     */
    public static final String KEY_CAO_CALIBRATION_PROFILE_COUNT = "caoCalibCount";

    /**
     * @param id Calibration profile ID
     * @return Preference key for the marker radius in pixels
     */
    public static String getPixelRadiusKeyForProfile(int id) {
        return "caoCalib:" + id + ":pxradius";
    }

    /**
     * @param id Calibration profile ID
     * @return Preference key for the marker radius in centimeters
     */
    public static String getRadiusKeyForProfile(int id) {
        return "caoCalib:" + id + ":radius";
    }

    /**
     * @param id Calibration profile ID
     * @return Preference key for the marker name
     */
    public static String getNameKeyForProfile(int id) {
        return "caoCalib:" + id + ":name";
    }

    /**
     * @param id Calibration profile ID
     * @return Preference key for the distance to the marker during calibration
     */
    public static String getDistanceKeyForProfile(int id) {
        return "caoCalib:" + id + ":distance";
    }

}
