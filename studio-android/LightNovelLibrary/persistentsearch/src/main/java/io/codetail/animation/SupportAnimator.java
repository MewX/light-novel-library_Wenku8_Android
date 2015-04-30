package io.codetail.animation;

import android.view.animation.Interpolator;

public abstract class SupportAnimator {

    /**
     * @return true if using native android animation framework, otherwise is
     * nineoldandroids
     */
    public abstract boolean isNativeAnimator();

    /**
     * @return depends from {@link android.os.Build.VERSION} if sdk version
     * {@link android.os.Build.VERSION_CODES#LOLLIPOP} and greater will return
     * {@link android.animation.Animator} otherwise {@link com.nineoldandroids.animation.Animator}
     */
    public abstract Object get();

    /**
     * Starts this animation. If the animation has a nonzero startDelay, the animation will start
     * running after that delay elapses. A non-delayed animation will have its initial
     * value(s) set immediately, followed by calls to
     * {@link android.animation.Animator.AnimatorListener#onAnimationStart(android.animation.Animator)}
     * for any listeners of this animator.
     *
     * <p>The animation started by calling this method will be run on the thread that called
     * this method. This thread should have a Looper on it (a runtime exception will be thrown if
     * this is not the case). Also, if the animation will animate
     * properties of objects in the view hierarchy, then the calling thread should be the UI
     * thread for that view hierarchy.</p>
     *
     */
    public abstract void start();

    /**
     * Sets the duration of the animation.
     *
     * @param duration The length of the animation, in milliseconds.
     */
    public abstract void setDuration(int duration);

    /**
     * The time interpolator used in calculating the elapsed fraction of the
     * animation. The interpolator determines whether the animation runs with
     * linear or non-linear motion, such as acceleration and deceleration. The
     * default value is {@link android.view.animation.AccelerateDecelerateInterpolator}.
     *
     * @param value the interpolator to be used by this animation
     */
    public abstract void setInterpolator(Interpolator value);


    /**
     * Adds a listener to the set of listeners that are sent events through the life of an
     * animation, such as start, repeat, and end.
     *
     * @param listener the listener to be added to the current set of listeners for this animation.
     */
    public abstract void addListener(AnimatorListener listener);


    /**
     * Returns whether this Animator is currently running (having been started and gone past any
     * initial startDelay period and not yet ended).
     *
     * @return Whether the Animator is running.
     */
    public abstract boolean isRunning();


    /**
     * <p>An animation listener receives notifications from an animation.
     * Notifications indicate animation related events, such as the end or the
     * repetition of the animation.</p>
     */
    public static interface AnimatorListener {
        /**
         * <p>Notifies the start of the animation.</p>
         */
        void onAnimationStart();

        /**
         * <p>Notifies the end of the animation. This callback is not invoked
         * for animations with repeat count set to INFINITE.</p>
         */
        void onAnimationEnd();

        /**
         * <p>Notifies the cancellation of the animation. This callback is not invoked
         * for animations with repeat count set to INFINITE.</p>
         */
        void onAnimationCancel();

        /**
         * <p>Notifies the repetition of the animation.</p>
         */
        void onAnimationRepeat();
    }

}
