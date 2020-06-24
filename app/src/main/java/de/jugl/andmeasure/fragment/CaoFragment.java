package de.jugl.andmeasure.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import de.jugl.andmeasure.AppConstants;
import de.jugl.andmeasure.CalibrationProfile;
import de.jugl.andmeasure.R;
import de.jugl.andmeasure.activity.CaoActivity;
import de.jugl.andmeasure.activity.MainActivity;

public class CaoFragment extends Fragment implements View.OnClickListener, AdapterView.OnItemSelectedListener {

    /**
     * Spinner for calibration profiles.
     */
    private Spinner mSpCalibrationProfiles;

    /**
     * Input fields for marker distance, marker name and marker radius.
     */
    private EditText mEtMarkerDistance, mEtMarkerName, mEtMarkerRadius;

    /**
     * Button to start calibration and measurement.
     */
    private Button mBtnCalibrate, mBtnStart;

    /**
     * Text field to show the most recent distance measurement result.
     */
    private TextView mTvDistance;

    /**
     * Calibration profile container.
     */
    private LinearLayout mLlCalibrationProfileContainer;

    /**
     * Text fields for calibration profile details.
     */
    private TextView mTvCalibrationProfileDistance, mTvCalibrationProfileRadius;

    /**
     * Reference to main activity.
     */
    private MainActivity mActivity;

    /**
     * Application preferences.
     */
    private SharedPreferences mPrefs;

    /**
     * Calibration profile spinner adapter.
     */
    private CalibrationProfileAdapter mAdapter;

