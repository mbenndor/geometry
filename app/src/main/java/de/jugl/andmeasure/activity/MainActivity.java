package de.jugl.andmeasure.activity;

import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.widget.EditText;

import de.jugl.andmeasure.R;
import de.jugl.andmeasure.fragment.CalibrationFragment;
import de.jugl.andmeasure.fragment.CaoFragment;
import de.jugl.andmeasure.fragment.StereoscopyFragment;

public class MainActivity extends AppCompatActivity {

    /**
     * Request code for camera permission.
     */
    public static final int CODE_REQUEST_CAMERA_PERM = 0;

    /**
     * Permission callback listener.
     */
    private PermissionCallbackListener mPcl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewPager mViewPager = this.findViewById(R.id.main_pager);
        mViewPager.setAdapter(new CvPagerAdapter(getSupportFragmentManager()));
    }

    /**
     * Binds a permission callback listener to this activity. The listener will receive the permission request
     * code and whether the request was successful or not.
     *
     * @param pcl Permission callback listener
     */
    public void setPermissionCallbackListener(PermissionCallbackListener pcl) {
        this.mPcl = pcl;
    }

    private class CvPagerAdapter extends FragmentPagerAdapter {

        private Fragment[] mFragments = new Fragment[] {
                new CalibrationFragment(),
                new CaoFragment(),
                new StereoscopyFragment()
        };

        private String[] mPageTitles = new String[] {
                getString(R.string.calibration_title),
                getString(R.string.cao_title),
                getString(R.string.stereo_title)
        };

        public CvPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return this.mPageTitles[position];
        }

        @Override
        public Fragment getItem(int i) {
            return this.mFragments[i];
        }

        @Override
        public int getCount() {
            return this.mFragments.length;
        }

    }

    /**
     * Validates an {@link EditText} and checks the following conditions.
     *
     * <ul>
     *     <li>Input may not be empty</li>
     *     <li>Input must be a floating point number</li>
     *     <li>Input must be positive</li>
     * </ul>
     *
     * @param et {@link EditText} to validate
     * @return <code>true</code> if all above conditions are met, <code>false</code> otherwise
     */
    public boolean validateDoubleInput(EditText et) {
        // Checks for empty inputs.
        if (et.getText().length() == 0) {
            et.setError(getString(R.string.error_input_empty));
            return false;
        }

        // Tries to parse the input into a double.
        double d;

        try {
            d = this.getDoubleInput(et);
        } catch (NumberFormatException e) {
            et.setError(getString(R.string.error_input_nan));
            return false;
        }

        // Checks if input is positive.
        if (Math.signum(d) != 1) {
            et.setError(getString(R.string.error_input_zero));
            return false;
        }

        return true;
    }

    /**
     * Returns the input of a {@link EditText} as a floating point number with double precision. The field
     * should be validated with {@link #validateDoubleInput(EditText)}.
     *
     * @param et Input field
     * @return Parsed floating point number
     */
    public double getDoubleInput(EditText et) {
        return Double.parseDouble(et.getText().toString().trim());
    }

    /**
     * Stores a 64-bit floating point number in {@link SharedPreferences}. The number is converted into
     * its bit representation.
     *
     * @param editor {@link SharedPreferences.Editor} used for saving the number
     * @param key Key to store the number at
     * @param d Number to store
     */
    public void putDouble(SharedPreferences.Editor editor, String key, double d) {
        editor.putLong(key, Double.doubleToRawLongBits(d));
    }

    /**
     * Returns a 64-bit floating point number stored in {@link SharedPreferences}. It is parsed from
     * its bit representation.
     *
     * @param prefs {@link SharedPreferences} to read from
     * @param key Key where the number is stored
     * @param def Default value if no such key exists
     * @return Stored number, or default if it doesn't exist.
     */
    public double getDouble(SharedPreferences prefs, String key, double def) {
        if (!prefs.contains(key)) {
            return def;
        }

        return Double.longBitsToDouble(prefs.getLong(key, 0));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (this.mPcl != null) {
            // Check if all permissions were checked.
            if (grantResults.length == 0 || grantResults.length != permissions.length) {
                this.mPcl.onPermissionCallback(requestCode, false);
                return;
            }

            // Check if all permissions were granted.
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    this.mPcl.onPermissionCallback(requestCode, false);
                    return;
                }
            }

            this.mPcl.onPermissionCallback(requestCode, true);
            return;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public interface PermissionCallbackListener {

        void onPermissionCallback(int code, boolean granted);

    }

}
