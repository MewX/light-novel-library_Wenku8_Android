package org.mewx.wenku8.component;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * Created by MewX on 2015/6/6.
 */
public class ScrollViewNoFling extends ScrollView {
    public ScrollViewNoFling(Context context) {
        super(context);
    }

    public ScrollViewNoFling(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ScrollViewNoFling(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ScrollViewNoFling(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void fling (int velocityY)
    {
    /*Scroll view is no longer gonna handle scroll velocity.
     * super.fling(velocityY);
    */
    }
}