    public CaoFragment() { }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_cao, container, false);

        this.mEtMarkerDistance = v.findViewById(R.id.et_cao_marker_distance);
        this.mEtMarkerName = v.findViewById(R.id.et_cao_marker_name);
        this.mEtMarkerRadius = v.findViewById(R.id.et_cao_marker_radius);

        this.mBtnCalibrate = v.findViewById(R.id.btn_cao_calibrate);
        this.mBtnStart = v.findViewById(R.id.btn_cao_start);

        this.mBtnCalibrate.setOnClickListener(this);
        this.mBtnStart.setOnClickListener(this);

        this.mTvDistance = v.findViewById(R.id.tv_cao_distance);

        this.mSpCalibrationProfiles = v.findViewById(R.id.sp_cao_calibration_profiles);
        this.mSpCalibrationProfiles.setOnItemSelectedListener(this);

        this.mLlCalibrationProfileContainer = v.findViewById(R.id.ll_cao_calibration_profile_container);
        this.mTvCalibrationProfileDistance = v.findViewById(R.id.tv_cao_profile_distance);
        this.mTvCalibrationProfileRadius = v.findViewById(R.id.tv_cao_profile_radius);

        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        this.mActivity = (MainActivity) this.getActivity();
        this.mPrefs = this.mActivity.getPreferences(Activity.MODE_PRIVATE);

        this.inflateCalibrationProfileSpinner();
    }

    /**
     * Reads the calibration profiles from the application preferences and inflates the spinner.
     */
    private void inflateCalibrationProfileSpinner() {
        int profileCount = this.mPrefs.getInt(AppConstants.KEY_CAO_CALIBRATION_PROFILE_COUNT, 0);

        // Hide spinner if no profiles are found.
        if (profileCount == 0) {
            this.mLlCalibrationProfileContainer.setVisibility(View.GONE);
            return;
        } else {
            this.mLlCalibrationProfileContainer.setVisibility(View.VISIBLE);
        }

        CalibrationProfile[] profiles = new CalibrationProfile[profileCount];

        String profileName;
        double profileDistance, profilePixelRadius, profileRadius;

        // Get properties of every profile.
        for (int i = 0; i < profileCount; i++) {
            profileName = this.mPrefs.getString(AppConstants.getNameKeyForProfile(i), null);
            profileDistance = this.mActivity.getDouble(this.mPrefs, AppConstants.getDistanceKeyForProfile(i), -1d);
            profileRadius = this.mActivity.getDouble(this.mPrefs, AppConstants.getRadiusKeyForProfile(i), -1d);
            profilePixelRadius = this.mActivity.getDouble(this.mPrefs, AppConstants.getPixelRadiusKeyForProfile(i), -1d);

            profiles[i] = new CalibrationProfile(profileName, profileDistance, profileRadius, profilePixelRadius);
        }

        this.mAdapter = new CalibrationProfileAdapter(this.mActivity, profiles);
        this.mSpCalibrationProfiles.setAdapter(this.mAdapter);
    }

    /**
     * Handles calibration results.
     *
     * @param data Return values of the calibration activity
     */
    private void handleCalibrationResult(Intent data) {
        SharedPreferences.Editor editor = this.mPrefs.edit();
        CalibrationProfile profile = data.getParcelableExtra(CaoActivity.EXTRA_CALIBRATION_PROFILE);

        int markerId = this.mPrefs.getInt(AppConstants.KEY_CAO_CALIBRATION_PROFILE_COUNT, 0);

        // Increment profile counter.
        editor.putInt(AppConstants.KEY_CAO_CALIBRATION_PROFILE_COUNT, markerId + 1);

        // Save profile to preferences.
        editor.putString(AppConstants.getNameKeyForProfile(markerId), profile.getName());
        this.mActivity.putDouble(editor, AppConstants.getDistanceKeyForProfile(markerId), profile.getDistance());
        this.mActivity.putDouble(editor, AppConstants.getRadiusKeyForProfile(markerId), profile.getRadius());
        this.mActivity.putDouble(editor, AppConstants.getPixelRadiusKeyForProfile(markerId), profile.getPixelRadius());

        // Write preferences now so they can be used to inflate the spinner.
        editor.commit();

        this.inflateCalibrationProfileSpinner();
    }

    /**
     * Handles measurement results.
     *
     * @param data Return values of the measurement activity
     */
    private void handleMeasurementResult(Intent data) {
        double distance = data.getDoubleExtra(CaoActivity.EXTRA_DISTANCE, -1f);
        this.mTvDistance.setText(this.getResources().getString(R.string.data_value_distance, distance));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        if (requestCode == CaoActivity.REQUEST_CALIBRATION) {
            this.handleCalibrationResult(data);
        } else {
            this.handleMeasurementResult(data);
        }
    }

    /**
     * Starts the calibration activity.
     */
    private void startCalibrationActivity() {
        Intent i = new Intent(getContext(), CaoActivity.class);

        String profileName = this.mEtMarkerName.getText().toString().trim();
        double profileDistance = this.mActivity.getDoubleInput(this.mEtMarkerDistance);
        double profileRadius = this.mActivity.getDoubleInput(this.mEtMarkerRadius);

        CalibrationProfile profileStub = new CalibrationProfile(profileName, profileDistance, profileRadius);

        i.putExtra(CaoActivity.EXTRA_REQUEST_CODE, CaoActivity.REQUEST_CALIBRATION);
        i.putExtra(CaoActivity.EXTRA_CALIBRATION_PROFILE, profileStub);

        this.startActivityForResult(i, CaoActivity.REQUEST_CALIBRATION);
    }

    /**
     * Starts the measurement activity.
     */
    private void startMeasureActivity() {
        // Focal size is inferred from the calibration profile.
        CalibrationProfile profile = this.mAdapter.getItem(this.mSpCalibrationProfiles.getSelectedItemPosition());
        Intent i = new Intent(getContext(), CaoActivity.class);

        i.putExtra(CaoActivity.EXTRA_REQUEST_CODE, CaoActivity.REQUEST_MEASUREMENT);
        i.putExtra(CaoActivity.EXTRA_CALIBRATION_PROFILE, profile);

        this.startActivityForResult(i, CaoActivity.REQUEST_MEASUREMENT);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == this.mBtnCalibrate.getId()) {
            // Check if marker name is empty.
            if (this.mEtMarkerName.getText().toString().trim().length() == 0) {
                this.mEtMarkerName.setError(getString(R.string.error_input_string_empty));
                return;
            }

            // Validate double inputs.
            if (!this.mActivity.validateDoubleInput(this.mEtMarkerDistance) || !this.mActivity.validateDoubleInput(this.mEtMarkerRadius)) {
                return;
            }

            // Check if field of view has already been calculated.
            if (!this.mPrefs.getBoolean(AppConstants.KEY_FOV_CALIBRATED, false)) {
                Toast.makeText(getContext(), R.string.error_fov_not_calibrated, Toast.LENGTH_LONG).show();
                return;
            }

            this.startCalibrationActivity();
        } else if (v.getId() == this.mBtnStart.getId()) {
            // Check if there's at least one calibration profile.
            if (this.mPrefs.getInt(AppConstants.KEY_CAO_CALIBRATION_PROFILE_COUNT, 0) == 0) {
                Toast.makeText(getContext(), R.string.error_cao_not_calibrated, Toast.LENGTH_LONG).show();
                return;
            }

            this.startMeasureActivity();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        CalibrationProfile profile = this.mAdapter.getItem(position);

        this.mTvCalibrationProfileRadius.setText(this.getResources().getString(R.string.data_value_distance, profile.getRadius()));
        this.mTvCalibrationProfileDistance.setText(this.getResources().getString(R.string.data_value_distance, profile.getDistance()));
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    private static class CalibrationProfileAdapter extends ArrayAdapter<CalibrationProfile> {

        CalibrationProfileAdapter(Context context, CalibrationProfile[] objects) {
            super(context, android.R.layout.simple_spinner_item, objects);
            this.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = super.getView(position, convertView, parent);
            v.setPadding(0, v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom());

            return v;
        }
    }

}
