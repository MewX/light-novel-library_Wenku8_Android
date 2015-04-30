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

import android.view.animation.Interpolator;

import com.nineoldandroids.animation.Animator;

import static com.balysv.materialmenu.MaterialMenuDrawable.IconState;

/**
 * API for interaction with {@link MaterialMenuDrawable}
 */
public interface MaterialMenu {
    /**
     * Change icon without animation
     *
     * @param state new icon state
     */
    public void setState(IconState state);

    /**
     * Return current icon state
     *
     * @return icon state
     */
    public IconState getState();

    /**
     * Animate icon to given state.
     *
     * @param state new icon state
     */
    public void animateState(IconState state);

    /**
     * Animate icon to given state and draw touch circle
     *
     * @param state new icon state
     */
    public void animatePressedState(IconState state);

    /**
     * Set color of icon
     *
     * @param color new icon color
     */
    public void setColor(int color);

    /**
     * Set duration of transformation animations
     *
     * @param duration new animation duration
     */
    public void setTransformationDuration(int duration);

    /**
     * Set duration of pressed state circle animation
     *
     * @param duration new animation duration
     */
    public void setPressedDuration(int duration);

    /**
     * Set interpolator for transformation animations
     *
     * @param interpolator new interpolator
     */
    public void setInterpolator(Interpolator interpolator);

    /**
     * Set listener for {@code MaterialMenuDrawable} animation events
     *
     * @param listener new listener or null to remove any listener
     */
    public void setAnimationListener(Animator.AnimatorListener listener);

    /**
     * Enable RTL layout. Flips all icons horizontally
     *
     * @param rtlEnabled true to enable RTL layout
     */
    public void setRTLEnabled(boolean rtlEnabled);

    /**
     * Manually set a transformation value for an {@link com.balysv.materialmenu.MaterialMenuDrawable.AnimationState}
     *
     * @param animationState state to set value in
     * @param value          between {@link com.balysv.materialmenu.MaterialMenuDrawable#TRANSFORMATION_START} and
     *                       {@link com.balysv.materialmenu.MaterialMenuDrawable#TRANSFORMATION_END}.
     */
    public void setTransformationOffset(MaterialMenuDrawable.AnimationState animationState, float value);

    /**
     * @return {@link MaterialMenuDrawable} to be used for the menu
     */
    public MaterialMenuDrawable getDrawable();
}
