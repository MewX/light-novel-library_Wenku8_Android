package com.jpardogo.android.googleprogressbar.library;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.mewx.wenku8.R;

/**
 * FoldingCirclesDrawable
 * User: romainpiel
 * Date: 07/04/2014
 * Time: 13:00
 */
public class FoldingCirclesDrawable extends Drawable implements Drawable.Callback {

    private static final float MAX_LEVEL = 10000;
    private static final float CIRCLE_COUNT = ProgressStates.values().length;
    private static final float MAX_LEVEL_PER_CIRCLE = MAX_LEVEL / CIRCLE_COUNT;
    private static final int ALPHA_OPAQUE = 255;
    private static final int ALPHA_ABOVE_DEFAULT = 235;

    private Paint mFstHalfPaint;
    private Paint mScndHalfPaint;
    private Paint mAbovePaint;
    private RectF mOval = new RectF();
    private int mDiameter;
    private Path mPath;
    private int mHalf;
    private ProgressStates mCurrentState;
    private int mControlPointMinimum;
    private int mControlPointMaximum;
    private int mAxisValue;
    private int mAlpha = ALPHA_OPAQUE;
    private ColorFilter mColorFilter;
    private static int mColor1;
    private static int mColor2;
    private static int mColor3;
    private static int mColor4;
    private int fstColor, scndColor;
    private boolean goesBackward;

    private enum ProgressStates {
        FOLDING_DOWN,
        FOLDING_LEFT,
        FOLDING_UP,
        FOLDING_RIGHT
    }

    public FoldingCirclesDrawable(int[] colors) {
        initCirclesProgress(colors);
    }

    private void initCirclesProgress(int[] colors) {
        initColors(colors);
        mPath = new Path();

        Paint basePaint = new Paint();
        basePaint.setAntiAlias(true);

        mFstHalfPaint = new Paint(basePaint);
        mScndHalfPaint = new Paint(basePaint);
        mAbovePaint = new Paint(basePaint);

        // init alpha and color filter
        setAlpha(mAlpha);
        setColorFilter(mColorFilter);
    }

    private void initColors(int[] colors) {
        mColor1=colors[0];
        mColor2=colors[1];
        mColor3=colors[2];
        mColor4=colors[3];
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        measureCircleProgress(bounds.width(), bounds.height());
    }

    @Override
    protected boolean onLevelChange(int level) {

        // level goes from 0 to 10000 but the number of colors divides 10000
        // so we need to do that hack that maps level 10000 to level 0
        int animationLevel = level == MAX_LEVEL ? 0 : level;

        // state
        int stateForLevel = (int) (animationLevel / MAX_LEVEL_PER_CIRCLE);
        mCurrentState = ProgressStates.values()[stateForLevel];

        // colors
        resetColor(mCurrentState);
        int levelForCircle = (int) (animationLevel % MAX_LEVEL_PER_CIRCLE);

        boolean halfPassed;
        if (!goesBackward) {
            halfPassed = levelForCircle != (int) (animationLevel % (MAX_LEVEL_PER_CIRCLE / 2));
        } else {
            halfPassed = levelForCircle == (int) (animationLevel % (MAX_LEVEL_PER_CIRCLE / 2));
            levelForCircle = (int) (MAX_LEVEL_PER_CIRCLE - levelForCircle);
        }

        mFstHalfPaint.setColor(fstColor);
        mScndHalfPaint.setColor(scndColor);

        if (!halfPassed) {
            mAbovePaint.setColor(mScndHalfPaint.getColor());
        } else {
            mAbovePaint.setColor(mFstHalfPaint.getColor());
        }

        // invalidate alpha (Paint#setAlpha is a shortcut for setColor(alpha part)
        // so alpha is affected by setColor())
        setAlpha(mAlpha);

        // axis
        mAxisValue = (int) (mControlPointMinimum + (mControlPointMaximum - mControlPointMinimum) * (levelForCircle / MAX_LEVEL_PER_CIRCLE));

        return true;
    }

