package de.jugl.nandmeasure.util;

import android.view.MotionEvent;

import de.jugl.nandmeasure.activity.BaseCvCameraActivity;
import org.opencv.core.Point;
import org.opencv.core.Rect;

public class UserSelectionHelper implements BaseCvCameraActivity.CvMatTouchListener {

    /**
     * User selection.
     */
    private Rect mUserSelection;

    /**
     * Corners defining the user selection.
     */
    private Point mUserSelectionP1, mUserSelectionP2;

    public UserSelectionHelper() {
        this.mUserSelection = new Rect();
        this.mUserSelectionP1 = new Point();
        this.mUserSelectionP2 = new Point();
    }

    @Override
    public void onTouchDown(MotionEvent evt, int x, int y) {
        double[] vals = new double[] { x, y };

        this.mUserSelectionP1.set(vals);
        this.mUserSelectionP2.set(vals);
    }

    @Override
    public void onTouchMove(MotionEvent evt, int x, int y) {
        this.mUserSelectionP2.x = x;
        this.mUserSelectionP2.y = y;
    }

    @Override
    public void onTouchUp(MotionEvent evt, int x, int y) {
        this.onTouchMove(evt, x, y);

        this.mUserSelection = new Rect(this.mUserSelectionP1, this.mUserSelectionP2);
    }

    /**
     * @return First point defining the user selection
     */
    public Point getP1() {
        return this.mUserSelectionP1;
    }

    /**
     * @return Second point defining the user selection
     */
    public Point getP2() {
        return this.mUserSelectionP2;
    }

    /**
     * @return User selection
     */
    public Rect getSelection() {
        return this.mUserSelection;
    }

}
