package org.mewx.wenku8.reader.view;

import android.content.Context;
import android.view.View;

import org.mewx.wenku8.reader.loader.WenkuReaderLoader;
import org.mewx.wenku8.reader.setting.WenkuReaderSettingV1;

/**
 * Created by MewX on 2015/7/8.
 *
 * Implement whole view of page, and use full screen page size.
 *
 * Default Elements:
 *  - Top: ChapterTitle, WIFI/DC
 *  - Bot: Battery, Paragraph/All, CurrentTime
 *
 * Click Elements:
 *  - Top: NovelTitle
 *  - Bot: ToolBar
 */
public class WenkuReaderPageView extends View {
    // enum
    public enum LOADING_DIRECTION {
        FORWARDS, // go to next page
        CURRENT, // get this page
        BACKWARDS // go to previous page
    }

    // core variables
    private WenkuReaderLoader mLoader = null;
    private WenkuReaderSettingV1 mSetting = null;

    // vars
    private int firstLineIndex;
    private int firstWordIndex;
    private int lastLineIndex;
    private int lastWordIndex; // last paragraph's last word's index

    // view components (battery, page number, etc.)

    /**
     * This function init the view class
     * @param context current context, should be WenkuReaderActivity
     * @param wrl loader which is inited
     * @param wrs setting which is inited
     * @param index if FORWARDS, this is the last index of last page;
     *              if CURRENT, this is the first index of this page;
     *              if BACKWARDS, this is the first index of last page;
     * @param directionForward get next or get previous
     */
    public WenkuReaderPageView(Context context, WenkuReaderLoader wrl, WenkuReaderSettingV1 wrs, int index, LOADING_DIRECTION directionForward) {
        super(context);
        mLoader = wrl;
        mSetting = wrs;

        // save vars, calc all ints

    }

    public int getFirstLineIndex() {
        return firstLineIndex;
    }

    public int getFirstWordIndex() {
        return firstWordIndex;
    }

    public int getLastLineIndex() {
        return lastLineIndex;
    }

    public int getLastWordIndex() {
        return lastWordIndex;
    }
}
