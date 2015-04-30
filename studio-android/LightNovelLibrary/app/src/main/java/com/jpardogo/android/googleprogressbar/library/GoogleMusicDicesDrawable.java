package com.jpardogo.android.googleprogressbar.library;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * Dices 'I'm feeling lucky' progress of Google Play Music app.
 *
 * @author Oleksandr Melnykov
 */
public class GoogleMusicDicesDrawable extends Drawable implements Drawable.Callback {

    private static final int DICE_SIDE_COLOR = Color.parseColor("#FFDBDBDB");
    private static final int DICE_SIDE_SHADOW_COLOR = Color.parseColor("#FFB8B8B9");
    private static final int ANIMATION_DURATION = 350;
    private static final int ANIMATION_START_DELAY = 150;

    private static final Interpolator ACCELERATE_INTERPOLATOR = new AccelerateInterpolator();

    private Paint mPaint;
    private Paint mPaintShadow;
    private Paint mPaintCircle;
    private int mSize;
    private float mScale;

    private DiceRotation mDiceRotation;
    private DiceState[] mDiceStates;
    private int mDiceState;

    private enum DiceSide {
        ONE,
        TWO,
        THREE,
        FOUR,
        FIVE,
        SIX
    }

    private enum DiceRotation {
        LEFT,
        DOWN;

        DiceRotation invert() {
            return this == LEFT ? DOWN : LEFT;
        }
    }

    private class DiceState {
        private DiceSide side1;
        private DiceSide side2;
        DiceState(DiceSide side1, DiceSide side2) {
            this.side1 = side1;
            this.side2 = side2;
        }
    }

    public GoogleMusicDicesDrawable() {
        init();
    }

    private void init() {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(DICE_SIDE_COLOR);

        mPaintShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintShadow.setColor(DICE_SIDE_SHADOW_COLOR);

        mPaintCircle = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaintCircle.setColor(Color.WHITE);

        mDiceStates = new DiceState[] {
                new DiceState(DiceSide.ONE, DiceSide.THREE),
                new DiceState(DiceSide.TWO, DiceSide.THREE),
                new DiceState(DiceSide.TWO, DiceSide.SIX),
                new DiceState(DiceSide.FOUR, DiceSide.SIX),
                new DiceState(DiceSide.FOUR, DiceSide.FIVE),
                new DiceState(DiceSide.ONE, DiceSide.FIVE)
        };
        mDiceRotation = DiceRotation.LEFT;

        initObjectAnimator();
    }

    private void initObjectAnimator() {
        final ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(this, "scale", 0, 1);
        objectAnimator.setInterpolator(ACCELERATE_INTERPOLATOR);
        objectAnimator.setDuration(ANIMATION_DURATION);
        objectAnimator.setStartDelay(ANIMATION_START_DELAY);
        objectAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mScale = 0;
                mDiceState++;
                if (mDiceState == mDiceStates.length) {
                    mDiceState = 0;
                }
                mDiceRotation = mDiceRotation.invert();
                objectAnimator.start();
            }
        });
        objectAnimator.start();
    }

    @Override
    public void draw(Canvas canvas) {
        if (mDiceRotation != null) {
            switch (mDiceRotation) {
                case DOWN:
                    drawScaleY(canvas);
                    break;
                case LEFT:
                    drawScaleX(canvas);
                    break;
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        mPaintShadow.setAlpha(alpha);
        mPaintCircle.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
        mPaintShadow.setColorFilter(cf);
        mPaintCircle.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mSize = bounds.width();
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

    private void drawScaleX(Canvas canvas) {
        canvas.save();
        Matrix matrix = new Matrix();
        matrix.preScale(1 - mScale, 1, 0, mSize / 2);
        canvas.concat(matrix);
        drawDiceSide(canvas, mDiceStates[mDiceState].side1, mScale > 0.1f);
        canvas.restore();

        canvas.save();
        matrix = new Matrix();
        matrix.preScale(mScale, 1, mSize, mSize / 2);
        canvas.concat(matrix);
        drawDiceSide(canvas, mDiceStates[mDiceState].side2, false);
        canvas.restore();
    }

    private void drawScaleY(Canvas canvas) {
        canvas.save();
        Matrix matrix = new Matrix();
        matrix.preScale(1, mScale, mSize / 2, 0);
        canvas.concat(matrix);
        drawDiceSide(canvas, mDiceStates[mDiceState].side1, false);
        canvas.restore();

        canvas.save();
        matrix = new Matrix();
        matrix.preScale(1, 1 - mScale, mSize / 2, mSize);
        canvas.concat(matrix);
        drawDiceSide(canvas, mDiceStates[mDiceState].side2, mScale > 0.1f);
        canvas.restore();
    }

    private void drawDiceSide(Canvas canvas, DiceSide side, boolean shadow) {
        int circleRadius = mSize / 10;
        canvas.drawRect(0, 0, mSize, mSize, shadow ? mPaintShadow : mPaint);
        switch (side) {
            case ONE:
                canvas.drawCircle(mSize / 2, mSize / 2, circleRadius, mPaintCircle);
                break;
            case TWO:
                canvas.drawCircle(mSize / 4, mSize - mSize / 4, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize - mSize / 4, mSize / 4, circleRadius, mPaintCircle);
                break;
            case THREE:
                canvas.drawCircle(mSize / 2, mSize / 2, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize / 4, mSize / 4, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize - mSize / 4, mSize - mSize / 4, mSize / 10, mPaintCircle);
                break;
            case FOUR:
                canvas.drawCircle(mSize / 4, mSize / 4, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize / 4, mSize - mSize / 4, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize - mSize / 4, mSize - mSize / 4, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize - mSize / 4, mSize / 4, circleRadius, mPaintCircle);
                break;
            case FIVE:
                canvas.drawCircle(mSize / 2, mSize / 2, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize / 4, mSize / 4, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize / 4, mSize - mSize / 4, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize - mSize / 4, mSize - mSize / 4, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize - mSize / 4, mSize / 4, circleRadius, mPaintCircle);
                break;
            case SIX:
                canvas.drawCircle(mSize / 4, mSize / 4, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize / 4, mSize / 2, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize / 4, mSize - mSize / 4, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize - mSize / 4, mSize / 4, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize - mSize / 4, mSize / 2, circleRadius, mPaintCircle);
                canvas.drawCircle(mSize - mSize / 4, mSize - mSize / 4, circleRadius, mPaintCircle);
                break;
        }
    }

    float getScale() {
        return mScale;
    }

    void setScale(float scale) {
        this.mScale = scale;
    }

    public static class Builder {
        public Drawable build() {
            return new GoogleMusicDicesDrawable();
        }
    }
}
