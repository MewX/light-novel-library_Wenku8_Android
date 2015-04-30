package io.codetail.animation;

import android.view.animation.Interpolator;

public class ReverseInterpolator implements Interpolator {
	
    public float getInterpolation(float t) {
    	t = Math.abs(t -1f);
        float x = t*2.0f;
        if (t<0.5f) return 0.5f*x*x*x*x*x;
        x = (t-0.5f)*2-1;
        return 0.5f*x*x*x*x*x+1;
    }
}