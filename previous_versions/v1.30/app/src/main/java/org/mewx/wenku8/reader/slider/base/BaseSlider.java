package org.mewx.wenku8.reader.slider.base;

/**
 * Created by xuzb on 1/16/15.
 */
public abstract class BaseSlider implements Slider {
    /** 手指移动的方向 */
    public static final int MOVE_TO_LEFT = 0;  // Move to next
    public static final int MOVE_TO_RIGHT = 1; // Move to previous
    public static final int MOVE_NO_RESULT = 4;

    /** 触摸的模式 */
    static final int MODE_NONE = 0;
    static final int MODE_MOVE = 1;
}
