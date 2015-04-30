package io.codetail.animation;

import android.view.animation.Interpolator;

import com.nineoldandroids.animation.Animator;

import java.lang.ref.WeakReference;

final class SupportAnimatorPreL extends SupportAnimator {

    WeakReference<Animator> mSupportFramework;

    SupportAnimatorPreL(Animator animator) {
        mSupportFramework = new WeakReference<Animator>(animator);
    }

    @Override
    public boolean isNativeAnimator() {
        return false;
    }

    @Override
    public Object get() {
        return mSupportFramework.get();
    }

    @Override
    public void start() {
        Animator a = mSupportFramework.get();
        if(a != null) {
            a.start();
        }
    }

    @Override
    public void setDuration(int duration) {
        Animator a = mSupportFramework.get();
        if(a != null) {
            a.setDuration(duration);
        }
    }

    @Override
    public void setInterpolator(Interpolator value) {
        Animator a = mSupportFramework.get();
        if(a != null) {
            a.setInterpolator(value);
        }
    }

    @Override
    public void addListener(final AnimatorListener listener) {
        Animator a = mSupportFramework.get();
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
        Animator a = mSupportFramework.get();
        return a != null && a.isRunning();
    }
}
