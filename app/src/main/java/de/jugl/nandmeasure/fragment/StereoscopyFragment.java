package de.jugl.nandmeasure.fragment;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import de.jugl.nandmeasure.AppConstants;
import de.jugl.nandmeasure.R;
import de.jugl.nandmeasure.activity.MainActivity;
import de.jugl.nandmeasure.activity.StereoscopyActivity;

public class StereoscopyFragment extends Fragment implements View.OnClickListener {

    /**
     * Button to start the measurement activity.
     */
    private Button mBtnStart;

    /**
     * Input field for distance between camera locations.
     */
    private EditText mEtDistance;

    /**
     * Text fields to output the most recently calculated distances.
     */
    private TextView mTvDistance, mTvDistanceCorrected;

    /**
     * Switch to (de-)activate marker correction.
     */
    private Switch mSwIgnoreCorrection;

    /**
     * Reference to main activity.
     */
    private MainActivity mActivity;

    /**
     * Application preferences.
     */
    private SharedPreferences mPrefs;

    public StereoscopyFragment() { }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_stereoscopy, container, false);

        this.mBtnStart = v.findViewById(R.id.btn_stereo_start);
        this.mBtnStart.setOnClickListener(this);

        this.mEtDistance = v.findViewById(R.id.et_stereo_distance);
        this.mTvDistance = v.findViewById(R.id.tv_stereo_distance);
        this.mTvDistanceCorrected = v.findViewById(R.id.tv_stereo_distance_corrected);

        this.mSwIgnoreCorrection = v.findViewById(R.id.sw_stereo_ignore_correction);

        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        this.mActivity = (MainActivity) this.getActivity();
        this.mPrefs = this.mActivity.getPreferences(Activity.MODE_PRIVATE);
    }

    /**
     * Starts the measurement activity.
     */
    private void launchStereoscopyActivity() {
        double horizontalFov = this.mActivity.getDouble(this.mPrefs, AppConstants.KEY_HORIZONTAL_FOV, -1d);
        double camDistance = this.mActivity.getDoubleInput(this.mEtDistance);

        Intent i = new Intent(getContext(), StereoscopyActivity.class);

        i.putExtra(StereoscopyActivity.EXTRA_CAMERA_DISTANCE, camDistance);
        i.putExtra(StereoscopyActivity.EXTRA_HORIZONTAL_FOV, horizontalFov);
        i.putExtra(StereoscopyActivity.EXTRA_IGNORE_CORRECTION, this.mSwIgnoreCorrection.isChecked());

        this.startActivityForResult(i, StereoscopyActivity.REQUEST_DISTANCE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }

        if (requestCode == StereoscopyActivity.REQUEST_DISTANCE) {
            double distance = data.getDoubleExtra(StereoscopyActivity.EXTRA_DISTANCE, -1d);
            double correctedDistance = data.getDoubleExtra(StereoscopyActivity.EXTRA_CORRECTED_DISTANCE, -1d);

            this.mTvDistance.setText(this.getResources().getString(R.string.data_value_distance, distance));
            this.mTvDistanceCorrected.setText(this.getResources().getString(R.string.data_value_distance, correctedDistance));
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == this.mBtnStart.getId()) {
            if (!this.mPrefs.getBoolean(AppConstants.KEY_FOV_CALIBRATED, false)) {
                Toast.makeText(getContext(), R.string.error_fov_not_calibrated, Toast.LENGTH_LONG).show();
                return;
            }

            if (!this.mActivity.validateDoubleInput(this.mEtDistance)) {
                return;
            }

            this.launchStereoscopyActivity();
        }
    }

}
