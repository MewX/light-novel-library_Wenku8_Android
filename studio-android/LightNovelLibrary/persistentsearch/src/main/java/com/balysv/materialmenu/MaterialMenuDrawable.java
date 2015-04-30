/*
 * Copyright (C) 2014 Balys Valentukevicius
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.balysv.materialmenu;


import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorListenerAdapter;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.util.Property;

import static android.graphics.Paint.Style;
import static com.nineoldandroids.animation.Animator.AnimatorListener;


public class MaterialMenuDrawable extends Drawable implements Animatable {

    public enum IconState {
        BURGER, ARROW, X, CHECK
    }

    public enum AnimationState {
        BURGER_ARROW, BURGER_X, ARROW_X, ARROW_CHECK, BURGER_CHECK, X_CHECK;

        public IconState getFirstState() {
            switch (this) {
                case BURGER_ARROW:
                    return IconState.BURGER;
                case BURGER_X:
                    return IconState.BURGER;
                case ARROW_X:
                    return IconState.ARROW;
                case ARROW_CHECK:
                    return IconState.ARROW;
                case BURGER_CHECK:
                    return IconState.BURGER;
                case X_CHECK:
                    return IconState.X;
                default:
                    return null;
            }
        }

        public IconState getSecondState() {
            switch (this) {
                case BURGER_ARROW:
                    return IconState.ARROW;
                case BURGER_X:
                    return IconState.X;
                case ARROW_X:
                    return IconState.X;
                case ARROW_CHECK:
                    return IconState.CHECK;
                case BURGER_CHECK:
                    return IconState.CHECK;
                case X_CHECK:
                    return IconState.CHECK;
                default:
                    return null;
            }
        }
    }

    public enum Stroke {
        /**
         * 3 dip
         */
        REGULAR(3),
        /**
         * 2 dip
         */
        THIN(2),
        /**
         * 1 dip
         */
        EXTRA_THIN(1);

        private final int strokeWidth;

        Stroke(int strokeWidth) {
            this.strokeWidth = strokeWidth;
        }

        protected static Stroke valueOf(int strokeWidth) {
            switch (strokeWidth) {
                case 3:
                    return REGULAR;
                case 2:
                    return THIN;
                case 1:
                    return EXTRA_THIN;
                default:
                    return THIN;
            }
        }
    }

    public static final int DEFAULT_COLOR              = Color.WHITE;
    public static final int DEFAULT_SCALE              = 1;
    public static final int DEFAULT_TRANSFORM_DURATION = 800;
    public static final int DEFAULT_PRESSED_DURATION   = 400;

    private static final int BASE_DRAWABLE_WIDTH  = 40;
    private static final int BASE_DRAWABLE_HEIGHT = 40;
    private static final int BASE_ICON_WIDTH      = 20;
    private static final int BASE_CIRCLE_RADIUS   = 18;

    private static final float ARROW_MID_LINE_ANGLE = 180;
    private static final float ARROW_TOP_LINE_ANGLE = 135;
    private static final float ARROW_BOT_LINE_ANGLE = 225;
    private static final float X_TOP_LINE_ANGLE     = 44;
    private static final float X_BOT_LINE_ANGLE     = -44;
    private static final float X_ROTATION_ANGLE     = 90;
    private static final float CHECK_MIDDLE_ANGLE   = 135;
    private static final float CHECK_BOTTOM_ANGLE   = -90;

    private static final float TRANSFORMATION_START = 0;
    private static final float TRANSFORMATION_MID   = 1.0f;
    private static final float TRANSFORMATION_END   = 2.0f;

    private static final int DEFAULT_CIRCLE_ALPHA = 200;

    private final float diph;
    private final float dip1;
    private final float dip2;
    private final float dip3;
    private final float dip4;
    private final float dip6;
    private final float dip8;

    private final int   width;
    private final int   height;
    private final float strokeWidth;
    private final float iconWidth;
    private final float topPadding;
    private final float sidePadding;
    private final float circleRadius;

    private final Stroke stroke;

    private final Object lock = new Object();

    private final Paint iconPaint   = new Paint();
    private final Paint circlePaint = new Paint();

    private float   transformationValue   = 0f;
    private float   pressedProgressValue  = 0f;
    private boolean transformationRunning = false;

    private IconState      currentIconState = IconState.BURGER;
    private AnimationState animationState   = AnimationState.BURGER_ARROW;

    private IconState animatingIconState;
    private boolean   drawTouchCircle;
    private boolean   neverDrawTouch;
    private boolean   rtlEnabled;

    private ObjectAnimator   transformation;
    private ObjectAnimator   pressedCircle;
    private AnimatorListener animatorListener;

    private MaterialMenuState materialMenuState;

    public MaterialMenuDrawable(Context context, int color, Stroke stroke) {
        this(context, color, stroke, DEFAULT_SCALE, DEFAULT_TRANSFORM_DURATION, DEFAULT_PRESSED_DURATION);
    }

    public MaterialMenuDrawable(Context context, int color, Stroke stroke, int transformDuration, int pressedDuration) {
        this(context, color, stroke, DEFAULT_SCALE, transformDuration, pressedDuration);
    }

    public MaterialMenuDrawable(Context context, int color, Stroke stroke, int scale, int transformDuration, int pressedDuration) {
        Resources resources = context.getResources();
        // convert each separately due to various densities
        this.dip1 = dpToPx(resources, 1) * scale;
        this.dip2 = dpToPx(resources, 2) * scale;
        this.dip3 = dpToPx(resources, 3) * scale;
        this.dip4 = dpToPx(resources, 4) * scale;
        this.dip6 = dpToPx(resources, 6) * scale;
        this.dip8 = dpToPx(resources, 8) * scale;
        this.diph = dip1 / 2;

        this.stroke = stroke;
        this.width = (int) (dpToPx(resources, BASE_DRAWABLE_WIDTH) * scale);
        this.height = (int) (dpToPx(resources, BASE_DRAWABLE_HEIGHT) * scale);
        this.iconWidth = dpToPx(resources, BASE_ICON_WIDTH) * scale;
        this.circleRadius = dpToPx(resources, BASE_CIRCLE_RADIUS) * scale;
        this.strokeWidth = dpToPx(resources, stroke.strokeWidth) * scale;

        this.sidePadding = (width - iconWidth) / 2;
        this.topPadding = (height - 5 * dip3) / 2;

        initPaint(color);
        initAnimations(transformDuration, pressedDuration);

        materialMenuState = new MaterialMenuState();
    }

    private MaterialMenuDrawable(int color, Stroke stroke, long transformDuration, long pressedDuration,
        int width, int height, float iconWidth, float circleRadius, float strokeWidth, float dip1
    ) {
        this.dip1 = dip1;
        this.dip2 = dip1 * 2;
        this.dip3 = dip1 * 3;
        this.dip4 = dip1 * 4;
        this.dip6 = dip1 * 6;
        this.dip8 = dip1 * 8;
        this.diph = dip1 / 2;
        this.stroke = stroke;
        this.width = width;
        this.height = height;
        this.iconWidth = iconWidth;
        this.circleRadius = circleRadius;
        this.strokeWidth = strokeWidth;
        this.sidePadding = (width - iconWidth) / 2;
        this.topPadding = (height - 5 * dip3) / 2;

        initPaint(color);
        initAnimations((int) transformDuration, (int) pressedDuration);

        materialMenuState = new MaterialMenuState();
    }

    private void initPaint(int color) {
        iconPaint.setAntiAlias(true);
        iconPaint.setStyle(Style.STROKE);
        iconPaint.setStrokeWidth(strokeWidth);
        iconPaint.setColor(color);

        circlePaint.setAntiAlias(true);
        circlePaint.setStyle(Style.FILL);
        circlePaint.setColor(color);
        circlePaint.setAlpha(DEFAULT_CIRCLE_ALPHA);

        setBounds(0, 0, width, height);
    }

    /*
     * Drawing
     */

    @Override public void draw(Canvas canvas) {
        final float ratio = transformationValue <= 1 ? transformationValue : 2 - transformationValue;

        if (rtlEnabled) {
            canvas.save();
            canvas.scale(-1, 1, 0, 0);
            canvas.translate(-getIntrinsicWidth(), 0);
        }

        drawTopLine(canvas, ratio);
        drawMiddleLine(canvas, ratio);
        drawBottomLine(canvas, ratio);

        if (rtlEnabled) {
            canvas.restore();
        }

        if (drawTouchCircle) drawTouchCircle(canvas);
    }

    private void drawTouchCircle(Canvas canvas) {
        canvas.restore();
        canvas.drawCircle(width / 2, height / 2, pressedProgressValue, circlePaint);
    }

    private void drawMiddleLine(Canvas canvas, float ratio) {
        canvas.restore();
        canvas.save();

        float rotation = 0;
        float pivotX = width / 2;
        float pivotY = width / 2;
        float startX = sidePadding;
        float startY = topPadding + dip3 / 2 * 5;
        float stopX = width - sidePadding;
        float stopY = topPadding + dip3 / 2 * 5;
        int alpha = 255;

        switch (animationState) {
            case BURGER_ARROW:
                // rotate by 180
                if (isMorphingForward()) {
                    rotation = ratio * ARROW_MID_LINE_ANGLE;
                } else {
                    rotation = ARROW_MID_LINE_ANGLE + (1 - ratio) * ARROW_MID_LINE_ANGLE;
                }
                // shorten one end
                stopX -= ratio * resolveStrokeModifier(ratio) / 2;
                break;
            case BURGER_X:
                // fade out
                alpha = (int) ((1 - ratio) * 255);
                break;
            case ARROW_X:
                // fade out and shorten one end
                alpha = (int) ((1 - ratio) * 255);
                startX += (1 - ratio) * dip2;
                break;
            case ARROW_CHECK:
                if (isMorphingForward()) {
                    // rotate until required angle
                    rotation = ratio * CHECK_MIDDLE_ANGLE;
                } else {
                    // rotate back to starting angle
                    rotation = CHECK_MIDDLE_ANGLE - CHECK_MIDDLE_ANGLE * (1 - ratio);
                }
                // shorten one end and lengthen the other
                startX += dip3 / 2 + dip4 - (1 - ratio) * dip2;
                stopX += ratio * dip1;
                pivotX = width / 2 + dip3 + diph;
                break;
            case BURGER_CHECK:
                // rotate until required angle
                rotation = ratio * CHECK_MIDDLE_ANGLE;
                // lengthen both ends
                startX += ratio * (dip4 + dip3 / 2);
                stopX += ratio * dip1;
                pivotX = width / 2 + dip3 + diph;
                break;
            case X_CHECK:
                // fade in
                alpha = (int) (ratio * 255);
                // rotation to check angle
                rotation = ratio * CHECK_MIDDLE_ANGLE;
                // lengthen both ends
                startX += ratio * (dip4 + dip3 / 2);
                stopX += ratio * dip1;
                pivotX = width / 2 + dip3 + diph;
                break;
        }

        iconPaint.setAlpha(alpha);
        canvas.rotate(rotation, pivotX, pivotY);
        canvas.drawLine(startX, startY, stopX, stopY, iconPaint);
        iconPaint.setAlpha(255);
    }

    private void drawTopLine(Canvas canvas, float ratio) {
        canvas.save();

        float rotation = 0, pivotX = 0, pivotY = 0;
        float rotation2 = 0;
        // pivot at center of line
        float pivotX2 = width / 2 + dip3 / 2;
        float pivotY2 = topPadding + dip2;

        float startX = sidePadding;
        float startY = topPadding + dip2;
        float stopX = width - sidePadding;
        float stopY = topPadding + dip2;
        int alpha = 255;

        switch (animationState) {
            case BURGER_ARROW:
                if (isMorphingForward()) {
                    // rotate until required angle
                    rotation = ratio * ARROW_BOT_LINE_ANGLE;
                } else {
                    // rotate back to start doing a 360
                    rotation = ARROW_BOT_LINE_ANGLE + (1 - ratio) * ARROW_TOP_LINE_ANGLE;
                }
                // rotate by middle
                pivotX = width / 2;
                pivotY = height / 2;

                // shorten both ends
                stopX -= resolveStrokeModifier(ratio);
                startX += dip3 * ratio;

                break;
            case BURGER_X:
                // rotate until required angles
                rotation = X_TOP_LINE_ANGLE * ratio;
                rotation2 = X_ROTATION_ANGLE * ratio;

                // pivot at left corner of line
                pivotX = sidePadding + dip4;
                pivotY = topPadding + dip3;

                // shorten one end
                startX += dip3 * ratio;
                break;
            case ARROW_X:
                // rotate from ARROW angle to X angle
                rotation = ARROW_BOT_LINE_ANGLE + (X_TOP_LINE_ANGLE - ARROW_BOT_LINE_ANGLE) * ratio;
                rotation2 = X_ROTATION_ANGLE * ratio;

                // move pivot from ARROW pivot to X pivot
                pivotX = width / 2 + (sidePadding + dip4 - width / 2) * ratio;
                pivotY = height / 2 + (topPadding + dip3 - height / 2) * ratio;

                // lengthen both ends
                stopX -= resolveStrokeModifier(ratio);
                startX += dip3;
                break;
            case ARROW_CHECK:
                // fade out
                alpha = (int) ((1 - ratio) * 255);
                // retain starting arrow configuration
                rotation = ARROW_BOT_LINE_ANGLE;
                pivotX = width / 2;
                pivotY = height / 2;

                // shorted both ends
                stopX -= resolveStrokeModifier(1);
                startX += dip3;
                break;
            case BURGER_CHECK:
                // fade out
                alpha = (int) ((1 - ratio) * 255);
                break;
            case X_CHECK:
                // retain X configuration
                rotation = X_TOP_LINE_ANGLE;
                rotation2 = X_ROTATION_ANGLE;
                pivotX = sidePadding + dip4;
                pivotY = topPadding + dip3;
                stopX += dip3 - dip3 * (1 - ratio);
                startX += dip3;

                // fade out
                alpha = (int) ((1 - ratio) * 255);
                break;
        }

        iconPaint.setAlpha(alpha);
        canvas.rotate(rotation, pivotX, pivotY);
        canvas.rotate(rotation2, pivotX2, pivotY2);
        canvas.drawLine(startX, startY, stopX, stopY, iconPaint);
        iconPaint.setAlpha(255);
    }

    private void drawBottomLine(Canvas canvas, float ratio) {
        canvas.restore();
        canvas.save();

        float rotation = 0, pivotX = 0, pivotY = 0;
        float rotation2 = 0;
        // pivot at center of line
        float pivotX2 = width / 2 + dip3 / 2;
        float pivotY2 = height - topPadding - dip2;

        float startX = sidePadding;
        float startY = height - topPadding - dip2;
        float stopX = width - sidePadding;
        float stopY = height - topPadding - dip2;

        switch (animationState) {
            case BURGER_ARROW:
                if (isMorphingForward()) {
                    // rotate to required angle
                    rotation = ARROW_TOP_LINE_ANGLE * ratio;
                } else {
                    // rotate back to start doing a 360
                    rotation = ARROW_TOP_LINE_ANGLE + (1 - ratio) * ARROW_BOT_LINE_ANGLE;
                }
                // pivot center of canvas
                pivotX = width / 2;
                pivotY = height / 2;

                // shorten both ends
                stopX = width - sidePadding - resolveStrokeModifier(ratio);
                startX = sidePadding + dip3 * ratio;
                break;
            case BURGER_X:
                if (isMorphingForward()) {
                    // rotate around
                    rotation2 = -X_ROTATION_ANGLE * ratio;
                } else {
                    // rotate directly
                    rotation2 = X_ROTATION_ANGLE * ratio;
                }
                // rotate to required angle
                rotation = X_BOT_LINE_ANGLE * ratio;

                // pivot left corner of line
                pivotX = sidePadding + dip4;
                pivotY = height - topPadding - dip3;

                // shorten one end
                startX += dip3 * ratio;
                break;
            case ARROW_X:
                // rotate from ARROW angle to X angle
                rotation = ARROW_TOP_LINE_ANGLE + (360 + X_BOT_LINE_ANGLE - ARROW_TOP_LINE_ANGLE) * ratio;
                rotation2 = -X_ROTATION_ANGLE * ratio;

                // move pivot from ARROW pivot to X pivot
                pivotX = width / 2 + (sidePadding + dip4 - width / 2) * ratio;
                pivotY = height / 2 + (height / 2 - topPadding - dip3) * ratio;

                // lengthen both ends
                stopX -= resolveStrokeModifier(ratio);
                startX += dip3;
                break;
            case ARROW_CHECK:
                // rotate from ARROW angle to CHECK angle
                rotation = ARROW_TOP_LINE_ANGLE + ratio * CHECK_BOTTOM_ANGLE;

                // move pivot from ARROW pivot to CHECK pivot
                pivotX = width / 2 + dip3 * ratio;
                pivotY = height / 2 - dip3 * ratio;

                // length stays same as ARROW
                stopX -= resolveStrokeModifier(1);
                startX += dip3 + (dip4 + dip1) * ratio;
                break;
            case BURGER_CHECK:
                // rotate from ARROW angle to CHECK angle
                rotation = ratio * (CHECK_BOTTOM_ANGLE + ARROW_TOP_LINE_ANGLE);

                // move pivot from BURGER pivot to CHECK pivot
                pivotX = width / 2 + dip3 * ratio;
                pivotY = height / 2 - dip3 * ratio;

                // length stays same as BURGER
                startX += dip8 * ratio;
                stopX -= resolveStrokeModifier(ratio);
                break;
            case X_CHECK:
                // rotate from X to CHECK angles
                rotation2 = -X_ROTATION_ANGLE * (1 - ratio);
                rotation = X_BOT_LINE_ANGLE + (CHECK_BOTTOM_ANGLE + ARROW_TOP_LINE_ANGLE - X_BOT_LINE_ANGLE) * ratio;

                // move pivot from X to CHECK
                pivotX = sidePadding + dip4 + (width / 2 + dip3 - sidePadding - dip4) * ratio;
                pivotY = height - topPadding - dip3 + (topPadding + height / 2 - height) * ratio;

                // shorten both ends
                startX += dip8 - (dip4 + dip1) * (1 - ratio);
                stopX -= resolveStrokeModifier(1 - ratio);
                break;
        }

        canvas.rotate(rotation, pivotX, pivotY);
        canvas.rotate(rotation2, pivotX2, pivotY2);
        canvas.drawLine(startX, startY, stopX, stopY, iconPaint);
    }

    private boolean isMorphingForward() {
        return transformationValue <= TRANSFORMATION_MID;
    }

    private float resolveStrokeModifier(float ratio) {
        switch (stroke) {
            case REGULAR:
                if (animationState == AnimationState.ARROW_X || animationState == AnimationState.X_CHECK) {
                    return dip3 - (dip3 * ratio);
                }
                return ratio * dip3;
            case THIN:
                if (animationState == AnimationState.ARROW_X || animationState == AnimationState.X_CHECK) {
                    return dip3 + diph - (dip3 + diph) * ratio;
                }
                return ratio * (dip3 + diph);
            case EXTRA_THIN:
                if (animationState == AnimationState.ARROW_X || animationState == AnimationState.X_CHECK) {
                    return dip4 - ((dip3 + dip1) * ratio);
                }
                return ratio * dip4;
        }
        return 0;
    }

    @Override public void setAlpha(int alpha) {
        iconPaint.setAlpha(alpha);
    }

    @Override public void setColorFilter(ColorFilter cf) {
        iconPaint.setColorFilter(cf);
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    /*
     * Accessor methods
     */

    public void setColor(int color) {
        iconPaint.setColor(color);
        circlePaint.setColor(color);
        invalidateSelf();
    }

    public void setTransformationDuration(int duration) {
        transformation.setDuration(duration);
    }

    public void setPressedDuration(int duration) {
        pressedCircle.setDuration(duration);
    }

    public void setInterpolator(Interpolator interpolator) {
        transformation.setInterpolator(interpolator);
    }

    public void setAnimationListener(AnimatorListener listener) {
        if (animatorListener != null) {
            transformation.removeListener(animatorListener);
        }

        if (listener != null) {
            transformation.addListener(listener);
        }

        animatorListener = listener;
    }

    public void setNeverDrawTouch(boolean neverDrawTouch) {
        this.neverDrawTouch = neverDrawTouch;
    }

    public void setIconState(IconState iconState) {
        synchronized (lock) {
            if (transformationRunning) {
                transformation.cancel();
                transformationRunning = false;
            }

            if (currentIconState == iconState) return;

            switch (iconState) {
                case BURGER:
                    animationState = AnimationState.BURGER_ARROW;
                    transformationValue = TRANSFORMATION_START;
                    break;
                case ARROW:
                    animationState = AnimationState.BURGER_ARROW;
                    transformationValue = TRANSFORMATION_MID;
                    break;
                case X:
                    animationState = AnimationState.BURGER_X;
                    transformationValue = TRANSFORMATION_MID;
                    break;
                case CHECK:
                    animationState = AnimationState.BURGER_CHECK;
                    transformationValue = TRANSFORMATION_MID;
            }
            currentIconState = iconState;
            invalidateSelf();
        }
    }

    public void animateIconState(IconState state, boolean drawTouch) {
        synchronized (lock) {
            if (transformationRunning) {
                transformation.end();
                pressedCircle.end();
            }
            drawTouchCircle = drawTouch;
            animatingIconState = state;
            start();
        }
    }

    public IconState setTransformationOffset(AnimationState animationState, float offset) {
        if (offset < TRANSFORMATION_START || offset > TRANSFORMATION_END) {
            throw new IllegalArgumentException(
                String.format("Value must be between %s and %s", TRANSFORMATION_START, TRANSFORMATION_END)
            );
        }

        this.animationState = animationState;

        final boolean isFirstIcon = offset < TRANSFORMATION_MID || offset == TRANSFORMATION_END;

        currentIconState = isFirstIcon ? animationState.getFirstState() : animationState.getSecondState();
        animatingIconState = isFirstIcon ? animationState.getSecondState() : animationState.getFirstState();

        setTransformationValue(offset);

        return currentIconState;
    }

    public void setRTLEnabled(boolean rtlEnabled) {
        this.rtlEnabled = rtlEnabled;
        invalidateSelf();
    }

    public IconState getIconState() {
        return currentIconState;
    }

    /*
     * Animations
     */
    private Property<MaterialMenuDrawable, Float> transformationProperty
        = new Property<MaterialMenuDrawable, Float>(Float.class, "transformation") {
        @Override
        public Float get(MaterialMenuDrawable object) {
            return object.getTransformationValue();
        }

        @Override
        public void set(MaterialMenuDrawable object, Float value) {
            object.setTransformationValue(value);
        }
    };

    private Property<MaterialMenuDrawable, Float> pressedProgressProperty
        = new Property<MaterialMenuDrawable, Float>(Float.class, "pressedProgress") {
        @Override
        public Float get(MaterialMenuDrawable object) {
            return object.getPressedProgress();
        }

        @Override
        public void set(MaterialMenuDrawable object, Float value) {
            object.setPressedProgress(value);
        }
    };

    public Float getTransformationValue() {
        return transformationValue;
    }

    public void setTransformationValue(Float value) {
        this.transformationValue = value;
        invalidateSelf();
    }

    public Float getPressedProgress() {
        return pressedProgressValue;
    }

    public void setPressedProgress(Float value) {
        this.pressedProgressValue = value;
        circlePaint.setAlpha((int) (DEFAULT_CIRCLE_ALPHA * (1 - value / (circleRadius * 1.22f))));
        invalidateSelf();
    }

    private void initAnimations(int transformDuration, int pressedDuration) {
        transformation = ObjectAnimator.ofFloat(this, transformationProperty, 0);
        transformation.setInterpolator(new DecelerateInterpolator(3));
        transformation.setDuration(transformDuration);
        transformation.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                transformationRunning = false;
                setIconState(animatingIconState);
            }
        });


        pressedCircle = ObjectAnimator.ofFloat(this, pressedProgressProperty, 0, 0);
        pressedCircle.setDuration(pressedDuration);
        pressedCircle.setInterpolator(new DecelerateInterpolator());
        pressedCircle.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                pressedProgressValue = 0;
            }

            @Override public void onAnimationCancel(Animator animation) {
                pressedProgressValue = 0;
            }
        });
    }

    private boolean resolveTransformation() {
        boolean isCurrentBurger = currentIconState == IconState.BURGER;
        boolean isCurrentArrow = currentIconState == IconState.ARROW;
        boolean isCurrentX = currentIconState == IconState.X;
        boolean isCurrentCheck = currentIconState == IconState.CHECK;
        boolean isAnimatingBurger = animatingIconState == IconState.BURGER;
        boolean isAnimatingArrow = animatingIconState == IconState.ARROW;
        boolean isAnimatingX = animatingIconState == IconState.X;
        boolean isAnimatingCheck = animatingIconState == IconState.CHECK;

        if ((isCurrentBurger && isAnimatingArrow) || (isCurrentArrow && isAnimatingBurger)) {
            animationState = AnimationState.BURGER_ARROW;
            return isCurrentBurger;
        }

        if ((isCurrentArrow && isAnimatingX) || (isCurrentX && isAnimatingArrow)) {
            animationState = AnimationState.ARROW_X;
            return isCurrentArrow;
        }

        if ((isCurrentBurger && isAnimatingX) || (isCurrentX && isAnimatingBurger)) {
            animationState = AnimationState.BURGER_X;
            return isCurrentBurger;
        }

        if ((isCurrentArrow && isAnimatingCheck) || (isCurrentCheck && isAnimatingArrow)) {
            animationState = AnimationState.ARROW_CHECK;
            return isCurrentArrow;
        }

        if ((isCurrentBurger && isAnimatingCheck) || (isCurrentCheck && isAnimatingBurger)) {
            animationState = AnimationState.BURGER_CHECK;
            return isCurrentBurger;
        }

        if ((isCurrentX && isAnimatingCheck) || (isCurrentCheck && isAnimatingX)) {
            animationState = AnimationState.X_CHECK;
            return isCurrentX;
        }

        throw new IllegalStateException(
            String.format("Animating from %s to %s is not supported", currentIconState, animatingIconState)
        );
    }

    @Override public void start() {
        if (transformationRunning) return;

        if (animatingIconState != null && animatingIconState != currentIconState) {
            transformationRunning = true;

            final boolean direction = resolveTransformation();
            transformation.setFloatValues(
                direction ? TRANSFORMATION_START : TRANSFORMATION_MID,
                direction ? TRANSFORMATION_MID : TRANSFORMATION_END
            );
            transformation.start();
        }

        if (pressedCircle.isRunning()) {
            pressedCircle.cancel();
        }
        if (drawTouchCircle && !neverDrawTouch) {
            pressedCircle.setFloatValues(0, circleRadius * 1.22f);
            pressedCircle.start();
        }

        invalidateSelf();
    }

    @Override public void stop() {
        if (isRunning() && transformation.isRunning()) {
            transformation.end();
        } else {
            transformationRunning = false;
            invalidateSelf();
        }
    }

    @Override public boolean isRunning() {
        return transformationRunning;
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }


    @Override
    public ConstantState getConstantState() {
        materialMenuState.changingConfigurations = getChangingConfigurations();
        return materialMenuState;
    }

    @Override
    public Drawable mutate() {
        materialMenuState = new MaterialMenuState();
        return this;
    }

    private final class MaterialMenuState extends ConstantState {
        private int changingConfigurations;

        private MaterialMenuState() {
        }

        @Override
        public Drawable newDrawable() {
            MaterialMenuDrawable drawable = new MaterialMenuDrawable(
                circlePaint.getColor(), stroke, transformation.getDuration(),
                pressedCircle.getDuration(), width, height, iconWidth, circleRadius, strokeWidth, dip1
            );
            drawable.setIconState(animatingIconState != null ? animatingIconState : currentIconState);
            drawable.setRTLEnabled(rtlEnabled);
            return drawable;
        }

        @Override
        public int getChangingConfigurations() {
            return changingConfigurations;
        }
    }

    static float dpToPx(Resources resources, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.getDisplayMetrics());
    }
}
