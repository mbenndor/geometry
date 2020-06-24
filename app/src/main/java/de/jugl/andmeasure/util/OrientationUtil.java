package de.jugl.andmeasure.util;

import android.hardware.SensorManager;
import android.util.Log;

public class OrientationUtil {

    private OrientationUtil() {}

    private static final float[] ROTATION_MAT = new float[9];

    /**
     * Converts a unit quaternion into Euler angles.
     *
     * @param quaternion Unit quaternion of shape <code>[x, y, z, w]</code>
     * @param orientation Array to write the angles in radians to
     */
    public static void convertQuaternionToEuler(float[] quaternion, float[] orientation) {
        SensorManager.getRotationMatrixFromVector(ROTATION_MAT, quaternion);
        SensorManager.getOrientation(ROTATION_MAT, orientation);
    }

    /**
     * Android's orientation sensor provides angles in radians within the range of [-&#960;, &#960;]. Angles
     * aren't measured beyond that range. This class accounts for full rotations around the device's axes
     * and returns an "absolute" angle.
     */
    public static class ContinuousAngleWrapper {

        private static final String TAG = "AngleWrapper";

        /**
         * Precalculated 2&#960;.
         */
        private static final float PI_2 = (float) (Math.PI * 2);

        /**
         * Minimum deviation between two consecutive angles where we need to account for the limited range
         * of Android's sensors.
         */
        private static final float REVOLUTION_THRESHOLD = 1f;

        /**
         * <code>true</code> if the class was initialized with an initial value, <code>false</code> otherwise.
         */
        private boolean mInitialized = false;

        /**
         * Last measured orientation.
         */
        private float mLastValue = 0f;

        /**
         * Count of full rotations.
         */
        private int mRevolutions = 0;

        /**
         * Saves the measured orientation and accounts for the limited range of Android's sensors.
         *
         * @param newValue Measured angle
         */
        public void update(float newValue) {
            // Initialize if we have no angle to refer to yet.
            if (!this.mInitialized) {
                this.mLastValue = newValue;
                this.mInitialized = true;
                this.mRevolutions = 0;
                return;
            }

            // Did the sign change between the last and the current angle?
            if (Math.signum(this.mLastValue) != Math.signum(newValue)) {
                float delta = newValue - this.mLastValue;

                // Are the two angles sufficiently apart from one another?
                if (Math.abs(delta) >= REVOLUTION_THRESHOLD) {
                    // If the delta between the two angles is negative, then the "absolute" angle
                    // would keep growing. The same works vice versa. This is why we need to invert
                    // the sign of the delta to adjust our rotation count.
                    this.mRevolutions -= (int) Math.signum(delta);
                    Log.d(TAG, "Sign changed. Revolutions: " + this.mRevolutions);
                }
            }

            this.mLastValue = newValue;
        }

        /**
         * Returns the "absolute" angle accounting for rotations around the device's axes.
         *
         * @return Absolute angle in radians
         */
        public float getFullAngle() {
            return PI_2 * this.mRevolutions + this.mLastValue;
        }

    }

}
