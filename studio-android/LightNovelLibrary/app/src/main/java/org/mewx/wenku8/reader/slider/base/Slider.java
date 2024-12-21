package org.mewx.wenku8.reader.slider.base;

import android.view.MotionEvent;

import org.mewx.wenku8.reader.slider.SlidingAdapter;
import org.mewx.wenku8.reader.slider.SlidingLayout;

/**
 * Created by xuzb on 1/16/15.
 */
public interface Slider {
    void init(SlidingLayout slidingLayout);
    void resetFromAdapter(SlidingAdapter adapter);
    boolean onTouchEvent(MotionEvent event);
    void computeScroll();
    void slideNext();
    void slidePrevious();
}
