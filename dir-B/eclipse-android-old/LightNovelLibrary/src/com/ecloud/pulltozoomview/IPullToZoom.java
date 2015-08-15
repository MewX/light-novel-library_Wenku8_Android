package com.ecloud.pulltozoomview;

/**
 * Author:    ZhuWenWu
 * Version    V1.0
 * Date:      2014/11/7  14:21.
 * Description:
 * Modification  History:
 * Date         	Author        		Version        	Description
 * -----------------------------------------------------------------------------------
 * 2014/11/7        ZhuWenWu            1.0                    1.0
 * Why & What is modified:
 */

import android.content.res.TypedArray;
import android.view.View;

public interface IPullToZoom<T extends View> {
    /**
     * Get the Wrapped Zoom View. Anything returned here has already been
     * added to the content view.
     *
     * @return The View which is currently wrapped
     */
    public View getZoomView();

    public View getHeaderView();

    /**
     * Get the Wrapped root View.
     *
     * @return The View which is currently wrapped
     */
    public T getRootView();

    /**
     * Whether Pull-to-Refresh is enabled
     *
     * @return enabled
     */
    public boolean isPullToZoomEnabled();

    /**
     * Returns whether the Widget is currently in the Zooming state
     *
     * @return true if the Widget is currently zooming
     */
    public boolean isZooming();

    /**
     * Returns whether the Widget is currently in the Zooming anim type
     *
     * @return true if the anim is parallax
     */
    public boolean isParallax();

    public boolean isHideHeader();

    public void handleStyledAttributes(TypedArray a);
}
