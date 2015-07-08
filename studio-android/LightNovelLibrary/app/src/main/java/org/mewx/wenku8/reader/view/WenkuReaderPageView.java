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
    // core variables
    private WenkuReaderLoader mLoader = null;
    private WenkuReaderSettingV1 mSetting = null;

    // view components (battery, page number, etc.)

    public WenkuReaderPageView(Context context, WenkuReaderLoader wrl, WenkuReaderSettingV1 wrs) {
        super(context);
        mLoader = wrl;
        mSetting = wrs;
    }
}