    private void resetColor(ProgressStates currentState) {
        switch (currentState){
            case FOLDING_DOWN:
                fstColor= mColor1;
                scndColor=mColor2;
                goesBackward=false;
            break;
            case FOLDING_LEFT:
                fstColor= mColor1;
                scndColor=mColor3;
                goesBackward=true;
                break;
            case FOLDING_UP:
                fstColor= mColor3;
                scndColor=mColor4;
                goesBackward=true;
                break;
            case FOLDING_RIGHT:
                fstColor=mColor2;
                scndColor=mColor4;
                goesBackward=false;
                break;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mCurrentState != null) {
            makeCirclesProgress(canvas);
        }
    }

    private void measureCircleProgress(int width, int height) {
        mDiameter = Math.min(width, height);
        mHalf = mDiameter / 2;
        mOval.set(0, 0, mDiameter, mDiameter);
        mControlPointMinimum = -mDiameter / 6;
        mControlPointMaximum = mDiameter + mDiameter / 6;
    }

    private void makeCirclesProgress(Canvas canvas) {

        switch (mCurrentState) {
            case FOLDING_DOWN:
            case FOLDING_UP:
                drawYMotion(canvas);
                break;
            case FOLDING_RIGHT:
            case FOLDING_LEFT:
                drawXMotion(canvas);
                break;
        }

        canvas.drawPath(mPath, mAbovePaint);
    }

    private void drawXMotion(Canvas canvas) {
        canvas.drawArc(mOval, 90, 180, true, mFstHalfPaint);
        canvas.drawArc(mOval, -270, -180, true, mScndHalfPaint);
        mPath.reset();
        mPath.moveTo(mHalf, 0);
        mPath.cubicTo(mAxisValue, 0, mAxisValue, mDiameter, mHalf, mDiameter);

        // draw again to add its thickness, to avoid visual problem
        mPath.moveTo(mHalf+1, 0);
        mPath.cubicTo(mAxisValue, 0, mAxisValue, mDiameter, mHalf+1, mDiameter);
    }

    private void drawYMotion(Canvas canvas) {
        canvas.drawArc(mOval, 0, -180, true, mFstHalfPaint);
        canvas.drawArc(mOval, -180, -180, true, mScndHalfPaint);
        mPath.reset();
        mPath.moveTo(0, mHalf);
        mPath.cubicTo(0, mAxisValue, mDiameter, mAxisValue, mDiameter, mHalf);

        // draw again to add its thickness, to avoid visual problem
        mPath.moveTo(0, mHalf+1);
        mPath.cubicTo(0, mAxisValue, mDiameter, mAxisValue, mDiameter, mHalf+1);
    }

    @Override
    public void setAlpha(int alpha) {
        this.mAlpha = alpha;
        mFstHalfPaint.setAlpha(alpha);
        mScndHalfPaint.setAlpha(alpha);
        int targetAboveAlpha = (ALPHA_ABOVE_DEFAULT * alpha) / ALPHA_OPAQUE;
        mAbovePaint.setAlpha(targetAboveAlpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        this.mColorFilter = cf;
        mFstHalfPaint.setColorFilter(cf);
        mScndHalfPaint.setColorFilter(cf);
        mAbovePaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        final Callback callback = getCallback();
        if (callback != null) {
            callback.invalidateDrawable(this);
        }
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        final Callback callback = getCallback();
        if (callback != null) {
            callback.scheduleDrawable(this, what, when);
        }
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        final Callback callback = getCallback();
        if (callback != null) {
            callback.unscheduleDrawable(this, what);
        }
    }

    public static class Builder {
        private int[] mColors;

        public Builder(Context context){
            initDefaults(context);
        }

        private void initDefaults(Context context) {
            //Default values
            mColors = context.getResources().getIntArray(R.array.google_colors);
        }

        public Builder colors(int[] colors) {
            if (colors == null || colors.length == 0) {
                throw new IllegalArgumentException("Your color array must contains at least 4 values");
            }

            mColors = colors;
            return this;
        }

        public Drawable build() {
            return new FoldingCirclesDrawable(mColors);
        }
    }
}
