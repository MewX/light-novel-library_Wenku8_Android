package io.codetail.animation;

import android.animation.Animator;
import android.annotation.TargetApi;
import android.os.Build;
import android.view.animation.Interpolator;

import java.lang.ref.WeakReference;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
final class SupportAnimatorLollipop extends SupportAnimator{

    WeakReference<Animator> mNativeAnimator;

    SupportAnimatorLollipop(Animator animator) {
        mNativeAnimator = new WeakReference<Animator>(animator);
    }

    @Override
    public boolean isNativeAnimator() {
        return true;
    }

    @Override
    public Object get() {
        return mNativeAnimator;
    }


    @Override
    public void start() {
        Animator a = mNativeAnimator.get();
        if(a != null) {
            a.start();
        }
    }

    @Override
    public void setDuration(int duration) {
        Animator a = mNativeAnimator.get();
        if(a != null) {
            a.setDuration(duration);
        }
    }

    @Override
    public void setInterpolator(Interpolator value) {
        Animator a = mNativeAnimator.get();
        if(a != null) {
            a.setInterpolator(value);
        }
    }

    @Override
    public void addListener(final AnimatorListener listener) {
        Animator a = mNativeAnimator.get();
        if(a == null) {
            return;
        }

        if(listener == null){
            a.addListener(null);
            return;
        }

        a.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                listener.onAnimationStart();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                listener.onAnimationEnd();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                listener.onAnimationCancel();
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
                listener.onAnimationRepeat();
            }
        });
    }

    @Override
    public boolean isRunning() {
        Animator a = mNativeAnimator.get();
        return a != null && a.isRunning();
    }
}
