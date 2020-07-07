package de.jugl.nandmeasure.fragment;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import de.jugl.nandmeasure.AppConstants;
import de.jugl.nandmeasure.R;
import de.jugl.nandmeasure.activity.FovActivity;
import de.jugl.nandmeasure.activity.MainActivity;

public class CalibrationFragment extends Fragment implements View.OnClickListener, MainActivity.PermissionCallbackListener {

    /**
     * Input field for marker radius.
     */
    private EditText mEditMarkerRadius;

    /**
     * Input field for marker distance.
     */
    private EditText mEditMarkerDistance;

    /**
     * Output field for horizontal FOV.
     */
    private TextView mTextViewCalibrationHorizontalFov;

    /**
     * Button that starts the calibration activity.
     */
    private Button mBtnStart;

    /**
     * Reference to main activity.
     */
    private MainActivity mActivity;

    /**
     * Application preferences.
     */
    private SharedPreferences mPrefs;

    public CalibrationFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_calibration, container, false);

        this.mEditMarkerDistance = v.findViewById(R.id.et_calibration_distance);
        this.mEditMarkerRadius = v.findViewById(R.id.et_calibration_radius);

        this.mBtnStart = v.findViewById(R.id.btn_calibration_start);
        this.mBtnStart.setOnClickListener(this);

        this.mTextViewCalibrationHorizontalFov = v.findViewById(R.id.tv_calibration_horizontal_fov);

        return v;
    }

    /**
     * Formats the camera's FOV and sets the corresponding output field.
     *
     * @param horizontalFov Horizontal FOV in radians
     */
    private void setFovTextViews(double horizontalFov) {
        this.mTextViewCalibrationHorizontalFov.setText(this.getResources().getString(R.string.data_value_angle, Math.toDegrees(horizontalFov)));
    }

    /**
     * Starts the FOV calibration activity.
     */
    private void launchCalibrationActivity() {
        Intent i = new Intent(getContext(), FovActivity.class);

        i.putExtra(FovActivity.EXTRA_MARKER_RADIUS, this.mActivity.getDoubleInput(this.mEditMarkerRadius));
        i.putExtra(FovActivity.EXTRA_MARKER_DISTANCE, this.mActivity.getDoubleInput(this.mEditMarkerDistance));

        this.startActivityForResult(i, FovActivity.REQUEST_FOV);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        this.mActivity = (MainActivity) this.getActivity();
        this.mPrefs = this.mActivity.getPreferences(Activity.MODE_PRIVATE);

        // If the camera's FOV has already been calculated, update the UI.
        if (this.mPrefs.getBoolean(AppConstants.KEY_FOV_CALIBRATED, false)) {
            double hFov = this.mActivity.getDouble(this.mPrefs, AppConstants.KEY_HORIZONTAL_FOV, -1);
            this.setFovTextViews(hFov);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        if (requestCode == FovActivity.REQUEST_FOV) {
            double horizontalFov = data.getDoubleExtra(FovActivity.EXTRA_FOV_HORIZONTAL, -1f);

            // Save and show the calculated FOV.
            SharedPreferences.Editor editor = this.mPrefs.edit();
            editor.putBoolean(AppConstants.KEY_FOV_CALIBRATED, true);
            this.mActivity.putDouble(editor, AppConstants.KEY_HORIZONTAL_FOV, horizontalFov);

            editor.apply();

            this.setFovTextViews(horizontalFov);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == this.mBtnStart.getId()) {
            // Validate user inputs.
            if (!this.mActivity.validateDoubleInput(this.mEditMarkerRadius) || !this.mActivity.validateDoubleInput(this.mEditMarkerDistance)) {
                return;
            }

            // Do we have the camera permission?
            if (ContextCompat.checkSelfPermission(this.mActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                // If not, request permission and wait for callback.
                this.mActivity.setPermissionCallbackListener(this);
                ActivityCompat.requestPermissions(this.mActivity, new String[] {
                        Manifest.permission.CAMERA
                }, MainActivity.CODE_REQUEST_CAMERA_PERM);
            } else {
                this.launchCalibrationActivity();
            }
        }
    }

    @Override
    public void onPermissionCallback(int code, boolean granted) {
        if (code == MainActivity.CODE_REQUEST_CAMERA_PERM && granted) {
            this.launchCalibrationActivity();
        }
    }

}
