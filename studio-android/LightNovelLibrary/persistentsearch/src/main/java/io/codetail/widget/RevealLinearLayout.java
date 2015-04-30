package io.codetail.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import io.codetail.animation.RevealAnimator;

public class RevealLinearLayout extends LinearLayout implements RevealAnimator{

    Path mRevealPath;

    boolean mClipOutlines;

    float mCenterX;
    float mCenterY;
    float mRadius;

    View mTarget;

    public RevealLinearLayout(Context context) {
        this(context, null);
    }

    public RevealLinearLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RevealLinearLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        mRevealPath = new Path();
    }

    /**
     * @hide
     */
    @Override
    public void setTarget(View view){
        mTarget = view;
    }

    /**
     * @hide
     */
    @Override
    public void setCenter(float centerX, float centerY){
        mCenterX = centerX;
        mCenterY = centerY;
    }

    /**
     * @hide
     */
    @Override
    public void setClipOutlines(boolean clip){
        mClipOutlines = clip;
    }

    /**
     * @hide
     */
    @Override
    public void setRevealRadius(float radius){
        mRadius = radius;
        invalidate();
    }

    /**
     * @hide
     */
    @Override
    public float getRevealRadius(){
        return mRadius;
    }


    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (!mClipOutlines && child != mTarget)
            return super.drawChild(canvas, child, drawingTime);

        final int state = canvas.save();

        mRevealPath.reset();
        mRevealPath.addCircle(mCenterX, mCenterY, mRadius, Path.Direction.CW);

        canvas.clipPath(mRevealPath);

        boolean isInvalided = super.drawChild(canvas, child, drawingTime);

        canvas.restoreToCount(state);

        return isInvalided;
    }
}
